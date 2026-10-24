package dev.wrenchbridge;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

public final class CatalogPacket {
    private CatalogPacket() {}
    public static final class Request implements IMessage {
        @Override public void fromBytes(ByteBuf buffer) {}
        @Override public void toBytes(ByteBuf buffer) {}
    }
    public static final class Reply implements IMessage {
        private List<String> rows = new ArrayList<String>();
        public Reply() {}
        Reply(List<String> rows) { this.rows=rows; }
        @Override public void fromBytes(ByteBuf buffer) {
            int count=buffer.readInt();
            if (count < 0 || count > 256) throw new IllegalArgumentException("Invalid wrench catalog size");
            rows=new ArrayList<String>();
            for (int i=0;i<count;i++) {
                String row=ByteBufUtils.readUTF8String(buffer);
                if (row.length()>256) throw new IllegalArgumentException("Invalid wrench catalog row");
                rows.add(row);
            }
        }
        @Override public void toBytes(ByteBuf buffer) {
            buffer.writeInt(rows.size()); for (String row:rows) ByteBufUtils.writeUTF8String(buffer,row);
        }
    }
    public static final class RequestHandler implements IMessageHandler<Request,IMessage> {
        @Override public IMessage onMessage(Request packet, MessageContext context) {
            EntityPlayerMP player=context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!player.isSpectator()) ModePacket.CHANNEL.sendTo(new Reply(StoredTools.catalog(player)),player);
            });
            return null;
        }
    }
    public static final class ReplyHandler implements IMessageHandler<Reply,IMessage> {
        @Override public IMessage onMessage(Reply packet, MessageContext context) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> dev.wrenchbridge.client.ClientHooks.catalog(packet.rows));
            return null;
        }
    }
    public static void register() {
        ModePacket.CHANNEL.registerMessage(RequestHandler.class, Request.class, 1, Side.SERVER);
        ModePacket.CHANNEL.registerMessage(ReplyHandler.class, Reply.class, 2, Side.CLIENT);
    }
    public static void request() { ModePacket.CHANNEL.sendToServer(new Request()); }
}
