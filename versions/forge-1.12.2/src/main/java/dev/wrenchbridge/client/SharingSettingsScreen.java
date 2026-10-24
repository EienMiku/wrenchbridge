package dev.wrenchbridge.client;

import dev.wrenchbridge.WrenchBridge;
import dev.wrenchbridge.core.SharingPreferences;
import java.io.IOException;
import net.minecraft.client.gui.*;
import net.minecraft.client.resources.I18n;

public final class SharingSettingsScreen extends GuiScreen {
  private final GuiScreen parent;
  private boolean failed;

  public SharingSettingsScreen(GuiScreen parent) {
    this.parent = parent;
  }

  private String label() {
    return I18n.format(
        "setting.wrenchbridge.other_wrenches",
        I18n.format(SharingPreferences.clientEnabled() ? "options.on" : "options.off"));
  }

  public void initGui() {
    buttonList.clear();
    int w = Math.min(280, width - 32);
    buttonList.add(new GuiButton(0, (width - w) / 2, height / 2 - 42, w, 20, label()));
    buttonList.add(new GuiButton(1, width / 2 - 50, height - 32, 100, 20, I18n.format("gui.done")));
  }

  protected void actionPerformed(GuiButton button) throws IOException {
    if (button.id == 1) {
      mc.displayGuiScreen(parent);
      return;
    }
    if (button.id == 0) {
      try {
        SharingClient.toggle();
        failed = false;
      } catch (IOException error) {
        failed = true;
        WrenchBridge.LOG.warn("Could not save client sharing preference", error);
      }
      button.displayString = label();
    }
  }

  protected void keyTyped(char typed, int key) throws IOException {
    if (key == 1) {
      mc.displayGuiScreen(parent);
      return;
    }
    super.keyTyped(typed, key);
  }

  public void drawScreen(int x, int y, float partial) {
    drawDefaultBackground();
    super.drawScreen(x, y, partial);
    drawCenteredString(
        fontRenderer, I18n.format("screen.wrenchbridge.settings"), width / 2, 24, 0xFFFFFF);
    int line = height / 2 - 10;
    for (String key :
        new String[] {
          "setting.wrenchbridge.scope",
          "setting.wrenchbridge.native",
          "setting.wrenchbridge.universal"
        }) {
      drawCenteredString(fontRenderer, I18n.format(key), width / 2, line, 0xB5C8D5);
      line += 14;
    }
    if (failed)
      drawCenteredString(
          fontRenderer,
          I18n.format("setting.wrenchbridge.save_failed"),
          width / 2,
          height - 54,
          0xFF7777);
  }

  public boolean doesGuiPauseGame() {
    return false;
  }
}
