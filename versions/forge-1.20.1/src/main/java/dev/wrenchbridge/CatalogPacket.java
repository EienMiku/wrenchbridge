package dev.wrenchbridge;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;

public final class CatalogPacket {
  private CatalogPacket() {}

  public record Request() {}

  public record Reply(List<String> rows) {}

  public static void register() {
    var channel = ModePacket.CHANNEL;
    channel
        .messageBuilder(Request.class, 1, NetworkDirection.PLAY_TO_SERVER)
        .encoder((packet, buffer) -> {})
        .decoder(buffer -> new Request())
        .consumerMainThread(
            (packet, context) -> {
              var player = context.get().getSender();
              respond(player);
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
        .consumerMainThread(
            (packet, context) -> {
              dev.wrenchbridge.client.ClientHooks.catalog(packet.rows);
              context.get().setPacketHandled(true);
            })
        .add();
  }

  private static List<String> read(FriendlyByteBuf buffer) {
    int size = buffer.readVarInt();
    if (size < 0 || size > 256) throw new IllegalArgumentException("Invalid wrench catalog size");
    List<String> rows = new ArrayList<>();
    for (int i = 0; i < size; i++) rows.add(buffer.readUtf(256));
    return rows;
  }

  static void respond(net.minecraft.server.level.ServerPlayer player) {
    if (player == null) return;
    Reply reply = ServerRequests.catalog(player);
    if (reply != null) ModePacket.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), reply);
  }

  public static void request() {
    ModePacket.CHANNEL.sendToServer(new Request());
  }
}
