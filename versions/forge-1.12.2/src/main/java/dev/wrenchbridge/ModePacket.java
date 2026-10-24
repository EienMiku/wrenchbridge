package dev.wrenchbridge;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public final class ModePacket implements IMessage {
  static final SimpleNetworkWrapper CHANNEL =
      NetworkRegistry.INSTANCE.newSimpleChannel(WrenchBridge.ID);
  public String itemId, mode;
  public boolean offhand;

  public ModePacket() {}

  public ModePacket(String itemId, String mode) {
    this(itemId, mode, false);
  }

  public ModePacket(String itemId, String mode, boolean offhand) {
    this.itemId = itemId;
    this.mode = mode;
    this.offhand = offhand;
  }

  @Override
  public void fromBytes(ByteBuf buffer) {
    itemId = ByteBufUtils.readUTF8String(buffer);
    mode = ByteBufUtils.readUTF8String(buffer);
    offhand = buffer.readBoolean();
    if (itemId.length() > 128 || mode.length() > 64)
      throw new IllegalArgumentException("Invalid wrench mode packet");
  }

  @Override
  public void toBytes(ByteBuf buffer) {
    ByteBufUtils.writeUTF8String(buffer, itemId);
    ByteBufUtils.writeUTF8String(buffer, mode);
    buffer.writeBoolean(offhand);
  }

  public static void register() {
    SharingPacket.register();
    CHANNEL.registerMessage(Handler.class, ModePacket.class, 0, Side.SERVER);
    CatalogPacket.register();
  }

  public static void send(ModePacket packet) {
    CHANNEL.sendToServer(packet);
  }

  public static final class Handler implements IMessageHandler<ModePacket, IMessage> {
    @Override
    public IMessage onMessage(ModePacket packet, MessageContext context) {
      EntityPlayerMP player = context.getServerHandler().player;
      player.getServerWorld().addScheduledTask(() -> select(packet, player));
      return null;
    }
  }

  private static void select(ModePacket packet, EntityPlayerMP player) {
    VirtualModes.select(
        player,
        packet.offhand
            ? net.minecraft.util.EnumHand.OFF_HAND
            : net.minecraft.util.EnumHand.MAIN_HAND,
        packet.itemId,
        packet.mode);
  }
}
