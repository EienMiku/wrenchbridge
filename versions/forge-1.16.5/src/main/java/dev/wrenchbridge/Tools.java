package dev.wrenchbridge;

import dev.wrenchbridge.core.NativeModes;
import dev.wrenchbridge.core.ToolRules;
import java.util.stream.Collectors;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

public final class Tools {
    private Tools() {}
    public static String id(ItemStack stack) { return ForgeRegistries.ITEMS.getKey(stack.getItem()).toString(); }
    public static boolean candidate(ItemStack stack) {
        return !stack.isEmpty() && !(stack.getItem() instanceof BlockItem)
                && ToolRules.isCandidate(id(stack), stack.getItem().getTags().stream()
                    .map(Object::toString).collect(Collectors.toList()), false);
    }
    public static boolean active(ItemStack stack) {
        return candidate(stack) && ToolRules.allowsSharedDismantle(false, false, NativeModes.current(stack.getItem(), stack));
    }
    public static int universalSlot(PlayerEntity player) {
        for (int slot = 0; slot < player.inventory.getContainerSize(); slot++) {
            if (slot >= 36 && slot != 40) continue;
            if (player.inventory.getItem(slot).getItem() == WrenchBridge.WRENCH.get()) return slot;
        }
        return -1;
    }
}
