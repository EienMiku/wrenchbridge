package dev.wrenchbridge;

import java.util.function.Supplier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Bind a native menu factory to the real donor's slot without changing either held item. */
public final class NativeMenuContext {
  private record Source(
      Player player, InteractionHand hand, int slot, ItemStack tool, java.util.UUID container) {}

  private static final ThreadLocal<Source> ACTIVE = new ThreadLocal<>();

  private NativeMenuContext() {}

  public static Integer inventorySlot(Player player, InteractionHand hand) {
    Source source = ACTIVE.get();
    return source != null
            && source.slot >= 0
            && source.player == player
            && source.hand == hand
            && player.getInventory().getItem(source.slot) == source.tool
        ? source.slot
        : null;
  }

  public static <T> T run(
      Player player, InteractionHand hand, int slot, ItemStack tool, Supplier<T> action) {
    Source previous = ACTIVE.get();
    ACTIVE.set(new Source(player, hand, slot, tool, null));
    try {
      return action.get();
    } finally {
      if (previous == null) ACTIVE.remove();
      else ACTIVE.set(previous);
    }
  }

  public static Object locator(
      Player player, InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
    Source source = ACTIVE.get();
    return source != null && source.player == player && source.hand == hand
        ? ContainerMenus.locator(player, source.container, hit)
        : null;
  }

  public static <T> T runContainer(
      Player player,
      InteractionHand hand,
      ItemStack tool,
      java.util.UUID container,
      Supplier<T> action) {
    Source previous = ACTIVE.get();
    ACTIVE.set(new Source(player, hand, -1, tool, container));
    try {
      return action.get();
    } finally {
      if (previous == null) ACTIVE.remove();
      else ACTIVE.set(previous);
    }
  }
}
