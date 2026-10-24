package dev.wrenchbridge;

import dev.wrenchbridge.core.NativeModes;
import dev.wrenchbridge.core.ToolRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.ItemAbility;

public final class Tools {
  private Tools() {}

  public static String id(ItemStack stack) {
    return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
  }

  public static boolean candidate(ItemStack stack) {
    return !stack.isEmpty()
        && !(stack.getItem() instanceof BlockItem)
        && ToolRules.isCandidate(
            id(stack),
            stack.typeHolder().tags().map(tag -> tag.location().toString()).toList(),
            stack.canPerformAction(ItemAbility.get("wrench_dismantle")));
  }

  public static boolean active(ItemStack stack) {
    if (!candidate(stack)) return false;
    // Powah's CONFIG/LINK/ROTATE modes affect ordinary use; native sneak dismantling is
    // mode-independent.
    if (dev.wrenchbridge.core.ClassProbe.is(stack.getItem(), "owmii.powah.api.wrench.IWrench"))
      return true;
    boolean dismantle = stack.canPerformAction(ItemAbility.get("wrench_dismantle"));
    boolean other =
        stack.canPerformAction(ItemAbility.get("wrench_configure"))
            || stack.canPerformAction(ItemAbility.get("wrench_empty"))
            || stack.canPerformAction(ItemAbility.get("wrench_rotate"));
    return ToolRules.allowsSharedDismantle(
        dismantle, other, NativeModes.current(stack.getItem(), stack));
  }

  public static int universalSlot(Player player) {
    for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
      if (slot >= 36 && slot != net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND) continue;
      if (player.getInventory().getItem(slot).is(WrenchBridge.WRENCH.get())) return slot;
    }
    return -1;
  }

  /** The palette targets a held universal wrench; ordinary tools never acquire its profiles. */
  public static InteractionHand universalHand(Player player) {
    if (player.getMainHandItem().is(WrenchBridge.WRENCH.get())) return InteractionHand.MAIN_HAND;
    if (player.getOffhandItem().is(WrenchBridge.WRENCH.get())) return InteractionHand.OFF_HAND;
    return null;
  }
}
