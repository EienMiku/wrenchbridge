package dev.wrenchbridge;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record SharingPacket(boolean enabled) implements CustomPacketPayload {
  public static final Type<SharingPacket> TYPE =
      new Type<>(Identifier.fromNamespaceAndPath(WrenchBridge.ID, "other_wrench_sharing"));
  public static final StreamCodec<ByteBuf, SharingPacket> CODEC =
      ByteBufCodecs.BOOL.map(SharingPacket::new, SharingPacket::enabled);

  public Type<SharingPacket> type() {
    return TYPE;
  }

  public static void register(RegisterPayloadHandlersEvent event) {
    event
        .registrar("5")
        .playToServer(
            TYPE,
            CODEC,
            (packet, context) -> {
              if (context.player() instanceof ServerPlayer player) apply(packet, player);
            });
  }

  public static void apply(SharingPacket packet, ServerPlayer player) {
    dev.wrenchbridge.core.SharingPreferences.receive(player.getUUID(), packet.enabled);
  }
}
