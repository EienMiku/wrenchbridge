package dev.wrenchbridge.verification;

import dev.wrenchbridge.WrenchBridge;
import dev.wrenchbridge.client.ModesScreen;
import dev.wrenchbridge.client.SharingSettingsScreen;
import dev.wrenchbridge.core.SharingPreferences;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/** Exercises the real settings buttons, saved preference and client-to-server payload. */
public final class SharingClientVerification {
  private static int ticks, phase, since;
  private static boolean finished;
  private static final boolean RESUME = Boolean.getBoolean("wrenchbridge.verifySharingResume");

  private SharingClientVerification() {}

  public static void register(IEventBus bus) {
    bus.addListener(
        (FMLClientSetupEvent event) ->
            event.enqueueWork(
                () -> GLFW.glfwHideWindow(Minecraft.getInstance().getWindow().getWindow())));
    NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick());
  }

  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static void tick() {
    if (finished) return;
    ticks++;
    if (ticks > 1800) {
      finish("FAIL: client sharing check timed out");
      return;
    }
    var game = Minecraft.getInstance();
    if (game.player == null || game.level == null || game.getSingleplayerServer() == null) return;
    try {
      if (phase == 0) {
        check(SharingPreferences.clientEnabled() == !RESUME, "initial saved preference");
        var container = ModList.get().getModContainerById(WrenchBridge.ID).orElseThrow();
        var factory = container.getCustomExtension(IConfigScreenFactory.class).orElseThrow();
        Screen parent = new TitleScreen();
        game.setScreen(factory.createScreen(container, parent));
        check(game.screen instanceof SharingSettingsScreen, "mod-list configuration entry");
        check(
            button(game.screen, "gui.done").getY() + 20 <= game.screen.height,
            "footer outside screen");
        if (!RESUME) button(game.screen, "setting.wrenchbridge.other_wrenches", true).onPress();
        phase = 1;
        since = ticks;
      } else if (phase == 1 && ticks - since >= 15) {
        check(!SharingPreferences.clientEnabled(), "OFF not reflected locally");
        check(
            !serverEnabled(),
            RESUME ? "saved OFF not synchronized on login" : "UI OFF not synchronized");
        Path config = FMLPaths.CONFIGDIR.get().resolve("wrenchbridge-client.properties");
        check(Files.readString(config).contains("shareOtherWrenches=false"), "OFF not saved");
        SharingPreferences.load(config);
        check(!SharingPreferences.clientEnabled(), "reload lost OFF");
        if (!RESUME) {
          try (var screenshot = Screenshot.takeScreenshot(game.getMainRenderTarget())) {
            screenshot.writeToFile(Path.of("sharing-settings-off.png"));
          }
        }
        button(game.screen, "setting.wrenchbridge.other_wrenches", false).onPress();
        phase = 2;
        since = ticks;
      } else if (phase == 2 && ticks - since >= 15) {
        check(SharingPreferences.clientEnabled() && serverEnabled(), "UI ON not synchronized");
        var id = game.player.getUUID();
        game.getSingleplayerServer()
            .submit(
                () -> {
                  var player = game.getSingleplayerServer().getPlayerList().getPlayer(id);
                  player.setItemInHand(
                      InteractionHand.MAIN_HAND, new ItemStack(WrenchBridge.WRENCH.get()));
                  player.inventoryMenu.broadcastChanges();
                })
            .get(3, TimeUnit.SECONDS);
        game.setScreen(null);
        phase = 3;
        since = ticks;
      } else if (phase == 3 && ticks - since >= 15) {
        check(
            game.player.getMainHandItem().is(WrenchBridge.WRENCH.get()),
            "held universal not synced");
        game.setScreen(new ModesScreen(InteractionHand.MAIN_HAND));
        button(game.screen, "screen.wrenchbridge.settings").onPress();
        check(game.screen instanceof SharingSettingsScreen, "palette settings entry");
        button(game.screen, "gui.done").onPress();
        check(game.screen instanceof ModesScreen, "Done did not return to palette");
        button(game.screen, "screen.wrenchbridge.settings").onPress();
        if (!RESUME) button(game.screen, "setting.wrenchbridge.other_wrenches", true).onPress();
        phase = 4;
        since = ticks;
      } else if (phase == 4 && ticks - since >= 15) {
        check(serverEnabled() == RESUME, "final synchronization");
        finish(
            "PASS: settings factory, palette entry/return, UI OFF/ON, real C2S synchronization,"
                + " persistence"
                + (RESUME ? ", saved OFF restored on next login" : ", OFF saved for next launch"));
      }
    } catch (Throwable error) {
      WrenchBridge.LOG.error("Client sharing verification failed", error);
      finish("FAIL: " + error);
    }
  }

  private static Button button(Screen screen, String key) {
    return buttonWithLabel(screen, Component.translatable(key).getString());
  }

  private static Button button(Screen screen, String key, boolean enabled) {
    return buttonWithLabel(
        screen,
        Component.translatable(key, Component.translatable(enabled ? "options.on" : "options.off"))
            .getString());
  }

  private static Button buttonWithLabel(Screen screen, String label) {
    return screen.children().stream()
        .filter(Button.class::isInstance)
        .map(Button.class::cast)
        .filter(button -> button.getMessage().getString().equals(label))
        .findFirst()
        .orElseThrow();
  }

  private static boolean serverEnabled() throws Exception {
    var game = Minecraft.getInstance();
    var id = game.player.getUUID();
    return game.getSingleplayerServer()
        .submit(() -> SharingPreferences.allows(id, false, false))
        .get(3, TimeUnit.SECONDS);
  }

  private static void finish(String result) {
    if (finished) return;
    finished = true;
    try {
      Files.writeString(
          Path.of(RESUME ? "sharing-client-resume-result.txt" : "sharing-client-result.txt"),
          result + "\n");
    } catch (Exception error) {
      WrenchBridge.LOG.error("Could not save sharing check result", error);
    }
    WrenchBridge.LOG.info(result);
    Minecraft.getInstance().stop();
  }
}
