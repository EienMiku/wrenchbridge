package dev.wrenchbridge.client;

import dev.wrenchbridge.NativeInputRule;
import dev.wrenchbridge.core.ClassProbe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Narrow callback scope: only opted-in native reads see the projection, never global item queries. */
public final class NativeClientCallbacks {
    private record Active(Player player, NativeInputRule rule, NativeClientViews.Projection projection,
            Level level, BlockPos pos, BlockState base) {}
    private static final ThreadLocal<Active> ACTIVE = new ThreadLocal<>();
    private NativeClientCallbacks() {}

    public static void input(NativeInputRule rule, Runnable original) {
        var game = Minecraft.getInstance();
        if (game.player == null || game.screen != null || !(game.hitResult instanceof BlockHitResult hit)) {
            original.run(); return;
        }
        var projection = NativeClientViews.input(game.player, rule.item());
        if (projection == null) { original.run(); return; }
        var previous = ACTIVE.get();
        var level = game.level;
        var base = level.getBlockState(hit.getBlockPos());
        ACTIVE.set(new Active(game.player, rule, projection, level, hit.getBlockPos().immutable(), base));
        try { original.run(); }
        finally { if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous); }
    }

    /** Screen factories can defer display until the next frame; bind construction, not display timing. */
    public static void screenCreated(Screen screen) {
        Active active = ACTIVE.get();
        if (active != null && ClassProbe.is(screen, active.rule.screenClass()))
            NativeScreenBindings.bind(screen, active.rule, active.projection, active.level, active.pos, active.base);
    }

    public static ItemStack mainHand(Player player) {
        Active active = ACTIVE.get();
        return active != null && active.player == player ? active.projection.tool() : player.getMainHandItem();
    }
}
