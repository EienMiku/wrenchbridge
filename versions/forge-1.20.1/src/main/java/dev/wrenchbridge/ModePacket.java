package dev.wrenchbridge;
import dev.wrenchbridge.core.NativeModes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
public record ModePacket(String itemId, String mode) {
    static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(WrenchBridge.ID, "mode"))
            .networkProtocolVersion(() -> "1").clientAcceptedVersions("1"::equals).serverAcceptedVersions("1"::equals)
            .simpleChannel();
    public static void register() {
        CatalogPacket.register();
        CHANNEL.messageBuilder(ModePacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((packet, buffer) -> { buffer.writeUtf(packet.itemId, 128); buffer.writeUtf(packet.mode, 64); })
                .decoder(buffer -> new ModePacket(buffer.readUtf(128), buffer.readUtf(64)))
                .consumerMainThread((packet, context) -> { handle(packet, context.get().getSender()); context.get().setPacketHandled(true); })
                .add();
    }
    public static void send(ModePacket packet) { CHANNEL.sendToServer(packet); }
    private static void handle(ModePacket packet, ServerPlayer player) {
        if (player == null || player.isSpectator()) return;
        int wrench = Tools.universalSlot(player);
        if (wrench < 0) return;
        int target = -1;
        if (packet.itemId.equals("wrenchbridge:universal_wrench") && packet.mode.isEmpty()) {
            StoredTools.returnBorrowed(player); target = wrench;
        }
        else {
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                if (slot >= 36 && slot != 40) continue;
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
        int hand = player.getInventory().selected;
        if (target != hand) {
            ItemStack original = player.getInventory().getItem(hand);
            ItemStack selected = player.getInventory().getItem(target);
            player.getInventory().setItem(hand, selected);
            player.getInventory().setItem(target, original);
        }
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
        player.displayClientMessage(Component.translatable("message.wrenchbridge.selected", player.getMainHandItem().getHoverName()), true);
    }
}
