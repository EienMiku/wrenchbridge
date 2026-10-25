package dev.wrenchbridge.client;

import dev.wrenchbridge.*;
import java.util.WeakHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

/** A GUI outlives its input callback. Keep its hand/profile/target binding until it closes. */
public final class NativeScreenBindings {
    private record Binding(NativeInputRule rule, NativeClientViews.Projection projection, Level level,
            BlockPos pos, BlockState base, String mode) {}
    private static final Map<Screen, Binding> SCREENS = new WeakHashMap<>();
    private NativeScreenBindings() {}

    public static void bind(Screen screen, NativeInputRule rule, NativeClientViews.Projection projection,
            Level level, BlockPos pos, BlockState base) {
        SCREENS.put(screen, new Binding(rule, projection, level, pos.immutable(), base, VirtualModes.mode(projection.held())));
    }
    public static boolean bound(Screen screen) { return SCREENS.containsKey(screen); }
    public static boolean valid(Screen screen) {
        Binding binding = SCREENS.get(screen);
        if (binding == null) return true;
        var game = Minecraft.getInstance();
        return game.player != null && game.level == binding.level
                && game.player.getItemInHand(binding.projection.hand()) == binding.projection.held()
                && NativeToolViews.selected(binding.projection.held(), binding.rule.item())
                && VirtualModes.mode(binding.projection.held()).equals(binding.mode);
    }
    public static boolean submit(Screen screen, BlockState selected) {
        Binding binding = SCREENS.get(screen);
        if (binding == null) return false;
        if (valid(screen) && selected != binding.base)
            PacketDistributor.sendToServer(NativeBlockActionPacket.of(binding.rule.action(), binding.projection.hand(),
                    binding.pos, binding.base, selected));
        screen.onClose();
        return true;
    }
    public static void forget(Screen screen) { SCREENS.remove(screen); }
    public static void clear() { SCREENS.clear(); }
}
