package dev.wrenchbridge;

import dev.wrenchbridge.core.NativeModes;
import dev.wrenchbridge.core.ToolRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ToolAction;

public final class Tools {
    private Tools() {}
    public static String id(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); }
    public static boolean candidate(ItemStack stack) {
        return !stack.isEmpty() && !(stack.getItem() instanceof BlockItem)
                && ToolRules.isCandidate(id(stack), stack.getTags().map(tag -> tag.location().toString()).toList(),
                stack.canPerformAction(ToolAction.get("wrench_dismantle")));
    }
    public static boolean active(ItemStack stack) {
        if (!candidate(stack)) return false;
        boolean dismantle = stack.canPerformAction(ToolAction.get("wrench_dismantle"));
        boolean other = stack.canPerformAction(ToolAction.get("wrench_configure"))
                || stack.canPerformAction(ToolAction.get("wrench_empty"))
                || stack.canPerformAction(ToolAction.get("wrench_rotate"));
        return ToolRules.allowsSharedDismantle(dismantle, other, NativeModes.current(stack.getItem(), stack));
    }
    public static int universalSlot(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (slot >= 36 && slot != 40) continue;
            if (player.getInventory().getItem(slot).is(WrenchBridge.WRENCH.get())) return slot;
        }
        return -1;
    }
}
