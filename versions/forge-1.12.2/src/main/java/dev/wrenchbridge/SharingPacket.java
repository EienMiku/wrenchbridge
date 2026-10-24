package dev.wrenchbridge;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;

public final class SharingPacket implements IMessage {
  public boolean enabled;

  public SharingPacket() {}

  public SharingPacket(boolean enabled) {
    this.enabled = enabled;
  }

  public void fromBytes(ByteBuf buffer) {
    enabled = buffer.readBoolean();
  }

  public void toBytes(ByteBuf buffer) {
    buffer.writeBoolean(enabled);
  }

  public static void register() {
    ModePacket.CHANNEL.registerMessage(Handler.class, SharingPacket.class, 4, Side.SERVER);
  }

  public static void send(boolean enabled) {
    ModePacket.CHANNEL.sendToServer(new SharingPacket(enabled));
  }

  public static void apply(SharingPacket packet, EntityPlayerMP player) {
    dev.wrenchbridge.core.SharingPreferences.receive(player.getUniqueID(), packet.enabled);
  }

  public static final class Handler implements IMessageHandler<SharingPacket, IMessage> {
    public IMessage onMessage(SharingPacket packet, MessageContext context) {
      EntityPlayerMP player = context.getServerHandler().player;
      player.getServerWorld().addScheduledTask(() -> apply(packet, player));
      return null;
    }
  }
}
