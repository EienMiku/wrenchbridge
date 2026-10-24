package dev.wrenchbridge;
import dev.wrenchbridge.core.NativeModes;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;
public final class ModePacket {
    public final String itemId, mode;
    public ModePacket(String itemId, String mode) { this.itemId=itemId; this.mode=mode; }
    static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(WrenchBridge.ID, "mode"))
            .networkProtocolVersion(() -> "1").clientAcceptedVersions("1"::equals).serverAcceptedVersions("1"::equals)
            .simpleChannel();
    public static void register() {
        CatalogPacket.register();
        CHANNEL.messageBuilder(ModePacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((packet, buffer) -> { buffer.writeUtf(packet.itemId, 128); buffer.writeUtf(packet.mode, 64); })
                .decoder(buffer -> new ModePacket(buffer.readUtf(128), buffer.readUtf(64)))
                .consumer((packet, context) -> { context.get().enqueueWork(() -> handle(packet, context.get().getSender())); context.get().setPacketHandled(true); })
                .add();
    }
    public static void send(ModePacket packet) { CHANNEL.sendToServer(packet); }
    private static void handle(ModePacket packet, ServerPlayerEntity player) {
        if (player == null || player.isSpectator()) return;
        int wrench = Tools.universalSlot(player);
        if (wrench < 0) return;
        int target = -1;
        if (packet.itemId.equals("wrenchbridge:universal_wrench") && packet.mode.isEmpty()) { StoredTools.returnBorrowed(player); target = wrench; }
        else {
            for (int slot = 0; slot < player.inventory.getContainerSize(); slot++) {
                if (slot >= 36 && slot != 40) continue;
                ItemStack stack = player.inventory.getItem(slot);
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
                player.inventory.setItem(slot, copy); StoredTools.replaced(player, stack, copy);
                target = slot;
                break;
            }
        }
        if (target < 0) { StoredTools.take(player, packet.itemId, packet.mode); return; }
        int hand = player.inventory.selected;
        if (target != hand) {
            ItemStack original = player.inventory.getItem(hand);
            ItemStack selected = player.inventory.getItem(target);
            player.inventory.setItem(hand, selected);
            player.inventory.setItem(target, original);
        }
        player.inventory.setChanged();
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
        player.displayClientMessage(new TranslationTextComponent("message.wrenchbridge.selected", player.getMainHandItem().getHoverName()), true);
    }
}
