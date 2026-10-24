package dev.wrenchbridge;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ModePacket(String itemId, String mode, boolean offhand)
    implements CustomPacketPayload {
  public ModePacket(String itemId, String mode) {
    this(itemId, mode, false);
  }

  public InteractionHand hand() {
    return offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
  }

  public static final Type<ModePacket> TYPE =
      new Type<>(Identifier.fromNamespaceAndPath(WrenchBridge.ID, "mode"));
  public static final StreamCodec<ByteBuf, ModePacket> CODEC =
      StreamCodec.composite(
          ByteBufCodecs.stringUtf8(128),
          ModePacket::itemId,
          ByteBufCodecs.stringUtf8(64),
          ModePacket::mode,
          ByteBufCodecs.BOOL,
          ModePacket::offhand,
          ModePacket::new);

  @Override
  public Type<ModePacket> type() {
    return TYPE;
  }

  public static void register(RegisterPayloadHandlersEvent event) {
    event.registrar("4").playToServer(TYPE, CODEC, ModePacket::handle);
  }

  private static void handle(ModePacket packet, IPayloadContext context) {
    if (context.player() instanceof ServerPlayer player) select(packet, player);
  }

  static void select(ModePacket packet, ServerPlayer player) {
    if (VirtualModes.select(player, packet.hand(), packet.itemId, packet.mode)) {
      ItemStack wrench = player.getItemInHand(packet.hand());
      var id = net.minecraft.resources.Identifier.tryParse(VirtualModes.item(wrench));
      Component selection =
          id == null
              ? Component.translatable("screen.wrenchbridge.shared")
              : NativeModeNames.choice(
                  new ItemStack(
                      net.minecraft.core.registries.BuiltInRegistries.ITEM
                          .get(id)
                          .orElseThrow()
                          .value()),
                  VirtualModes.mode(wrench));
      player.sendSystemMessage(
          Component.translatable("message.wrenchbridge.selected", selection), true);
    }
  }
}
