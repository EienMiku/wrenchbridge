package dev.wrenchbridge;

import dev.wrenchbridge.core.SourceViewLimits;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;

/** Bounded read-only snapshots of the authenticated player's own first matching tools. */
public final class SourceViewPacket {
  private SourceViewPacket() {}

  public static final class Request implements IMessage {
    public int token;
    public List<String> ids;

    public Request(int token, List<String> ids) {
      this.token = token;
      this.ids = new ArrayList<String>(ids);
    }

    public Request() {
      this(0, new ArrayList<String>());
    }

    public void toBytes(ByteBuf buffer) {
      writeRequest(buffer, this);
    }

    public void fromBytes(ByteBuf buffer) {
      Request decoded = readRequest(buffer);
      token = decoded.token;
      ids = decoded.ids;
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

  public static final class Reply implements IMessage {
    public int token;
    public List<Entry> entries;

    public Reply(int token, List<Entry> entries) {
      this.token = token;
      this.entries = entries;
    }

    public Reply() {
      this(0, new ArrayList<Entry>());
    }

    public void toBytes(ByteBuf buffer) {
      writeReply(buffer, this);
    }

    public void fromBytes(ByteBuf buffer) {
      Reply decoded = readReply(buffer);
      token = decoded.token;
      entries = decoded.entries;
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

  public static Reply reply(EntityPlayerMP p, Request request) {
    if (p.isSpectator() || request.ids.size() > SourceViewLimits.MAX_SOURCES) return null;
    int now = p.getServer().getTickCounter();
    Session previous = SESSIONS.get(p.getUniqueID());
    if (previous != null && now - previous.last < SourceViewLimits.REFRESH_TICKS) return null;
    List<String> ids = new ArrayList<String>(new LinkedHashSet<String>(request.ids));
    for (String id : ids)
      if (id.isEmpty() || id.length() > SourceViewLimits.MAX_ID_BYTES) return null;
    SESSIONS.put(p.getUniqueID(), new Session(request.token, now, ids));
    try {
      return capture(p, request.token, ids);
    } catch (RuntimeException | LinkageError error) {
      WrenchBridge.LOG.debug("Native source display was unavailable", error);
      return null;
    }
  }

  public static Reply capture(EntityPlayerMP p, int token, List<String> ids) {
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

  public static void changed(EntityPlayerMP p) {
    Session session = SESSIONS.get(p.getUniqueID());
    if (session != null
        && p.getServer().getTickCounter() - session.last <= SourceViewLimits.EXPIRE_TICKS)
      try {
        send(p, capture(p, session.token, session.ids));
      } catch (RuntimeException | LinkageError error) {
        WrenchBridge.LOG.debug("Native source display update was unavailable", error);
      }
  }

  public static byte[] encodeStack(net.minecraft.entity.player.EntityPlayer p, ItemStack source) {
    if (source.isEmpty()) return new byte[0];
    ByteBuf buffer = Unpooled.buffer(256, SourceViewLimits.MAX_STACK_BYTES);
    try {
      NBTTagCompound tag = (NBTTagCompound) (source.serializeNBT());
      net.minecraftforge.fml.common.network.ByteBufUtils.writeTag(buffer, tag);
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

  public static ItemStack decodeStack(net.minecraft.entity.player.EntityPlayer p, byte[] bytes) {
    if (bytes.length == 0 || bytes.length > SourceViewLimits.MAX_STACK_BYTES)
      return ItemStack.EMPTY;
    ByteBuf buffer = Unpooled.wrappedBuffer(bytes);
    try {
      NBTTagCompound tag = net.minecraftforge.fml.common.network.ByteBufUtils.readTag(buffer);
      return tag == null ? ItemStack.EMPTY : new ItemStack(tag);
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
    ModePacket.CHANNEL.registerMessage(RequestHandler.class, Request.class, 5, Side.SERVER);
    ModePacket.CHANNEL.registerMessage(ReplyHandler.class, Reply.class, 6, Side.CLIENT);
  }

  public static void request(int token, List<String> ids) {
    ModePacket.CHANNEL.sendToServer(new Request(token, ids));
  }

  private static void send(EntityPlayerMP player, Reply reply) {
    if (player.connection != null) ModePacket.CHANNEL.sendTo(reply, player);
  }

  public static class RequestHandler implements IMessageHandler<Request, IMessage> {
    public IMessage onMessage(Request packet, MessageContext context) {
      EntityPlayerMP player = context.getServerHandler().player;
      player
          .getServerWorld()
          .addScheduledTask(
              () -> {
                Reply result = reply(player, packet);
                if (result != null) send(player, result);
              });
      return null;
    }
  }

  public static class ReplyHandler implements IMessageHandler<Reply, IMessage> {
    public IMessage onMessage(Reply packet, MessageContext context) {
      net.minecraft.client.Minecraft.getMinecraft()
          .addScheduledTask(() -> dev.wrenchbridge.client.SourceViewClient.receive(packet));
      return null;
    }
  }
}
