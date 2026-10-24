package dev.wrenchbridge;

import dev.wrenchbridge.core.NativeModes;
import dev.wrenchbridge.core.ToolRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;

public final class Tools {
  private Tools() {}

  public static String id(ItemStack stack) {
    return ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
  }

  public static boolean candidate(ItemStack stack) {
    if (stack.isEmpty() || stack.getItem() instanceof ItemBlock) return false;
    List<String> tags = new ArrayList<String>();
    for (int id : OreDictionary.getOreIDs(stack))
      if (OreDictionary.getOreName(id).toLowerCase(Locale.ROOT).contains("wrench"))
        tags.add("c:tools/wrench");
    return dev.wrenchbridge.core.ClassProbe.is(
            stack.getItem(), "appeng.api.implementations.items.IAEWrench")
        || stack.getItem().getToolClasses(stack).contains("wrench")
        || ToolRules.isCandidate(id(stack), tags, false)
        || stack.getItem().getClass().getSimpleName().toLowerCase(Locale.ROOT).contains("wrench");
  }

  public static boolean active(ItemStack stack) {
    return candidate(stack)
        && ToolRules.allowsSharedDismantle(
            false, false, NativeModes.current(stack.getItem(), stack));
  }

  public static net.minecraft.util.EnumHand universalHand(EntityPlayer p) {
    if (p.getHeldItemMainhand().getItem() == WrenchBridge.WRENCH)
      return net.minecraft.util.EnumHand.MAIN_HAND;
    if (p.getHeldItemOffhand().getItem() == WrenchBridge.WRENCH)
      return net.minecraft.util.EnumHand.OFF_HAND;
    return null;
  }

  public static int universalSlot(EntityPlayer player) {
    for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
      if (slot >= 36 && slot != 40) continue;
      if (player.inventory.getStackInSlot(slot).getItem() == WrenchBridge.WRENCH) return slot;
    }
    return -1;
  }
}
