package dev.wrenchbridge;

import java.util.function.Supplier;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

/** A scoped view of wrench traits; it never changes a player's held item. */
public final class WrenchContext {
  private static final ThreadLocal<java.util.List<ItemStack>> ACTIVE = new ThreadLocal<>();
  private static final ThreadLocal<Hand> HAND = new ThreadLocal<>();
  private static final ThreadLocal<ServerAction> SERVER_ACTION = new ThreadLocal<>();

  private WrenchContext() {}

  public static boolean forced(ItemStack stack) {
    var active = ACTIVE.get();
    return active != null
        && active.stream()
            .anyMatch(
                source ->
                    source == stack
                        || (source.getItem() == stack.getItem()
                            && ItemStack.tagMatches(source, stack)));
  }

  public static boolean forcedItem(Object item) {
    var active = ACTIVE.get();
    return active != null && active.stream().anyMatch(stack -> stack.getItem() == item);
  }

  public static ItemStack protocolHand(PlayerEntity player, Hand requested) {
    var active = ACTIVE.get();
    return active != null && HAND.get() == requested
        ? active.get(active.size() - 1)
        : player.getItemInHand(requested);
  }

  public static Hand hand() {
    return HAND.get();
  }

  public static boolean serverOnly() {
    return SERVER_ACTION.get() != null;
  }

  public static PlayerEntity feedbackExclusion(PlayerEntity excluded) {
    var action = SERVER_ACTION.get();
    return action != null && excluded == action.player ? null : excluded;
  }

  private static final class LevelCue {
    public final net.minecraft.util.RegistryKey<net.minecraft.world.World> dimension;
    public final int type;
    public final net.minecraft.util.math.BlockPos pos;
    public final int data;

    public LevelCue(
        net.minecraft.util.RegistryKey<net.minecraft.world.World> dimension,
        int type,
        net.minecraft.util.math.BlockPos pos,
        int data) {
      this.dimension = dimension;
      this.type = type;
      this.pos = pos;
      this.data = data;
    }

    public net.minecraft.util.RegistryKey<net.minecraft.world.World> dimension() {
      return dimension;
    }

    public int type() {
      return type;
    }

    public net.minecraft.util.math.BlockPos pos() {
      return pos;
    }

    public int data() {
      return data;
    }

    public boolean equals(Object o) {
      if (!(o instanceof LevelCue)) return false;
      LevelCue other = (LevelCue) o;
      return java.util.Objects.equals(dimension, other.dimension)
          && java.util.Objects.equals(type, other.type)
          && java.util.Objects.equals(pos, other.pos)
          && java.util.Objects.equals(data, other.data);
    }

    public int hashCode() {
      return java.util.Objects.hash(dimension, type, pos, data);
    }
  }

  private static final class LevelFeedback {
    final java.util.Map<LevelCue, net.minecraft.network.play.server.SPlaySoundEventPacket> pending =
        new java.util.LinkedHashMap<>();
    final java.util.Set<LevelCue> included = new java.util.HashSet<>();
  }

  public static final class ServerAction implements AutoCloseable {
    private final net.minecraft.entity.player.ServerPlayerEntity player;
    private final ServerAction previous;
    private final LevelFeedback levels;

    private ServerAction(net.minecraft.entity.player.ServerPlayerEntity player) {
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
      PlayerEntity excluded,
      double x,
      double y,
      double z,
      double range,
      net.minecraft.util.RegistryKey<net.minecraft.world.World> dimension,
      net.minecraft.network.IPacket<?> packet) {
    ServerAction action = SERVER_ACTION.get();
    if (action == null
        || !(packet instanceof net.minecraft.network.play.server.SPlaySoundEventPacket event)
        || !action.player.level.dimension().equals(dimension)
        || action.player.distanceToSqr(x, y, z) >= range * range) return;
    LevelCue key;
    var buffer = new net.minecraft.network.PacketBuffer(io.netty.buffer.Unpooled.buffer());
    try {
      event.write(buffer);
      key =
          new LevelCue(
              dimension, buffer.readInt(), buffer.readBlockPos().immutable(), buffer.readInt());
    } catch (java.io.IOException error) {
      throw new IllegalStateException(error);
    } finally {
      buffer.release();
    }
    if (excluded == action.player) action.levels.pending.putIfAbsent(key, event);
    else action.levels.included.add(key);
  }

  public static ServerAction serverAction(net.minecraft.entity.player.ServerPlayerEntity player) {
    return new ServerAction(player);
  }

  public static <T> T runServer(
      net.minecraft.entity.player.ServerPlayerEntity player,
      Hand hand,
      ItemStack tool,
      Supplier<T> action) {
    return runServer(player, hand, player.getItemInHand(hand), tool, action);
  }

  /** This native action has no client prediction; its feedback must include the actor. */
  public static <T> T runServer(
      net.minecraft.entity.player.ServerPlayerEntity player,
      Hand hand,
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
      PlayerEntity player, Hand hand, ItemStack held, Supplier<T> action) {
    if (held.getItem() instanceof UniversalWrenchItem
        && !VirtualModes.isNative(held)
        && player.isShiftKeyDown()) return run(hand, held, held, action);
    return action.get();
  }

  public static <T> T run(Hand hand, ItemStack held, ItemStack used, Supplier<T> action) {
    var previous = ACTIVE.get();
    var previousHand = HAND.get();
    ACTIVE.set(java.util.Arrays.asList(held, used));
    HAND.set(hand);
    try {
      return action.get();
    } finally {
      if (previous == null) ACTIVE.remove();
      else ACTIVE.set(previous);
      if (previousHand == null) HAND.remove();
      else HAND.set(previousHand);
    }
  }
}
