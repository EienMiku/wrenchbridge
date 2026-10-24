package dev.wrenchbridge.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.wrenchbridge.Tools;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

public final class ClientHooks {
    private static KeyMapping modes;
    private ClientHooks() {}
    public static void register(IEventBus bus) {
        bus.addListener((RegisterKeyMappingsEvent event) -> {
            modes = new KeyMapping("key.wrenchbridge.modes", InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_G, KeyMapping.Category.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("wrenchbridge", "modes")));
            event.register(modes);
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            if (modes == null) return;
            while (modes.consumeClick()) {
                var game = Minecraft.getInstance();
                if (game.gui.screen() == null && game.player != null && Tools.universalSlot(game.player) >= 0) open();
            }
        });
    }
    public static void open() { Minecraft.getInstance().gui.setScreen(new ModesScreen()); }
    public static void catalog(java.util.List<String> rows) {
        if (Minecraft.getInstance().gui.screen() instanceof ModesScreen screen) screen.catalog(rows);
    }
}
