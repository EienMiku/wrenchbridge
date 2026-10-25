package dev.wrenchbridge;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
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
    final IBlockState before;
    final Runnable action;

    Write(IBlockState before, Runnable action) {
      this.before = before;
      this.action = action;
    }
  }

  private final Map<AuditKey, Write> writes = new LinkedHashMap<>();
  private final Map<BlockPos, Boolean> permissions = new HashMap<>();
  private final EntityPlayerMP player;
  private final EnumHand hand;
  private final BlockPos pos;
  private PlayerInteractEvent.RightClickBlock deferredEvent;
  private Runnable deferredCreate;
  private boolean runningCreate;

  private WrenchUseTransaction(EntityPlayerMP player, EnumHand hand, BlockPos pos) {
    this.player = player;
    this.hand = hand;
    this.pos = pos.toImmutable();
  }

  public static <T> T use(
      EntityPlayerMP player,
      EnumHand hand,
      ItemStack held,
      RayTraceResult hit,
      Supplier<T> action) {
    if (ACTIVE.get() != null
        || !SharingControl.allows(player, held)
        || !(held.getItem() instanceof UniversalWrenchItem)
            && (!player.isSneaking() || !Tools.candidate(held))) return action.get();
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
        || event.getEntityPlayer() != scope.player
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
      EntityPlayerMP player, World level, BlockPos pos, Supplier<Boolean> check) {
    WrenchUseTransaction scope = ACTIVE.get();
    return scope == null || scope.player != player || scope.player.world != level
        ? check.get()
        : scope.permissions.computeIfAbsent(pos.toImmutable(), ignored -> check.get());
  }

  /**
   * Only the optional GriefLogger break writers use this journal; protection callbacks still run
   * now.
   */
  public static boolean deferAudit(
      World level, BlockPos pos, String kind, Object actor, Runnable action) {
    WrenchUseTransaction scope = ACTIVE.get();
    if (scope == null) return false;
    AuditKey key = new AuditKey(level, pos.toImmutable(), kind, actor);
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
