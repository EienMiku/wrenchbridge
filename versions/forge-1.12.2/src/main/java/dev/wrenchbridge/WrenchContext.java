package dev.wrenchbridge;

import java.util.function.Supplier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

/** A temporary native view. Player inventory slots retain their actual objects. */
public final class WrenchContext {
  private static final ThreadLocal<Scope> ACTIVE = new ThreadLocal<>();
  private static final ThreadLocal<EntityPlayerMP> ACTOR = new ThreadLocal<>();

  private static final class Scope {
    final EnumHand hand;
    final ItemStack held, tool;

    Scope(EnumHand hand, ItemStack held, ItemStack tool) {
      this.hand = hand;
      this.held = held;
      this.tool = tool;
    }
  }

  private WrenchContext() {}

  public static boolean forcedItem(Object item) {
    Scope s = ACTIVE.get();
    return s != null && (s.held.getItem() == item || s.tool.getItem() == item);
  }

  public static ItemStack tool() {
    Scope s = ACTIVE.get();
    return s == null ? ItemStack.EMPTY : s.tool;
  }

  public static EnumHand hand() {
    Scope s = ACTIVE.get();
    return s == null ? null : s.hand;
  }

  public static ItemStack nativeHand(EntityPlayer player, EnumHand hand) {
    Scope s = ACTIVE.get();
    return s != null && s.hand == hand ? s.tool : player.getHeldItem(hand);
  }

  public static EntityPlayer feedbackExclusion(EntityPlayer excluded) {
    return excluded == ACTOR.get() ? null : excluded;
  }

  public static boolean serverOnly() {
    return ACTOR.get() != null;
  }

  public static <T> T run(EnumHand hand, ItemStack held, ItemStack tool, Supplier<T> action) {
    Scope previous = ACTIVE.get();
    ACTIVE.set(new Scope(hand, held, tool));
    try {
      return action.get();
    } finally {
      if (previous == null) ACTIVE.remove();
      else ACTIVE.set(previous);
    }
  }

  public static <T> T runServer(
      EntityPlayerMP player, EnumHand hand, ItemStack tool, Supplier<T> action) {
    EntityPlayerMP previous = ACTOR.get();
    ACTOR.set(player);
    try {
      return run(hand, player.getHeldItem(hand), tool, action);
    } finally {
      if (previous == null) ACTOR.remove();
      else ACTOR.set(previous);
    }
  }

  public static <T> T sharedUse(
      EntityPlayer player, EnumHand hand, ItemStack held, Supplier<T> action) {
    return held.getItem() instanceof UniversalWrenchItem
            && !VirtualModes.isNative(held)
            && player.isSneaking()
        ? run(hand, held, held, action)
        : action.get();
  }
}
