package dev.wrenchbridge;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.PacketDistributor;

public final class CatalogPacket {
  private CatalogPacket() {}

  public static final class Request {
    public Request() {}

    public boolean equals(Object o) {
      if (!(o instanceof Request)) return false;
      Request other = (Request) o;
      return true;
    }

    public int hashCode() {
      return java.util.Objects.hash();
    }
  }

  public static final class Reply {
    public final List<String> rows;

    public Reply(List<String> rows) {
      this.rows = rows;
    }

    public List<String> rows() {
      return rows;
    }

    public boolean equals(Object o) {
      if (!(o instanceof Reply)) return false;
      Reply other = (Reply) o;
      return java.util.Objects.equals(rows, other.rows);
    }

    public int hashCode() {
      return java.util.Objects.hash(rows);
    }
  }

  public static void register() {
    var channel = ModePacket.CHANNEL;
    channel
        .messageBuilder(Request.class, 1, NetworkDirection.PLAY_TO_SERVER)
        .encoder((packet, buffer) -> {})
        .decoder(buffer -> new Request())
        .consumer(
            (packet, context) -> {
              context
                  .get()
                  .enqueueWork(
                      () -> {
                        var player = context.get().getSender();
                        respond(player);
                      });
              context.get().setPacketHandled(true);
            })
        .add();
    channel
        .messageBuilder(Reply.class, 2, NetworkDirection.PLAY_TO_CLIENT)
        .encoder(
            (packet, buffer) -> {
              buffer.writeVarInt(packet.rows.size());
              for (String row : packet.rows) buffer.writeUtf(row, 256);
            })
        .decoder(buffer -> new Reply(read(buffer)))
        .consumer(
            (packet, context) -> {
              context
                  .get()
                  .enqueueWork(
                      () -> {
                        dev.wrenchbridge.client.ClientHooks.catalog(packet.rows);
                      });
              context.get().setPacketHandled(true);
            })
        .add();
  }

  private static List<String> read(PacketBuffer buffer) {
    int size = buffer.readVarInt();
    if (size < 0 || size > 256) throw new IllegalArgumentException("Invalid wrench catalog size");
    List<String> rows = new ArrayList<>();
    for (int i = 0; i < size; i++) rows.add(buffer.readUtf(256));
    return rows;
  }

  static void respond(net.minecraft.entity.player.ServerPlayerEntity player) {
    if (player == null) return;
    Reply reply = ServerRequests.catalog(player);
    if (reply != null) ModePacket.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), reply);
  }

  public static void request() {
    ModePacket.CHANNEL.sendToServer(new Request());
  }
}
