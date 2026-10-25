package dev.wrenchbridge.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import dev.wrenchbridge.WrenchBridge;
import dev.wrenchbridge.core.SharingPreferences;
import dev.wrenchbridge.core.SharingPreferences.ClientOption;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.text.*;

/** Available from the mod list and a separate key, without a held wrench or loaded world. */
public final class SharingSettingsScreen extends Screen {
  private final Screen parent;
  private final List<Button> optionButtons = new ArrayList<>();
  private Button resetButton;
  private boolean failed;

  public SharingSettingsScreen(Screen parent) {
    super(new TranslationTextComponent("screen.wrenchbridge.settings_title"));
    this.parent = parent;
  }

  private ITextComponent label(ClientOption option) {
    return new TranslationTextComponent(
        option.translationKey,
        new TranslationTextComponent(
            SharingPreferences.enabled(option) ? "options.on" : "options.off"));
  }

  protected void init() {
    optionButtons.clear();
    int w = Math.min(280, width - 32), top = Math.max(50, height / 2 - 66);
    for (ClientOption option : ClientOption.values()) {
      Button button =
          addButton(
              new Button(
                  (width - w) / 2,
                  top + option.ordinal() * 24,
                  w,
                  20,
                  label(option),
                  clicked -> change(option)));

      optionButtons.add(button);
    }
    resetButton =
        addButton(
            new Button(
                width / 2 - 104,
                height - 32,
                100,
                20,
                new TranslationTextComponent("setting.wrenchbridge.reset"),
                button -> reset()));

    addButton(
        new Button(
            width / 2 + 4,
            height - 32,
            100,
            20,
            new TranslationTextComponent("gui.done"),
            button -> onClose()));
    refresh();
  }

  private void change(ClientOption option) {
    try {
      SharingClient.toggle(option);
      failed = false;
    } catch (IOException error) {
      failed = true;
      WrenchBridge.LOG.warn("Could not save client settings", error);
    }
    refresh();
  }

  private void reset() {
    try {
      SharingClient.reset();
      failed = false;
    } catch (IOException error) {
      failed = true;
      WrenchBridge.LOG.warn("Could not save client settings", error);
    }
    refresh();
  }

  private void refresh() {
    for (int i = 0; i < optionButtons.size(); i++)
      optionButtons.get(i).setMessage(label(ClientOption.values()[i]));
    resetButton.active = !SharingPreferences.clientDefaults();
  }

  public void onClose() {
    minecraft.setScreen(parent);
  }

  public void render(MatrixStack graphics, int mouseX, int mouseY, float partial) {
    renderBackground(graphics);
    super.render(graphics, mouseX, mouseY, partial);
    drawCenteredString(graphics, font, title, width / 2, 16, 0xFFFFFFFF);
    drawCenteredString(
        graphics,
        font,
        new TranslationTextComponent("screen.wrenchbridge.settings_saved"),
        width / 2,
        34,
        0xFFB5C8D5);
    drawCenteredString(
        graphics,
        font,
        new TranslationTextComponent("screen.wrenchbridge.settings_key_hint"),
        width / 2,
        height - 66,
        0xFFB5C8D5);
    if (failed)
      drawCenteredString(
          graphics,
          font,
          new TranslationTextComponent("setting.wrenchbridge.save_failed"),
          width / 2,
          height - 52,
          0xFFFF7777);
    for (int i = 0; i < optionButtons.size(); i++)
      if (optionButtons.get(i).isHovered())
        renderComponentTooltip(
            graphics,
            java.util.Collections.singletonList(
                new TranslationTextComponent(ClientOption.values()[i].hintKey)),
            mouseX,
            mouseY);
    if (resetButton.isHovered())
      renderComponentTooltip(
          graphics,
          java.util.Collections.singletonList(
              new TranslationTextComponent("setting.wrenchbridge.reset_hint")),
          mouseX,
          mouseY);
  }

  public boolean isPauseScreen() {
    return false;
  }
}
