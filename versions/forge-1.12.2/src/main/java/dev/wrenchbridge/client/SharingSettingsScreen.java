package dev.wrenchbridge.client;

import dev.wrenchbridge.WrenchBridge;
import dev.wrenchbridge.core.SharingPreferences;
import dev.wrenchbridge.core.SharingPreferences.ClientOption;
import java.io.IOException;
import net.minecraft.client.gui.*;
import net.minecraft.client.resources.I18n;

/** Available from the mod list and a separate key, without a held wrench or loaded world. */
public final class SharingSettingsScreen extends GuiScreen {
  private final GuiScreen parent;
  private boolean failed;

  public SharingSettingsScreen(GuiScreen parent) {
    this.parent = parent;
  }

  private String label(ClientOption option) {
    return I18n.format(
        option.translationKey,
        I18n.format(SharingPreferences.enabled(option) ? "options.on" : "options.off"));
  }

  public void initGui() {
    buttonList.clear();
    int w = Math.min(280, width - 32), top = Math.max(50, height / 2 - 66);
    for (ClientOption option : ClientOption.values())
      buttonList.add(
          new GuiButton(
              option.ordinal(),
              (width - w) / 2,
              top + option.ordinal() * 24,
              w,
              20,
              label(option)));
    buttonList.add(
        new GuiButton(
            100, width / 2 - 104, height - 32, 100, 20, I18n.format("setting.wrenchbridge.reset")));
    buttonList.add(
        new GuiButton(101, width / 2 + 4, height - 32, 100, 20, I18n.format("gui.done")));
    refresh();
  }

  private void refresh() {
    for (GuiButton button : buttonList) {
      if (button.id < ClientOption.values().length)
        button.displayString = label(ClientOption.values()[button.id]);
      if (button.id == 100) button.enabled = !SharingPreferences.clientDefaults();
    }
  }

  protected void actionPerformed(GuiButton button) throws IOException {
    if (button.id == 101) {
      mc.displayGuiScreen(parent);
      return;
    }
    try {
      if (button.id == 100) SharingClient.reset();
      else SharingClient.toggle(ClientOption.values()[button.id]);
      failed = false;
    } catch (IOException error) {
      failed = true;
      WrenchBridge.LOG.warn("Could not save client settings", error);
    }
    refresh();
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
        fontRenderer, I18n.format("screen.wrenchbridge.settings_title"), width / 2, 16, 0xFFFFFF);
    drawCenteredString(
        fontRenderer, I18n.format("screen.wrenchbridge.settings_saved"), width / 2, 34, 0xB5C8D5);
    drawCenteredString(
        fontRenderer,
        I18n.format("screen.wrenchbridge.settings_key_hint"),
        width / 2,
        height - 66,
        0xB5C8D5);
    if (failed)
      drawCenteredString(
          fontRenderer,
          I18n.format("setting.wrenchbridge.save_failed"),
          width / 2,
          height - 52,
          0xFF7777);
    for (GuiButton button : buttonList) {
      if (x >= button.x
          && x < button.x + button.width
          && y >= button.y
          && y < button.y + button.height) {
        String key =
            button.id < ClientOption.values().length
                ? ClientOption.values()[button.id].hintKey
                : button.id == 100 ? "setting.wrenchbridge.reset_hint" : null;
        if (key != null)
          drawHoveringText(
              fontRenderer.listFormattedStringToWidth(I18n.format(key), Math.min(280, width - 24)),
              x,
              y);
      }
    }
  }

  public boolean doesGuiPauseGame() {
    return false;
  }
}
