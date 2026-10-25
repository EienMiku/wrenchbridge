package dev.wrenchbridge;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;

public final class ModePacket {
  public final String itemId;
  public final String mode;
  public final boolean offhand;

  public ModePacket(String itemId, String mode, boolean offhand) {
    this.itemId = itemId;
    this.mode = mode;
    this.offhand = offhand;
  }

  public String itemId() {
    return itemId;
  }

  public String mode() {
    return mode;
  }

  public boolean offhand() {
    return offhand;
  }

  public boolean equals(Object o) {
    if (!(o instanceof ModePacket)) return false;
    ModePacket other = (ModePacket) o;
    return java.util.Objects.equals(itemId, other.itemId)
        && java.util.Objects.equals(mode, other.mode)
        && java.util.Objects.equals(offhand, other.offhand);
  }

  public int hashCode() {
    return java.util.Objects.hash(itemId, mode, offhand);
  }

  public ModePacket(String id, String mode) {
    this(id, mode, false);
  }

  public Hand hand() {
    return offhand ? Hand.OFF_HAND : Hand.MAIN_HAND;
  }

  static final SimpleChannel CHANNEL =
      NetworkRegistry.ChannelBuilder.named(new ResourceLocation(WrenchBridge.ID, "mode"))
          .networkProtocolVersion(() -> "7")
          .clientAcceptedVersions("7"::equals)
          .serverAcceptedVersions("7"::equals)
          .simpleChannel();

  public static void register() {
    SharingPacket.register();
    SourceViewPacket.register();
    CatalogPacket.register();
    CHANNEL
        .messageBuilder(ModePacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
        .encoder(ModePacket::encode)
        .decoder(ModePacket::decode)
        .consumer(
            (p, c) -> {
              c.get()
                  .enqueueWork(
                      () -> {
                        if (c.get().getSender() != null)
                          ServerRequests.mode(p, c.get().getSender());
                      });
              c.get().setPacketHandled(true);
            })
        .add();
  }

  public static void encode(ModePacket p, net.minecraft.network.PacketBuffer b) {
    b.writeUtf(p.itemId, 128);
    b.writeUtf(p.mode, 64);
    b.writeBoolean(p.offhand);
  }

  public static ModePacket decode(net.minecraft.network.PacketBuffer b) {
    return new ModePacket(b.readUtf(128), b.readUtf(64), b.readBoolean());
  }

  public static void send(ModePacket p) {
    CHANNEL.sendToServer(p);
  }

  static boolean select(ModePacket packet, ServerPlayerEntity player) {
    if (VirtualModes.select(player, packet.hand(), packet.itemId, packet.mode)) {
      ItemStack wrench = player.getItemInHand(packet.hand());
      var id =
          VirtualModes.isNative(wrench)
              ? net.minecraft.util.ResourceLocation.tryParse(VirtualModes.item(wrench))
              : null;
      ITextComponent selection =
          id == null
              ? new net.minecraft.util.text.TranslationTextComponent("screen.wrenchbridge.shared")
              : NativeModeNames.choice(
                  new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id)),
                  VirtualModes.mode(wrench));
      player.displayClientMessage(
          new net.minecraft.util.text.TranslationTextComponent(
              "message.wrenchbridge.selected", selection),
          true);
      return true;
    }
    return false;
  }
}
