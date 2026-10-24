package dev.wrenchbridge;

import dev.wrenchbridge.core.ClassProbe;
import dev.wrenchbridge.core.NativeModes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/** Server-only discovery through container APIs. Never edits a foreign container's serialized data. */
@SuppressWarnings("removal")
public final class StoredTools {
    private record Located(ItemStack root, IItemHandler inventory, int slot, ItemStack tool) {}
    private record Lease(ItemStack root, int slot, ItemStack tool) {}
    private static final Map<UUID, Lease> BORROWED = new HashMap<>();
    private StoredTools() {}

    private static List<ItemStack> roots(Player player) {
        List<ItemStack> roots = new ArrayList<>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
            roots.add(player.getInventory().getItem(slot));
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Object optional = ClassProbe.compatibleMethod(api, "getCuriosInventory", player).invoke(null, player);
            if (optional instanceof Optional<?> value && value.isPresent()) {
                Object equipped = value.get().getClass().getMethod("getEquippedCurios").invoke(value.get());
                if (equipped instanceof IItemHandler items)
                    for (int slot = 0; slot < Math.min(64, items.getSlots()); slot++) roots.add(items.getStackInSlot(slot));
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // Curios is optional. Player inventory containers remain available if its API differs.
        }
        return roots;
    }

    private static IItemHandler inventory(ItemStack root) {
        if (root.isEmpty() || root.getCount() != 1) return null;
        var handler = root.getCapability(Capabilities.Item.ITEM, net.neoforged.neoforge.transfer.access.ItemAccess.forStack(root));
        return handler == null ? null : IItemHandler.of(handler);
    }

    private static List<Located> find(Player player) {
        List<Located> found = new ArrayList<>();
        int inspected = 0;
        Set<IItemHandler> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (ItemStack root : roots(player)) {
            try {
                IItemHandler items = inventory(root);
                if (items == null || !visited.add(items)) continue;
                for (int slot = 0; slot < Math.min(512, items.getSlots()) && inspected++ < 2048; slot++) {
                    ItemStack stack = items.getStackInSlot(slot);
                    if (Tools.candidate(stack) && !stack.is(WrenchBridge.WRENCH.get())
                            && !items.extractItem(slot, 1, true).isEmpty()) found.add(new Located(root, items, slot, stack));
                    if (found.size() >= 64) return found;
                }
            } catch (RuntimeException error) { WrenchBridge.LOG.debug("Container declined tool discovery", error); }
        }
        return found;
    }

    public static List<String> catalog(ServerPlayer player) {
        Set<String> rows = new LinkedHashSet<>();
        if (Tools.universalSlot(player) < 0) return List.of();
        for (Located entry : find(player)) {
            String id = Tools.id(entry.tool);
            if (id.length() > 128) continue;
            rows.add(id + "|");
            for (String mode : NativeModes.choices(entry.tool.getItem(), entry.tool, player)) {
                if (id.length() <= 128 && mode.length() <= 64 && rows.size() < 256) rows.add(id + "|" + mode);
            }
            if (rows.size() >= 256) break;
        }
        return new ArrayList<>(rows);
    }

    public static boolean take(ServerPlayer player, String id, String mode) {
        for (Located entry : find(player)) {
            if (!Tools.id(entry.tool).equals(id)) continue;
            ItemStack prepared = entry.tool.copyWithCount(1);
            try {
                if (!mode.isEmpty() && !NativeModes.select(prepared.getItem(), prepared, player, mode)) return false;
            } catch (ReflectiveOperationException | RuntimeException error) { return false; }
            int empty = -1;
            for (int slot = 0; slot < 36; slot++)
                if (slot != player.getInventory().getSelectedSlot() && player.getInventory().getItem(slot).isEmpty()) { empty = slot; break; }
            if (empty < 0 && !player.getMainHandItem().isEmpty()) {
                player.sendSystemMessage(Component.translatable("message.wrenchbridge.inventory_full"), true);
                return false;
            }
            ItemStack extracted = entry.inventory.extractItem(entry.slot, 1, false);
            if (extracted.isEmpty()) return false;
            // Item handlers own extraction, filtering and persistence. The actual stack supplies native state.
            if (!Tools.id(extracted).equals(id) || extracted.getCount() != 1) {
                preserve(player, entry.inventory.insertItem(entry.slot, extracted, false));
                return false;
            }
            if (!mode.isEmpty()) extracted = prepared;
            ItemStack previous = player.getMainHandItem();
            if (!previous.isEmpty()) player.getInventory().setItem(empty, previous);
            player.setItemInHand(InteractionHand.MAIN_HAND, extracted);
            BORROWED.put(player.getUUID(), new Lease(entry.root, entry.slot, extracted));
            changed(player);
            return true;
        }
        return false;
    }

    /** Return only the exact borrowed stack, while the original container is still carried and accepts it. */
    public static void returnBorrowed(ServerPlayer player) {
        Lease lease = BORROWED.remove(player.getUUID());
        if (lease == null || player.getMainHandItem() != lease.tool || roots(player).stream().noneMatch(root -> root == lease.root)) return;
        try {
            IItemHandler items = inventory(lease.root);
            if (items == null || lease.slot >= items.getSlots()
                    || !items.insertItem(lease.slot, lease.tool, true).isEmpty()) return;
            ItemStack remainder = items.insertItem(lease.slot, lease.tool, false);
            player.setItemInHand(InteractionHand.MAIN_HAND, remainder);
            changed(player);
        } catch (RuntimeException error) { WrenchBridge.LOG.warn("Container declined borrowed tool return", error); }
    }

    public static void replaced(ServerPlayer player, ItemStack old, ItemStack replacement) {
        Lease lease = BORROWED.get(player.getUUID());
        if (lease != null && lease.tool == old) BORROWED.put(player.getUUID(), new Lease(lease.root, lease.slot, replacement));
    }
    public static void forget(ServerPlayer player) { BORROWED.remove(player.getUUID()); }
    private static void preserve(ServerPlayer player, ItemStack remainder) {
        if (!remainder.isEmpty() && !player.getInventory().add(remainder)) player.drop(remainder, false);
    }
    private static void changed(ServerPlayer player) {
        player.getInventory().setChanged(); player.inventoryMenu.broadcastChanges(); player.containerMenu.broadcastChanges();
    }
}
