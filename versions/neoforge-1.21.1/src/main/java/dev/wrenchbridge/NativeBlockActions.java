package dev.wrenchbridge;

import dev.wrenchbridge.core.RequestBudget;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Native GUI commits use one authenticated action path; the client never names a Java method or donor slot. */
public final class NativeBlockActions {
    private static final Map<ResourceLocation, NativeBlockProtocol> PROTOCOLS = Map.of(NativeInputRule.CREATE_ROTATION.action(), new CreateRotationProtocol());
    private static final Map<UUID, RequestBudget> BUDGETS = new HashMap<>();
    private static final ThreadLocal<PlayerInteractEvent.RightClickBlock> INPUT = new ThreadLocal<>();
    private NativeBlockActions() {}
    public static boolean input(PlayerInteractEvent.RightClickBlock event) { return INPUT.get() == event; }
    public static void forget(UUID player) { BUDGETS.remove(player); }
    public static void clear() { BUDGETS.clear(); }

    public static InteractionResult execute(ServerPlayer player, NativeBlockActionPacket request) {
        NativeBlockProtocol protocol = PROTOCOLS.get(request.action());
        if (protocol == null || !player.isAlive() || player.isSpectator() || !player.mayBuild()
                || !NativeToolViews.selected(player.getItemInHand(request.hand()), protocol.sourceItem())) return InteractionResult.FAIL;
        var held = player.getItemInHand(request.hand());
        String mode = VirtualModes.mode(held);
        int tick = player.getServer().getTickCount();
        if (!BUDGETS.computeIfAbsent(player.getUUID(), id -> new RequestBudget(8, 2, tick)).acquire(tick)) return InteractionResult.FAIL;
        BlockState base = state(request.baseState()), selected = state(request.selectedState());
        var level = player.serverLevel();
        if (base == null || selected == null || !player.canInteractWithBlock(request.pos(), 0)
                || !level.hasChunkAt(request.pos()) || !level.mayInteract(player, request.pos())
                || !player.mayUseItemAt(request.pos(), Direction.UP, player.getItemInHand(request.hand()))) return InteractionResult.FAIL;
        if (protocol.project(base, selected, level.getBlockState(request.pos())) == null) return InteractionResult.FAIL;
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(request.pos()), Direction.UP, request.pos(), false);
        try {
            return NativeToolExecution.use(player, request.hand(), protocol.sourceItem(), source ->
                    WrenchUseTransaction.use(player, request.hand(), player.getItemInHand(request.hand()), hit, () -> {
                        // This is the actual key-initiated interaction, not a speculative native-use callback.
                        // Keep the physical universal item visible while listeners run; our use adapter is skipped.
                        var event = new PlayerInteractEvent.RightClickBlock(player, request.hand(), request.pos(), hit);
                        var previous = INPUT.get(); INPUT.set(event);
                        try { NeoForge.EVENT_BUS.post(event); }
                        finally { if (previous == null) INPUT.remove(); else INPUT.set(previous); }
                        if (event.isCanceled()) return event.getCancellationResult();
                        if (player.getItemInHand(request.hand()) != held || !NativeToolViews.selected(held, protocol.sourceItem())
                                || !VirtualModes.mode(held).equals(mode)) return InteractionResult.FAIL;
                        if (event.getUseBlock() == TriState.FALSE || event.getUseItem() == TriState.FALSE && event.getUseBlock() != TriState.TRUE)
                            return InteractionResult.FAIL;
                        if (!BridgeEvents.breakAllowed(player, level, request.pos())) return InteractionResult.FAIL;
                        if (!player.isAlive() || player.isSpectator() || !player.mayBuild() || player.serverLevel() != level
                                || !player.canInteractWithBlock(request.pos(), 0) || player.getItemInHand(request.hand()) != held
                                || !NativeToolViews.selected(held, protocol.sourceItem()) || !VirtualModes.mode(held).equals(mode))
                            return InteractionResult.FAIL;
                        BlockState projected = protocol.project(base, selected, level.getBlockState(request.pos()));
                        if (projected == null) return InteractionResult.FAIL;
                        return WrenchContext.runServer(player, request.hand(), player.getItemInHand(request.hand()), source, () -> {
                            try { protocol.apply(player, request.pos(), projected, source); return InteractionResult.CONSUME; }
                            catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
                        });
                    }), InteractionResult.FAIL);
        } catch (ReflectiveOperationException | RuntimeException error) {
            WrenchBridge.LOG.warn("Native GUI action rejected {} at {}", request.action(), request.pos(), error);
            return InteractionResult.FAIL;
        }
    }
    private static BlockState state(int id) {
        return id < 0 || id >= Block.BLOCK_STATE_REGISTRY.size() ? null : Block.BLOCK_STATE_REGISTRY.byId(id);
    }
}
