package dev.wrenchbridge.client;

import dev.wrenchbridge.WrenchBridge;
import dev.wrenchbridge.core.SharingPreferences;
import java.io.IOException;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SharingSettingsScreen extends Screen {
  private final Screen parent;
  private boolean failed;

  public SharingSettingsScreen(Screen parent) {
    super(Component.translatable("screen.wrenchbridge.settings"));
    this.parent = parent;
  }

  private Component label() {
    return Component.translatable(
        "setting.wrenchbridge.other_wrenches",
        Component.translatable(SharingPreferences.clientEnabled() ? "options.on" : "options.off"));
  }

  protected void init() {
    int w = Math.min(280, width - 32);
    addRenderableWidget(
        Button.builder(
                label(),
                button -> {
                  try {
                    SharingClient.toggle();
                    failed = false;
                  } catch (IOException error) {
                    failed = true;
                    WrenchBridge.LOG.warn("Could not save client sharing preference", error);
                  }
                  button.setMessage(label());
                })
            .bounds((width - w) / 2, height / 2 - 42, w, 20)
            .build());
    addRenderableWidget(
        Button.builder(Component.translatable("gui.done"), button -> onClose())
            .bounds(width / 2 - 50, height - 32, 100, 20)
            .build());
  }

  public void onClose() {
    minecraft.setScreen(parent);
  }

  public void extractRenderState(
      GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
    super.extractRenderState(graphics, mouseX, mouseY, partial);
    graphics.centeredText(font, title, width / 2, 24, 0xFFFFFFFF);
    int y = height / 2 - 10;
    for (String key :
        new String[] {
          "setting.wrenchbridge.scope",
          "setting.wrenchbridge.native",
          "setting.wrenchbridge.universal"
        }) {
      graphics.centeredText(font, Component.translatable(key), width / 2, y, 0xFFB5C8D5);
      y += 14;
    }
    if (failed)
      graphics.centeredText(
          font,
          Component.translatable("setting.wrenchbridge.save_failed"),
          width / 2,
          height - 54,
          0xFFFF7777);
  }

  public boolean isPauseScreen() {
    return false;
  }
}
