package dev.wrenchbridge;

import dev.wrenchbridge.core.SourceViewLimits;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.PacketDistributor;

/** Bounded read-only snapshots of the authenticated player's own first matching tools. */
public final class SourceViewPacket {
  private SourceViewPacket() {}

  public static final class Request {
    public final int token;
    public final List<String> ids;

    public Request(int token, List<String> ids) {
      this.token = token;
      this.ids = new ArrayList<String>(ids);
    }
  }

  public static final class Entry {
    public final String id;
    public final byte[] data;

    public Entry(String id, byte[] data) {
      this.id = id;
      this.data = data;
    }
  }

  public static final class Reply {
    public final int token;
    public final List<Entry> entries;

    public Reply(int token, List<Entry> entries) {
      this.token = token;
      this.entries = entries;
    }
  }

  private static final class Session {
    int token, last, captured;
    boolean dirty;
    List<String> ids;

    Session(int token, int tick, List<String> ids) {
      this.token = token;
      this.last = tick;
      this.captured = tick;
      this.ids = new ArrayList<String>(ids);
    }
  }

  private static final Map<UUID, Session> SESSIONS = new HashMap<UUID, Session>();

  private static final Set<UUID> PENDING = new HashSet<>();

  public static void forget(UUID player) {
    SESSIONS.remove(player);
    PENDING.remove(player);
  }

  public static void clearServer() {
    SESSIONS.clear();
    PENDING.clear();
  }

  public static Reply reply(ServerPlayerEntity p, Request request) {
    if (p.isSpectator() || request.ids.size() > SourceViewLimits.MAX_SOURCES) return null;
    int now = p.getServer().getTickCount();
    Session previous = SESSIONS.get(p.getUUID());
    List<String> ids = new ArrayList<String>(new LinkedHashSet<String>(request.ids));
    for (String id : ids)
      if (id.isEmpty() || id.length() > SourceViewLimits.MAX_ID_BYTES) return null;
    Session session = previous == null ? new Session(request.token, now, ids) : previous;
    session.token = request.token;
    session.last = now;
    session.ids = ids;
    SESSIONS.put(p.getUUID(), session);
    if (previous != null && now - previous.captured < SourceViewLimits.REFRESH_TICKS) {
      session.dirty = true;
      PENDING.add(p.getUUID());
      return null;
    }
    session.captured = now;
    session.dirty = false;
    PENDING.remove(p.getUUID());
    try {
      return capture(p, request.token, ids);
    } catch (RuntimeException | LinkageError error) {
      WrenchBridge.LOG.debug("Native source display was unavailable", error);
      return null;
    }
  }

  public static Reply capture(ServerPlayerEntity p, int token, List<String> ids) {
    if (ids.size() > SourceViewLimits.MAX_SOURCES)
      throw new IllegalArgumentException("Too many source views");
    Map<String, ItemStack> sources = StoredTools.snapshots(p, ids);
    List<Entry> entries = new ArrayList<Entry>();
    for (String id : ids) {
      ItemStack source = sources.get(id);
      entries.add(new Entry(id, encodeStack(p, source == null ? ItemStack.EMPTY : source)));
    }
    return new Reply(token, entries);
  }

  public static void changed(ServerPlayerEntity p) {
    Session session = SESSIONS.get(p.getUUID());
    if (session != null
        && p.getServer().getTickCount() - session.last <= SourceViewLimits.EXPIRE_TICKS) {
      session.dirty = true;
      PENDING.add(p.getUUID());
    }
  }

  static Reply flush(ServerPlayerEntity p) {
    Session session = SESSIONS.get(p.getUUID());
    int now = p.getServer().getTickCount();
    if (session == null || !session.dirty) return null;
    if (p.isSpectator() || now - session.last > SourceViewLimits.EXPIRE_TICKS) {
      forget(p.getUUID());
      return null;
    }
    if (now - session.captured < SourceViewLimits.REFRESH_TICKS) return null;
    session.captured = now;
    session.dirty = false;
    PENDING.remove(p.getUUID());
    try {
      return capture(p, session.token, session.ids);
    } catch (RuntimeException | LinkageError error) {
      WrenchBridge.LOG.debug("Native source display update was unavailable", error);
      return null;
    }
  }

  static void tick(net.minecraft.server.MinecraftServer server) {
    if (PENDING.isEmpty()) return;
    for (UUID id : new java.util.ArrayList<>(PENDING)) {
      ServerPlayerEntity player = server.getPlayerList().getPlayer(id);
      if (player == null) {
        forget(id);
        continue;
      }
      Reply reply = flush(player);
      if (reply != null) send(player, reply);
    }
  }

  public static byte[] encodeStack(net.minecraft.entity.player.PlayerEntity p, ItemStack source) {
    if (source.isEmpty()) return new byte[0];
    ByteBuf buffer = Unpooled.buffer(256, SourceViewLimits.MAX_STACK_BYTES);
    try {
      CompoundNBT tag = (CompoundNBT) (source.save(new CompoundNBT()));
      new net.minecraft.network.PacketBuffer(buffer).writeNbt(tag);
      byte[] bytes = new byte[buffer.readableBytes()];
      buffer.readBytes(bytes);
      return bytes;
    } catch (RuntimeException error) {
      WrenchBridge.LOG.debug("Could not snapshot native source tool", error);
      return new byte[0];
    } finally {
      buffer.release();
    }
  }

  public static ItemStack decodeStack(net.minecraft.entity.player.PlayerEntity p, byte[] bytes) {
    if (bytes.length == 0 || bytes.length > SourceViewLimits.MAX_STACK_BYTES)
      return ItemStack.EMPTY;
    ByteBuf buffer = Unpooled.wrappedBuffer(bytes);
    try {
      CompoundNBT tag = new net.minecraft.network.PacketBuffer(buffer).readNbt();
      return tag == null ? ItemStack.EMPTY : ItemStack.of(tag);
    } catch (RuntimeException error) {
      WrenchBridge.LOG.debug("Could not read native source snapshot", error);
      return ItemStack.EMPTY;
    } finally {
      buffer.release();
    }
  }

  private static void writeId(ByteBuf buffer, String id) {
    byte[] data = id.getBytes(StandardCharsets.UTF_8);
    if (data.length == 0 || data.length > SourceViewLimits.MAX_ID_BYTES)
      throw new IllegalArgumentException("Source ID too long");
    buffer.writeShort(data.length);
    buffer.writeBytes(data);
  }

  private static String readId(ByteBuf buffer) {
    int size = buffer.readUnsignedShort();
    if (size == 0 || size > SourceViewLimits.MAX_ID_BYTES)
      throw new IllegalArgumentException("Invalid source ID");
    byte[] data = new byte[size];
    buffer.readBytes(data);
    return new String(data, StandardCharsets.UTF_8);
  }

  public static void writeRequest(ByteBuf buffer, Request packet) {
    if (packet.ids.size() > SourceViewLimits.MAX_SOURCES)
      throw new IllegalArgumentException("Too many source IDs");
    buffer.writeInt(packet.token);
    buffer.writeByte(packet.ids.size());
    for (String id : packet.ids) writeId(buffer, id);
  }

  public static Request readRequest(ByteBuf buffer) {
    int token = buffer.readInt(), size = buffer.readUnsignedByte();
    if (size > SourceViewLimits.MAX_SOURCES)
      throw new IllegalArgumentException("Too many source IDs");
    List<String> ids = new ArrayList<String>();
    for (int i = 0; i < size; i++) ids.add(readId(buffer));
    return new Request(token, ids);
  }

  public static void writeReply(ByteBuf buffer, Reply packet) {
    if (packet.entries.size() > SourceViewLimits.MAX_SOURCES)
      throw new IllegalArgumentException("Too many source snapshots");
    buffer.writeInt(packet.token);
    buffer.writeByte(packet.entries.size());
    for (Entry entry : packet.entries) {
      writeId(buffer, entry.id);
      if (entry.data.length > SourceViewLimits.MAX_STACK_BYTES)
        throw new IllegalArgumentException("Source snapshot too large");
      buffer.writeInt(entry.data.length);
      buffer.writeBytes(entry.data);
    }
  }

  public static Reply readReply(ByteBuf buffer) {
    int token = buffer.readInt(), size = buffer.readUnsignedByte();
    if (size > SourceViewLimits.MAX_SOURCES)
      throw new IllegalArgumentException("Too many source snapshots");
    List<Entry> entries = new ArrayList<Entry>();
    for (int i = 0; i < size; i++) {
      String id = readId(buffer);
      int length = buffer.readInt();
      if (length < 0 || length > SourceViewLimits.MAX_STACK_BYTES)
        throw new IllegalArgumentException("Invalid source snapshot size");
      byte[] data = new byte[length];
      buffer.readBytes(data);
      entries.add(new Entry(id, data));
    }
    return new Reply(token, entries);
  }

  public static void register() {
    ModePacket.CHANNEL
        .messageBuilder(Request.class, 5, NetworkDirection.PLAY_TO_SERVER)
        .encoder((p, b) -> writeRequest(b, p))
        .decoder(SourceViewPacket::readRequest)
        .consumer(
            (p, c) -> {
              c.get()
                  .enqueueWork(
                      () -> {
                        var player = c.get().getSender();
                        if (player != null) {
                          Reply r = reply(player, p);
                          if (r != null) send(player, r);
                        }
                      });
              c.get().setPacketHandled(true);
            })
        .add();
    ModePacket.CHANNEL
        .messageBuilder(Reply.class, 6, NetworkDirection.PLAY_TO_CLIENT)
        .encoder((p, b) -> writeReply(b, p))
        .decoder(SourceViewPacket::readReply)
        .consumer(
            (p, c) -> {
              c.get().enqueueWork(() -> dev.wrenchbridge.client.SourceViewClient.receive(p));
              c.get().setPacketHandled(true);
            })
        .add();
  }

  public static void request(int token, List<String> ids) {
    ModePacket.CHANNEL.sendToServer(new Request(token, ids));
  }

  private static void send(ServerPlayerEntity player, Reply reply) {
    if (player.connection != null)
      ModePacket.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), reply);
  }
}
