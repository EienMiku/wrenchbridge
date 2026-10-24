package dev.wrenchbridge.verification;

import java.util.function.BiConsumer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.player.Player;

/** Observes the real server broadcast boundary, without adding recipients or playing sounds. */
public final class SoundObserver {
    public static volatile BiConsumer<Player, Packet<?>> listener;
    public static volatile java.util.function.Consumer<String> nativeListener;
    private SoundObserver() {}
    public static void broadcast(Player excluded, Packet<?> packet) {
        var current = listener;
        if (current != null) current.accept(excluded, packet);
    }
    public static void nativeCue(String cue) { var current = nativeListener; if (current != null) current.accept(cue); }
}
