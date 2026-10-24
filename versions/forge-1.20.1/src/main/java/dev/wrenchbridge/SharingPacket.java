package dev.wrenchbridge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;

public final class SharingPacket {
  public final boolean enabled;

  public SharingPacket(boolean enabled) {
    this.enabled = enabled;
  }

  public static void register() {
    ModePacket.CHANNEL
        .messageBuilder(SharingPacket.class, 4, NetworkDirection.PLAY_TO_SERVER)
        .encoder(SharingPacket::encode)
        .decoder(SharingPacket::decode)
        .consumerMainThread(
            (packet, context) -> {
              if (context.get().getSender() != null) apply(packet, context.get().getSender());
              context.get().setPacketHandled(true);
            })
        .add();
  }

  public static void encode(SharingPacket packet, net.minecraft.network.FriendlyByteBuf buffer) {
    buffer.writeBoolean(packet.enabled);
  }

  public static SharingPacket decode(net.minecraft.network.FriendlyByteBuf buffer) {
    return new SharingPacket(buffer.readBoolean());
  }

  public static void send(boolean enabled) {
    ModePacket.CHANNEL.sendToServer(new SharingPacket(enabled));
  }

  public static void apply(SharingPacket packet, ServerPlayer player) {
    dev.wrenchbridge.core.SharingPreferences.receive(player.getUUID(), packet.enabled);
  }
}
