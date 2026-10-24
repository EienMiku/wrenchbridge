package dev.wrenchbridge;

import dev.wrenchbridge.core.NativeModes;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public final class ModePacket implements IMessage {
    static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(WrenchBridge.ID);
    public String itemId, mode;
    public ModePacket() { }
    public ModePacket(String itemId, String mode) { this.itemId = itemId; this.mode = mode; }
    @Override public void fromBytes(ByteBuf buffer) {
        itemId = ByteBufUtils.readUTF8String(buffer); mode = ByteBufUtils.readUTF8String(buffer);
        if (itemId.length() > 128 || mode.length() > 64) throw new IllegalArgumentException("Invalid wrench mode packet");
    }
    @Override public void toBytes(ByteBuf buffer) { ByteBufUtils.writeUTF8String(buffer, itemId); ByteBufUtils.writeUTF8String(buffer, mode); }
    public static void register() { CHANNEL.registerMessage(Handler.class, ModePacket.class, 0, Side.SERVER); CatalogPacket.register(); }
    public static void send(ModePacket packet) { CHANNEL.sendToServer(packet); }
    public static final class Handler implements IMessageHandler<ModePacket, IMessage> {
        @Override public IMessage onMessage(ModePacket packet, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> select(packet, player));
            return null;
        }
    }
    private static void select(ModePacket packet, EntityPlayerMP player) {
        int wrench = Tools.universalSlot(player);
        if (wrench < 0 || player.isSpectator()) return;
        int target = -1;
        if (packet.itemId.equals("wrenchbridge:universal_wrench") && packet.mode.isEmpty()) { StoredTools.returnBorrowed(player); target = wrench; }
        else for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
            if (slot >= 36 && slot != 40) continue;
            ItemStack stack = player.inventory.getStackInSlot(slot);
            if (!Tools.candidate(stack) || !Tools.id(stack).equals(packet.itemId)) continue;
            ItemStack copy = packet.mode.isEmpty() ? stack : stack.copy();
            if (!packet.mode.isEmpty()) {
                try { if (!NativeModes.select(copy.getItem(), copy, player, packet.mode)) return; }
                catch (ReflectiveOperationException | RuntimeException error) { WrenchBridge.LOG.warn("Native mode rejected", error); return; }
            }
            player.inventory.setInventorySlotContents(slot, copy); StoredTools.replaced(player, stack, copy); target = slot; break;
        }
        if (target < 0) { StoredTools.take(player, packet.itemId, packet.mode); return; }
        int hand = player.inventory.currentItem;
        if (target != hand) {
            ItemStack old = player.inventory.getStackInSlot(hand);
            ItemStack selected = player.inventory.getStackInSlot(target);
            player.inventory.setInventorySlotContents(hand, selected);
            player.inventory.setInventorySlotContents(target, old);
        }
        player.inventory.markDirty(); player.inventoryContainer.detectAndSendChanges();
    }
}
