package dev.wrenchbridge.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import dev.wrenchbridge.WrenchBridge;
import dev.wrenchbridge.core.SharingPreferences;
import java.io.IOException;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.text.*;

public final class SharingSettingsScreen extends Screen {
  private final Screen parent;
  private boolean failed;

  public SharingSettingsScreen(Screen parent) {
    super(new TranslationTextComponent("screen.wrenchbridge.settings"));
    this.parent = parent;
  }

  private ITextComponent label() {
    return new TranslationTextComponent(
        "setting.wrenchbridge.other_wrenches",
        new TranslationTextComponent(
            SharingPreferences.clientEnabled() ? "options.on" : "options.off"));
  }

  protected void init() {
    int w = Math.min(280, width - 32);
    addButton(
        new Button(
            (width - w) / 2,
            height / 2 - 42,
            w,
            20,
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
            }));
    addButton(
        new Button(
            width / 2 - 50,
            height - 32,
            100,
            20,
            new TranslationTextComponent("gui.done"),
            button -> onClose()));
  }

  public void onClose() {
    minecraft.setScreen(parent);
  }

  public void render(MatrixStack graphics, int mouseX, int mouseY, float partial) {
    renderBackground(graphics);
    super.render(graphics, mouseX, mouseY, partial);
    drawCenteredString(graphics, font, title, width / 2, 24, 0xFFFFFF);
    int y = height / 2 - 10;
    for (String key :
        new String[] {
          "setting.wrenchbridge.scope",
          "setting.wrenchbridge.native",
          "setting.wrenchbridge.universal"
        }) {
      drawCenteredString(graphics, font, new TranslationTextComponent(key), width / 2, y, 0xB5C8D5);
      y += 14;
    }
    if (failed)
      drawCenteredString(
          graphics,
          font,
          new TranslationTextComponent("setting.wrenchbridge.save_failed"),
          width / 2,
          height - 54,
          0xFF7777);
  }

  public boolean isPauseScreen() {
    return false;
  }
}
