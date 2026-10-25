package dev.wrenchbridge;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public record ModePacket(String itemId, String mode, boolean offhand) {
  public ModePacket(String id, String mode) {
    this(id, mode, false);
  }

  public InteractionHand hand() {
    return offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
  }

  static final SimpleChannel CHANNEL =
      NetworkRegistry.ChannelBuilder.named(new ResourceLocation(WrenchBridge.ID, "mode"))
          .networkProtocolVersion(() -> "6")
          .clientAcceptedVersions("6"::equals)
          .serverAcceptedVersions("6"::equals)
          .simpleChannel();

  public static void register() {
    SharingPacket.register();
    SourceViewPacket.register();
    CatalogPacket.register();
    CHANNEL
        .messageBuilder(ModePacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
        .encoder(ModePacket::encode)
        .decoder(ModePacket::decode)
        .consumerMainThread(
            (p, c) -> {
              if (c.get().getSender() != null) select(p, c.get().getSender());
              c.get().setPacketHandled(true);
            })
        .add();
  }

  public static void encode(ModePacket p, net.minecraft.network.FriendlyByteBuf b) {
    b.writeUtf(p.itemId, 128);
    b.writeUtf(p.mode, 64);
    b.writeBoolean(p.offhand);
  }

  public static ModePacket decode(net.minecraft.network.FriendlyByteBuf b) {
    return new ModePacket(b.readUtf(128), b.readUtf(64), b.readBoolean());
  }

  public static void send(ModePacket p) {
    CHANNEL.sendToServer(p);
  }

  static void select(ModePacket packet, ServerPlayer player) {
    if (VirtualModes.select(player, packet.hand(), packet.itemId, packet.mode)) {
      ItemStack wrench = player.getItemInHand(packet.hand());
      var id = net.minecraft.resources.ResourceLocation.tryParse(VirtualModes.item(wrench));
      Component selection =
          id == null
              ? Component.translatable("screen.wrenchbridge.shared")
              : NativeModeNames.choice(
                  new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id)),
                  VirtualModes.mode(wrench));
      player.displayClientMessage(
          Component.translatable("message.wrenchbridge.selected", selection), true);
    }
  }
}
