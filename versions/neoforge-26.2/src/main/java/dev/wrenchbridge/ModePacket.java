package dev.wrenchbridge;

import dev.wrenchbridge.core.NativeModes;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ModePacket(String itemId, String mode) implements CustomPacketPayload {
    public static final Type<ModePacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(WrenchBridge.ID, "mode"));
    public static final StreamCodec<ByteBuf, ModePacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(128), ModePacket::itemId,
            ByteBufCodecs.stringUtf8(64), ModePacket::mode, ModePacket::new);
    @Override public Type<ModePacket> type() { return TYPE; }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, CODEC, ModePacket::handle);
    }
    private static void handle(ModePacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || player.isSpectator()) return;
        int wrench = Tools.universalSlot(player);
        if (wrench < 0) return;
        int target = -1;
        if (packet.itemId.equals("wrenchbridge:universal_wrench") && packet.mode.isEmpty()) {
            StoredTools.returnBorrowed(player); target = wrench;
        }
        else {
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                if (slot >= 36 && slot != net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND) continue;
                ItemStack stack = player.getInventory().getItem(slot);
                if (!Tools.candidate(stack) || !Tools.id(stack).equals(packet.itemId)) continue;
                ItemStack copy = packet.mode.isEmpty() ? stack : stack.copy();
                if (!packet.mode.isEmpty()) {
                    try {
                        if (!NativeModes.select(copy.getItem(), copy, player, packet.mode)) return;
                    } catch (ReflectiveOperationException | RuntimeException error) {
                        WrenchBridge.LOG.warn("Mode setter rejected {} / {}", packet.itemId, packet.mode, error);
                        return;
                    }
                }
                // Native mode changes are committed only after validation; inventory swaps never synthesize tools.
                player.getInventory().setItem(slot, copy);
                StoredTools.replaced(player, stack, copy);
                target = slot;
                break;
            }
        }
        if (target < 0) { StoredTools.take(player, packet.itemId, packet.mode); return; }
        int hand = player.getInventory().getSelectedSlot();
        if (target != hand) {
            ItemStack original = player.getInventory().getItem(hand);
            ItemStack selected = player.getInventory().getItem(target);
            player.getInventory().setItem(hand, selected);
            player.getInventory().setItem(target, original);
        }
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
        player.sendSystemMessage(Component.translatable("message.wrenchbridge.selected", player.getMainHandItem().getHoverName()), true);
    }
}
