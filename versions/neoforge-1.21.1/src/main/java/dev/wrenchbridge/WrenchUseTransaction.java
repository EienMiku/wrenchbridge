package dev.wrenchbridge;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** One synchronous server use, including native event listeners. No world scan or per-player tick work. */
public final class WrenchUseTransaction implements AutoCloseable {
    private static final ThreadLocal<WrenchUseTransaction> ACTIVE = new ThreadLocal<>();
    private record AuditKey(Level level, BlockPos pos, String kind, Object actor) {}
    private record Write(BlockState before, Runnable action) {}
    private final Map<AuditKey, Write> writes = new LinkedHashMap<>();
    private final Map<BlockPos, Boolean> permissions = new HashMap<>();
    private final ServerPlayer player;
    private final InteractionHand hand;
    private final BlockPos pos;
    private PlayerInteractEvent.RightClickBlock deferredEvent;
    private Runnable deferredCreate;
    private boolean runningCreate;

    private WrenchUseTransaction(ServerPlayer player, InteractionHand hand, BlockPos pos) {
        this.player = player; this.hand = hand; this.pos = pos.immutable();
    }

    public static InteractionResult use(ServerPlayer player, InteractionHand hand, ItemStack held,
            BlockHitResult hit, Supplier<InteractionResult> action) {
        if (ACTIVE.get() != null || !SharingControl.allows(player, held)
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
        if (scope == null || scope.runningCreate || BridgeEvents.isDispatching()
                || event.getEntity() != scope.player || event.getHand() != scope.hand || !event.getPos().equals(scope.pos)
                || scope.deferredEvent != null && scope.deferredEvent != event) return false;
        scope.deferredEvent = event; scope.deferredCreate = action;
        return true;
    }

    public static void runCreate(PlayerInteractEvent.RightClickBlock event) {
        WrenchUseTransaction scope = ACTIVE.get();
        if (scope == null || scope.deferredEvent != event || scope.deferredCreate == null) return;
        Runnable action = scope.deferredCreate;
        scope.deferredCreate = null; scope.deferredEvent = null;
        scope.runningCreate = true;
        try { action.run(); } finally { scope.runningCreate = false; }
    }

    public static boolean hasDeferredCreate(PlayerInteractEvent.RightClickBlock event) {
        WrenchUseTransaction scope = ACTIVE.get();
        return scope != null && scope.deferredEvent == event && scope.deferredCreate != null;
    }

    static boolean permission(ServerPlayer player, Level level, BlockPos pos, Supplier<Boolean> check) {
        WrenchUseTransaction scope = ACTIVE.get();
        return scope == null || scope.player != player || scope.player.level() != level ? check.get()
                : scope.permissions.computeIfAbsent(pos.immutable(), ignored -> check.get());
    }

    /** Only the optional GriefLogger break writers use this journal; protection callbacks still run now. */
    public static boolean deferAudit(Level level, BlockPos pos, String kind, Object actor, Runnable action) {
        WrenchUseTransaction scope = ACTIVE.get();
        if (scope == null) return false;
        AuditKey key = new AuditKey(level, pos.immutable(), kind, actor);
        scope.writes.computeIfAbsent(key, ignored -> new Write(level.getBlockState(pos), action));
        return true;
    }

    @Override public void close() {
        ACTIVE.remove();
        deferredCreate = null; deferredEvent = null;
        for (var entry : writes.entrySet()) {
            AuditKey key = entry.getKey(); Write write = entry.getValue();
            // Rotation/configuration, refusal, and missing sources have not removed the old block.
            if (key.level.getBlockState(key.pos).is(write.before.getBlock())) continue;
            try { write.action.run(); }
            catch (RuntimeException | LinkageError error) { WrenchBridge.LOG.warn("Could not commit native wrench audit at {}", key.pos, error); }
        }
    }
}
