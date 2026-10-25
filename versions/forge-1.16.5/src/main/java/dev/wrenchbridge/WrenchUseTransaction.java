package dev.wrenchbridge;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/**
 * One synchronous server use, including native event listeners. No world scan or per-player tick
 * work.
 */
public final class WrenchUseTransaction implements AutoCloseable {
  private static final ThreadLocal<WrenchUseTransaction> ACTIVE = new ThreadLocal<>();

  private static final class AuditKey {
    final World level;
    final BlockPos pos;
    final String kind;
    final Object actor;

    AuditKey(World level, BlockPos pos, String kind, Object actor) {
      this.level = level;
      this.pos = pos;
      this.kind = kind;
      this.actor = actor;
    }

    public boolean equals(Object value) {
      if (!(value instanceof AuditKey)) return false;
      AuditKey other = (AuditKey) value;
      return level == other.level
          && pos.equals(other.pos)
          && kind.equals(other.kind)
          && java.util.Objects.equals(actor, other.actor);
    }

    public int hashCode() {
      return java.util.Objects.hash(level, pos, kind, actor);
    }
  }

  private static final class Write {
    final BlockState before;
    final Runnable action;

    Write(BlockState before, Runnable action) {
      this.before = before;
      this.action = action;
    }
  }

  private final Map<AuditKey, Write> writes = new LinkedHashMap<>();
  private final Map<BlockPos, Boolean> permissions = new HashMap<>();
  private final ServerPlayerEntity player;
  private final Hand hand;
  private final BlockPos pos;
  private PlayerInteractEvent.RightClickBlock deferredEvent;
  private Runnable deferredCreate;
  private boolean runningCreate;

  private WrenchUseTransaction(ServerPlayerEntity player, Hand hand, BlockPos pos) {
    this.player = player;
    this.hand = hand;
    this.pos = pos.immutable();
  }

  public static <T> T use(
      ServerPlayerEntity player,
      Hand hand,
      ItemStack held,
      BlockRayTraceResult hit,
      Supplier<T> action) {
    if (ACTIVE.get() != null
        || !SharingControl.allows(player, held)
        || !(held.getItem() instanceof UniversalWrenchItem)
            && (!player.isShiftKeyDown() || !Tools.candidate(held))) return action.get();
    try (var scope = new WrenchUseTransaction(player, hand, hit.getBlockPos())) {
      ACTIVE.set(scope);
      return action.get();
    }
  }

  /** Create's HIGH listener must observe the result of other HIGH protection listeners first. */
  public static boolean deferCreate(PlayerInteractEvent.RightClickBlock event, Runnable action) {
    WrenchUseTransaction scope = ACTIVE.get();
    if (scope == null
        || scope.runningCreate
        || BridgeEvents.isDispatching()
        || event.getPlayer() != scope.player
        || event.getHand() != scope.hand
        || !event.getPos().equals(scope.pos)
        || scope.deferredEvent != null && scope.deferredEvent != event) return false;
    scope.deferredEvent = event;
    scope.deferredCreate = action;
    return true;
  }

  public static void runCreate(PlayerInteractEvent.RightClickBlock event) {
    WrenchUseTransaction scope = ACTIVE.get();
    if (scope == null || scope.deferredEvent != event || scope.deferredCreate == null) return;
    Runnable action = scope.deferredCreate;
    scope.deferredCreate = null;
    scope.deferredEvent = null;
    scope.runningCreate = true;
    try {
      action.run();
    } finally {
      scope.runningCreate = false;
    }
  }

  public static boolean hasDeferredCreate(PlayerInteractEvent.RightClickBlock event) {
    WrenchUseTransaction scope = ACTIVE.get();
    return scope != null && scope.deferredEvent == event && scope.deferredCreate != null;
  }

  static boolean permission(
      ServerPlayerEntity player, World level, BlockPos pos, Supplier<Boolean> check) {
    WrenchUseTransaction scope = ACTIVE.get();
    return scope == null || scope.player != player || scope.player.level != level
        ? check.get()
        : scope.permissions.computeIfAbsent(pos.immutable(), ignored -> check.get());
  }

  /**
   * Only the optional GriefLogger break writers use this journal; protection callbacks still run
   * now.
   */
  public static boolean deferAudit(
      World level, BlockPos pos, String kind, Object actor, Runnable action) {
    WrenchUseTransaction scope = ACTIVE.get();
    if (scope == null) return false;
    AuditKey key = new AuditKey(level, pos.immutable(), kind, actor);
    scope.writes.computeIfAbsent(key, ignored -> new Write(level.getBlockState(pos), action));
    return true;
  }

  @Override
  public void close() {
    ACTIVE.remove();
    deferredCreate = null;
    deferredEvent = null;
    for (var entry : writes.entrySet()) {
      AuditKey key = entry.getKey();
      Write write = entry.getValue();
      // Rotation/configuration, refusal, and missing sources have not removed the old block.
      if (key.level.getBlockState(key.pos).getBlock() == write.before.getBlock()) continue;
      try {
        write.action.run();
      } catch (RuntimeException | LinkageError error) {
        WrenchBridge.LOG.warn("Could not commit native wrench audit at {}", key.pos, error);
      }
    }
  }
}
