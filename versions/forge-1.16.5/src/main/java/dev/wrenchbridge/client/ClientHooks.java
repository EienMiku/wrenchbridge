package dev.wrenchbridge.client;

import dev.wrenchbridge.Tools;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

public final class ClientHooks {
    private static KeyBinding modes;
    private ClientHooks() {}
    public static void register(IEventBus bus) {
        bus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> {
            modes = new KeyBinding("key.wrenchbridge.modes", GLFW.GLFW_KEY_G, "key.categories.wrenchbridge");
            ClientRegistry.registerKeyBinding(modes);
        }));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase != TickEvent.Phase.END || modes == null) return;
            while (modes.consumeClick()) {
                Minecraft game = Minecraft.getInstance();
                if (game.screen == null && game.player != null && Tools.universalSlot(game.player) >= 0) open();
            }
        });
    }
    public static void open() { Minecraft.getInstance().setScreen(new ModesScreen()); }
    public static void catalog(java.util.List<String> rows) {
        if (Minecraft.getInstance().screen instanceof ModesScreen) ((ModesScreen) Minecraft.getInstance().screen).catalog(rows);
    }
}
