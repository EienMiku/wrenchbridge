package dev.wrenchbridge;

import java.util.function.Supplier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** A scoped view of wrench traits; it never changes a player's held item. */
public final class WrenchContext {
  private static final ThreadLocal<java.util.List<ItemStack>> ACTIVE = new ThreadLocal<>();
  private static final ThreadLocal<InteractionHand> HAND = new ThreadLocal<>();
  private static final ThreadLocal<ServerAction> SERVER_ACTION = new ThreadLocal<>();

  private WrenchContext() {}

  public static boolean forced(ItemStack stack) {
    var active = ACTIVE.get();
    return active != null
        && active.stream()
            .anyMatch(
                source -> source == stack || ItemStack.isSameItemSameComponents(source, stack));
  }

  public static boolean forcedItem(Object item) {
    var active = ACTIVE.get();
    return active != null && active.stream().anyMatch(stack -> stack.getItem() == item);
  }

  public static InteractionHand hand() {
    return HAND.get();
  }

  public static boolean serverOnly() {
    return SERVER_ACTION.get() != null;
  }

  public static net.minecraft.world.entity.Entity feedbackExclusion(
      net.minecraft.world.entity.Entity excluded) {
    var action = SERVER_ACTION.get();
    return action != null && excluded == action.player ? null : excluded;
  }

  private record LevelCue(
      net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
      int type,
      net.minecraft.core.BlockPos pos,
      int data) {}

  private static final class LevelFeedback {
    final java.util.Map<LevelCue, net.minecraft.network.protocol.game.ClientboundLevelEventPacket>
        pending = new java.util.LinkedHashMap<>();
    final java.util.Set<LevelCue> included = new java.util.HashSet<>();
  }

  public static final class ServerAction implements AutoCloseable {
    private final net.minecraft.server.level.ServerPlayer player;
    private final ServerAction previous;
    private final LevelFeedback levels;

    private ServerAction(net.minecraft.server.level.ServerPlayer player) {
      this.player = player;
      previous = SERVER_ACTION.get();
      levels =
          previous != null && previous.player == player ? previous.levels : new LevelFeedback();
      SERVER_ACTION.set(this);
    }

    @Override
    public void close() {
      if (previous == null) SERVER_ACTION.remove();
      else SERVER_ACTION.set(previous);
      if ((previous == null || previous.levels != levels)
          && player.connection != null
          && !player.hasDisconnected())
        for (var entry : levels.pending.entrySet())
          if (!levels.included.contains(entry.getKey())) player.connection.send(entry.getValue());
    }
  }

  /**
   * Observe actual, uncancelled broadcasts; defer only the actor's missing copy of level effects.
   */
  public static void levelFeedback(
      net.minecraft.world.entity.Entity excluded,
      double x,
      double y,
      double z,
      double range,
      net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
      net.minecraft.network.protocol.Packet<?> packet) {
    ServerAction action = SERVER_ACTION.get();
    if (action == null
        || !(packet
            instanceof net.minecraft.network.protocol.game.ClientboundLevelEventPacket event)
        || !action.player.level().dimension().equals(dimension)
        || action.player.distanceToSqr(x, y, z) >= range * range) return;
    var key = new LevelCue(dimension, event.getType(), event.getPos().immutable(), event.getData());
    if (excluded == action.player) action.levels.pending.putIfAbsent(key, event);
    else action.levels.included.add(key);
  }

  public static ServerAction serverAction(net.minecraft.server.level.ServerPlayer player) {
    return new ServerAction(player);
  }

  /** This native action has no client prediction; its feedback must include the actor. */
  public static <T> T runServer(
      net.minecraft.server.level.ServerPlayer player,
      InteractionHand hand,
      ItemStack held,
      ItemStack used,
      Supplier<T> action) {
    try (var feedback = serverAction(player)) {
      return run(hand, held, used, action);
    }
  }

  /**
   * Preserve native tag-based compatibility for an intentional shared use, including unknown
   * targets.
   */
  public static <T> T sharedUse(
      Player player, InteractionHand hand, ItemStack held, Supplier<T> action) {
    if (held.getItem() instanceof UniversalWrenchItem
        && !VirtualModes.isNative(held)
        && player.isShiftKeyDown()) return run(hand, held, held, action);
    return action.get();
  }

  public static <T> T run(
      InteractionHand hand, ItemStack held, ItemStack used, Supplier<T> action) {
    var previous = ACTIVE.get();
    var previousHand = HAND.get();
    // Capture the item identities before the callback can consume or replace either stack.
    WrenchTraitScope heldTraits =
        held.isEmpty() ? null : (WrenchTraitScope) (Object) held.getItem();
    WrenchTraitScope usedTraits =
        used.isEmpty() || used.getItem() == held.getItem()
            ? null
            : (WrenchTraitScope) (Object) used.getItem();
    if (heldTraits != null) heldTraits.wrenchbridge$retainTraits();
    try {
      if (usedTraits != null) usedTraits.wrenchbridge$retainTraits();
      try {
        ACTIVE.set(java.util.List.of(held, used));
        HAND.set(hand);
        try {
          return action.get();
        } finally {
          if (previous == null) ACTIVE.remove();
          else ACTIVE.set(previous);
          if (previousHand == null) HAND.remove();
          else HAND.set(previousHand);
        }
      } finally {
        if (usedTraits != null) usedTraits.wrenchbridge$releaseTraits();
      }
    } finally {
      if (heldTraits != null) heldTraits.wrenchbridge$releaseTraits();
    }
  }
}
