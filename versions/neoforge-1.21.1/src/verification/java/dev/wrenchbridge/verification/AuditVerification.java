package dev.wrenchbridge.verification;

import dev.wrenchbridge.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Uses the installed GriefLogger implementation. The HIGH sentinels model permission outcomes, not FTB internals. */
public final class AuditVerification {
    private static final UUID PLAYER = UUID.fromString("55b2079c-cfe8-4625-96d9-60b44785f0b7");
    private static BlockPos watched;
    private static int blocks, containers, cleanup, itemCount, breakEvents;
    private static boolean denyBreak, denyInteract;
    private AuditVerification() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, true, BlockEvent.BreakEvent.class, event -> {
            if (event.getPos().equals(watched) && event.getPlayer().getUUID().equals(PLAYER)) {
                breakEvents++;
                if (denyBreak) event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, false, PlayerInteractEvent.RightClickBlock.class, event -> {
            if (event.getPos().equals(watched) && event.getEntity().getUUID().equals(PLAYER) && denyInteract)
                event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
        });
    }
    public static void blockWrite(BlockPos pos) { if (pos.equals(watched)) blocks++; }
    public static void cleanupWrite(BlockPos pos) { if (pos.equals(watched)) cleanup++; }
    public static void containerWrite(BlockPos pos, List<?> items) {
        if (!pos.equals(watched)) return;
        containers++;
        for (Object item : items) try { itemCount += ((Number) item.getClass().getMethod("getCount").invoke(item)).intValue(); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void watch(BlockPos pos) { watched = pos; blocks = containers = cleanup = itemCount = breakEvents = 0; }
    private static Map<String, Object> row(String name) {
        return Map.of("scenario", name, "x", watched.getX(), "y", watched.getY(), "z", watched.getZ(),
                "break_events", breakEvents, "block_writes", blocks, "container_writes", containers,
                "interaction_cleanup", cleanup, "snapshot_items", itemCount);
    }
    private static void check(boolean ok, String text) { ReviewVerification.check(ok, "audit: " + text); }

    public static Object verify(ServerLevel level) throws Exception {
        if (!net.neoforged.fml.ModList.get().isLoaded("grieflogger")) return "SKIPPED: GriefLogger not installed";
        // Register after mod setup, deliberately later than Create at the same HIGH priority.
        register();
        ServerPlayer player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(PLAYER, "WBAuditProbe"));
        player.getInventory().clearContent(); player.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        Object users = Class.forName("com.daqem.grieflogger.database.service.Services").getField("USER").get(null);
        users.getClass().getMethod("insertOrUpdateName", UUID.class, String.class).invoke(users, PLAYER, "WBAuditProbe");
        player.setShiftKeyDown(true); player.setPose(net.minecraft.world.entity.Pose.CROUCHING);
        List<Map<String, Object>> results = new ArrayList<>(); int index = 0;
        try {
            // Real native writer calls must remain deferred, then be committed once for a real removal.
            for (String scenario : List.of("no-removal", "rotation-only", "removed", "exception-after-removal")) {
                boolean removed = scenario.equals("removed") || scenario.startsWith("exception-");
                BlockPos pos = new BlockPos(128 + index++ * 2, 110, 0);
                player.moveTo(pos.getX() + 0.5, 111, 2.5, 180, 0);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(WrenchBridge.WRENCH.get()));
                level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
                ((ChestBlockEntity) level.getBlockEntity(pos)).setItem(0, new ItemStack(Items.IRON_INGOT, 16));
                seedInteraction(player, level, pos);
                watch(pos);
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
                try {
                    WrenchUseTransaction.use(player, InteractionHand.MAIN_HAND, player.getMainHandItem(), hit, () -> {
                        for (int i = 0; i < 2; i++) NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), player));
                        check(blocks == 0 && containers == 0 && cleanup == 0, "native writes occurred before the action");
                        if (scenario.equals("rotation-only")) level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(ChestBlock.FACING, Direction.EAST));
                        if (removed) {
                            level.destroyBlock(pos, true, player);
                            if (scenario.startsWith("exception-")) throw new IllegalStateException("expected failure after removal");
                        }
                        return InteractionResult.FAIL;
                    });
                } catch (IllegalStateException expected) { if (!scenario.startsWith("exception-")) throw expected; }
                check(blocks == (removed ? 1 : 0) && containers == (removed ? 1 : 0) && cleanup == (removed ? 1 : 0)
                        && itemCount == (removed ? 16 : 0), scenario + " counts " + row(scenario));
                results.add(row(scenario)); watched = null; level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
            // A stale selected mode with no actual source must not emit even a break preflight.
            BlockPos missing = new BlockPos(128 + index++ * 2, 110, 0);
            player.getInventory().setItem(9, ReviewVerification.item("wrenchbridge:mode_wrench"));
            check(VirtualModes.select(player, InteractionHand.MAIN_HAND, "wrenchbridge:mode_wrench", "WRENCH"), "select missing-source fixture");
            player.getInventory().setItem(9, ItemStack.EMPTY);
            level.setBlockAndUpdate(missing, Blocks.CHEST.defaultBlockState()); seedInteraction(player, level, missing); watch(missing);
            player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(missing), Direction.UP, missing, false));
            check(!level.isEmptyBlock(missing) && breakEvents == 0 && blocks == 0 && containers == 0 && cleanup == 0, "missing source produced an audit");
            results.add(row("virtual-missing-source")); watched = null; level.setBlockAndUpdate(missing, Blocks.AIR.defaultBlockState());

            if (net.neoforged.fml.ModList.get().isLoaded("create"))
                for (String style : List.of("universal", "foreign", "virtual")) for (InteractionHand hand : InteractionHand.values())
                    for (String permission : List.of("edit-denied", "interact-denied", "allowed")) {
                        BlockPos pos = new BlockPos(128 + index++ * 2, 110, 0);
                        player.getInventory().clearContent(); player.moveTo(pos.getX() + 0.5, 111, 2.5, 180, 0);
                        player.setItemInHand(hand, style.equals("foreign") ? ReviewVerification.item("wrenchbridge:foreign_wrench") : new ItemStack(WrenchBridge.WRENCH.get()));
                        if (style.equals("virtual")) {
                            player.getInventory().setItem(9, ReviewVerification.item("wrenchbridge:mode_wrench"));
                            check(VirtualModes.select(player, hand, "wrenchbridge:mode_wrench", "WRENCH"), "select Create source");
                        }
                        level.setBlockAndUpdate(pos, BuiltInBlock("create:andesite_casing"));
                        watch(pos); denyBreak = permission.equals("edit-denied"); denyInteract = permission.equals("interact-denied");
                        player.gameMode.useItemOn(player, level, player.getItemInHand(hand), hand,
                                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
                        boolean allowed = permission.equals("allowed");
                        check(level.isEmptyBlock(pos) == allowed && blocks == (allowed ? 1 : 0), style + " " + hand + " " + permission + " " + row(permission));
                        if (permission.equals("interact-denied")) check(breakEvents == 0, "Create acted before interaction permission");
                        if (allowed) check(breakEvents == 1, "Create received redundant bridge break preflight");
                        results.add(row("create-" + style + "-" + hand + "-" + permission));
                        denyBreak = denyInteract = false; watched = null; level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    }
            return results;
        } finally {
            denyBreak = denyInteract = false; watched = null; player.getInventory().clearContent(); player.closeContainer();
            Object database = Class.forName("com.daqem.grieflogger.GriefLogger").getMethod("getDatabase").invoke(null);
            for (String name : List.of("queue", "batchQueue")) {
                Object queue = database.getClass().getField(name).get(database);
                queue.getClass().getMethod("execute").invoke(queue);
            }
        }
    }
    private static net.minecraft.world.level.block.state.BlockState BuiltInBlock(String id) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse(id)).defaultBlockState();
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void seedInteraction(ServerPlayer player, ServerLevel level, BlockPos pos) throws Exception {
        Class<?> actor = Class.forName("com.daqem.grieflogger.player.GriefLoggerServerPlayer");
        Class<? extends Enum> action = (Class<? extends Enum>) Class.forName("com.daqem.grieflogger.model.action.BlockAction");
        Class.forName("com.daqem.grieflogger.event.block.LogBlockEvent").getMethod("logBlock", actor,
                net.minecraft.world.level.Level.class, net.minecraft.world.level.block.state.BlockState.class, BlockPos.class, action)
                .invoke(null, player, level, level.getBlockState(pos), pos, Enum.valueOf(action, "INTERACT_BLOCK"));
    }
}
