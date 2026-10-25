package dev.wrenchbridge;

import dev.wrenchbridge.core.SourceViewLimits;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Bounded read-only snapshots of the authenticated player's own first matching tools. */
public final class SourceViewPacket {
  private SourceViewPacket() {}

  public static final class Request implements CustomPacketPayload {
    public final int token;
    public final List<String> ids;

    public Request(int token, List<String> ids) {
      this.token = token;
      this.ids = new ArrayList<String>(ids);
    }

    public static final Type<Request> TYPE =
        new Type<>(Identifier.parse("wrenchbridge:source_view_request"));
    public static final StreamCodec<ByteBuf, Request> CODEC =
        StreamCodec.of(SourceViewPacket::writeRequest, SourceViewPacket::readRequest);

    public Type<Request> type() {
      return TYPE;
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

  public static final class Reply implements CustomPacketPayload {
    public final int token;
    public final List<Entry> entries;

    public Reply(int token, List<Entry> entries) {
      this.token = token;
      this.entries = entries;
    }

    public static final Type<Reply> TYPE =
        new Type<>(Identifier.parse("wrenchbridge:source_view_reply"));
    public static final StreamCodec<ByteBuf, Reply> CODEC =
        StreamCodec.of(SourceViewPacket::writeReply, SourceViewPacket::readReply);

    public Type<Reply> type() {
      return TYPE;
    }
  }

  private static final class Session {
    int token, last;
    List<String> ids;

    Session(int token, int tick, List<String> ids) {
      this.token = token;
      this.last = tick;
      this.ids = new ArrayList<String>(ids);
    }
  }

  private static final Map<UUID, Session> SESSIONS = new HashMap<UUID, Session>();

  public static void forget(UUID player) {
    SESSIONS.remove(player);
  }

  public static void clearServer() {
    SESSIONS.clear();
  }

  public static Reply reply(ServerPlayer p, Request request) {
    if (p.isSpectator() || request.ids.size() > SourceViewLimits.MAX_SOURCES) return null;
    int now = p.level().getServer().getTickCount();
    Session previous = SESSIONS.get(p.getUUID());
    if (previous != null && now - previous.last < SourceViewLimits.REFRESH_TICKS) return null;
    List<String> ids = new ArrayList<String>(new LinkedHashSet<String>(request.ids));
    for (String id : ids)
      if (id.isEmpty() || id.length() > SourceViewLimits.MAX_ID_BYTES) return null;
    SESSIONS.put(p.getUUID(), new Session(request.token, now, ids));
    try {
      return capture(p, request.token, ids);
    } catch (RuntimeException | LinkageError error) {
      WrenchBridge.LOG.debug("Native source display was unavailable", error);
      return null;
    }
  }

  public static Reply capture(ServerPlayer p, int token, List<String> ids) {
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

  public static void changed(ServerPlayer p) {
    Session session = SESSIONS.get(p.getUUID());
    if (session != null
        && p.level().getServer().getTickCount() - session.last <= SourceViewLimits.EXPIRE_TICKS)
      try {
        send(p, capture(p, session.token, session.ids));
      } catch (RuntimeException | LinkageError error) {
        WrenchBridge.LOG.debug("Native source display update was unavailable", error);
      }
  }

  public static byte[] encodeStack(net.minecraft.world.entity.player.Player p, ItemStack source) {
    if (source.isEmpty()) return new byte[0];
    ByteBuf buffer = Unpooled.buffer(256, SourceViewLimits.MAX_STACK_BYTES);
    try {
      CompoundTag tag =
          (CompoundTag)
              (ItemStack.CODEC
                  .encodeStart(
                      net.minecraft.resources.RegistryOps.create(
                          net.minecraft.nbt.NbtOps.INSTANCE, p.registryAccess()),
                      source)
                  .getOrThrow());
      new net.minecraft.network.FriendlyByteBuf(buffer).writeNbt(tag);
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

  public static ItemStack decodeStack(net.minecraft.world.entity.player.Player p, byte[] bytes) {
    if (bytes.length == 0 || bytes.length > SourceViewLimits.MAX_STACK_BYTES)
      return ItemStack.EMPTY;
    ByteBuf buffer = Unpooled.wrappedBuffer(bytes);
    try {
      CompoundTag tag = new net.minecraft.network.FriendlyByteBuf(buffer).readNbt();
      return tag == null
          ? ItemStack.EMPTY
          : ItemStack.CODEC
              .parse(
                  net.minecraft.resources.RegistryOps.create(
                      net.minecraft.nbt.NbtOps.INSTANCE, p.registryAccess()),
                  tag)
              .result()
              .orElse(ItemStack.EMPTY);
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

  public static void register(RegisterPayloadHandlersEvent event) {
    var registrar = event.registrar("6");
    registrar.playToServer(
        Request.TYPE,
        Request.CODEC,
        (packet, context) -> {
          if (context.player() instanceof ServerPlayer player) {
            Reply result = reply(player, packet);
            if (result != null) send(player, result);
          }
        });
    registrar.playToClient(
        Reply.TYPE,
        Reply.CODEC,
        (packet, context) -> dev.wrenchbridge.client.SourceViewClient.receive(packet));
  }

  public static void request(int token, List<String> ids) {
    net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
        new Request(token, ids));
  }

  private static void send(ServerPlayer player, Reply reply) {
    if (player.connection != null) PacketDistributor.sendToPlayer(player, reply);
  }
}
