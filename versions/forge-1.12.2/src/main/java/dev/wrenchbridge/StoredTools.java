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
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumHand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

/** Server-only discovery through container APIs. Never edits a foreign container's serialized data. */
public final class StoredTools {
    private static final class Located {
        final ItemStack root, tool; final IItemHandler inventory; final int slot;
        Located(ItemStack root, IItemHandler inventory, int slot, ItemStack tool) { this.root=root; this.inventory=inventory; this.slot=slot; this.tool=tool; }
    }
    private static final class Lease {
        final ItemStack root, tool; final int slot;
        Lease(ItemStack root, int slot, ItemStack tool) { this.root=root; this.slot=slot; this.tool=tool; }
    }
    private static final Map<UUID, Lease> BORROWED = new HashMap<>();
    private StoredTools() {}

    private static List<ItemStack> roots(EntityPlayer player) {
        List<ItemStack> roots = new ArrayList<>();
        for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++)
            roots.add(player.inventory.getStackInSlot(slot));
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Object optional = ClassProbe.compatibleMethod(api, "getCuriosInventory", player).invoke(null, player);
            if (!(optional instanceof Optional<?>)) optional = optional.getClass().getMethod("resolve").invoke(optional);
            if (optional instanceof Optional<?> && ((Optional<?>) optional).isPresent()) {
                Optional<?> value = (Optional<?>) optional;
                Object equipped = value.get().getClass().getMethod("getEquippedCurios").invoke(value.get());
                if (equipped instanceof IItemHandler) {
                    IItemHandler items = (IItemHandler) equipped;
                    for (int slot = 0; slot < Math.min(64, items.getSlots()); slot++) roots.add(items.getStackInSlot(slot));
                }
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // Curios is optional. Player inventory containers remain available if its API differs.
        }
        return roots;
    }

    private static IItemHandler inventory(ItemStack root) {
        return root.isEmpty() || root.getCount() != 1 ? null : root.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
    }

    private static List<Located> find(EntityPlayer player) {
        List<Located> found = new ArrayList<>();
        int inspected = 0;
        Set<IItemHandler> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (ItemStack root : roots(player)) {
            try {
                IItemHandler items = inventory(root);
                if (items == null || !visited.add(items)) continue;
                for (int slot = 0; slot < Math.min(512, items.getSlots()) && inspected++ < 2048; slot++) {
                    ItemStack stack = items.getStackInSlot(slot);
                    if (Tools.candidate(stack) && stack.getItem() != WrenchBridge.WRENCH
                            && !items.extractItem(slot, 1, true).isEmpty()) found.add(new Located(root, items, slot, stack));
                    if (found.size() >= 64) return found;
                }
            } catch (RuntimeException error) { WrenchBridge.LOG.debug("Container declined tool discovery", error); }
        }
        return found;
    }

    public static List<String> catalog(EntityPlayerMP player) {
        Set<String> rows = new LinkedHashSet<>();
        if (Tools.universalSlot(player) < 0) return java.util.Collections.emptyList();
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

    public static boolean take(EntityPlayerMP player, String id, String mode) {
        for (Located entry : find(player)) {
            if (!Tools.id(entry.tool).equals(id)) continue;
            ItemStack prepared = single(entry.tool);
            try {
                if (!mode.isEmpty() && !NativeModes.select(prepared.getItem(), prepared, player, mode)) return false;
            } catch (ReflectiveOperationException | RuntimeException error) { return false; }
            int empty = -1;
            for (int slot = 0; slot < 36; slot++)
                if (slot != player.inventory.currentItem && player.inventory.getStackInSlot(slot).isEmpty()) { empty = slot; break; }
            if (empty < 0 && !player.getHeldItemMainhand().isEmpty()) {
                player.sendStatusMessage(new TextComponentTranslation("message.wrenchbridge.inventory_full"), true);
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
            ItemStack previous = player.getHeldItemMainhand();
            if (!previous.isEmpty()) player.inventory.setInventorySlotContents(empty, previous);
            player.setHeldItem(EnumHand.MAIN_HAND, extracted);
            BORROWED.put(player.getUniqueID(), new Lease(entry.root, entry.slot, extracted));
            changed(player);
            return true;
        }
        return false;
    }

    /** Return only the exact borrowed stack, while the original container is still carried and accepts it. */
    public static void returnBorrowed(EntityPlayerMP player) {
        Lease lease = BORROWED.remove(player.getUniqueID());
        if (lease == null || player.getHeldItemMainhand() != lease.tool || roots(player).stream().noneMatch(root -> root == lease.root)) return;
        try {
            IItemHandler items = inventory(lease.root);
            if (items == null || lease.slot >= items.getSlots()
                    || !items.insertItem(lease.slot, lease.tool, true).isEmpty()) return;
            ItemStack remainder = items.insertItem(lease.slot, lease.tool, false);
            player.setHeldItem(EnumHand.MAIN_HAND, remainder);
            changed(player);
        } catch (RuntimeException error) { WrenchBridge.LOG.warn("Container declined borrowed tool return", error); }
    }

    public static void replaced(EntityPlayerMP player, ItemStack old, ItemStack replacement) {
        Lease lease = BORROWED.get(player.getUniqueID());
        if (lease != null && lease.tool == old) BORROWED.put(player.getUniqueID(), new Lease(lease.root, lease.slot, replacement));
    }
    public static void forget(EntityPlayerMP player) { BORROWED.remove(player.getUniqueID()); }
    private static void preserve(EntityPlayerMP player, ItemStack remainder) {
        if (!remainder.isEmpty() && !player.inventory.addItemStackToInventory(remainder)) player.dropItem(remainder, false);
    }
    private static ItemStack single(ItemStack stack) { ItemStack copy=stack.copy(); copy.setCount(1); return copy; }
    private static void changed(EntityPlayerMP player) {
        player.inventory.markDirty(); player.inventoryContainer.detectAndSendChanges(); player.openContainer.detectAndSendChanges();
    }
}
