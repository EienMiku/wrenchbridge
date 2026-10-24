package dev.wrenchbridge.client;

import dev.wrenchbridge.WrenchBridge;
import dev.wrenchbridge.core.SharingPreferences;
import dev.wrenchbridge.core.SharingPreferences.ClientOption;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Available from the mod list and a separate key, without a held wrench or loaded world. */
public final class SharingSettingsScreen extends Screen {
  private final Screen parent;
  private final List<Button> optionButtons = new ArrayList<>();
  private Button resetButton;
  private boolean failed;

  public SharingSettingsScreen(Screen parent) {
    super(Component.translatable("screen.wrenchbridge.settings_title"));
    this.parent = parent;
  }

  private Component label(ClientOption option) {
    return Component.translatable(
        option.translationKey,
        Component.translatable(SharingPreferences.enabled(option) ? "options.on" : "options.off"));
  }

  protected void init() {
    optionButtons.clear();
    int w = Math.min(280, width - 32), top = Math.max(50, height / 2 - 54);
    for (ClientOption option : ClientOption.values()) {
      Button button =
          addRenderableWidget(
              Button.builder(label(option), clicked -> change(option))
                  .bounds((width - w) / 2, top + option.ordinal() * 24, w, 20)
                  .build());
      button.setTooltip(Tooltip.create(Component.translatable(option.hintKey)));
      optionButtons.add(button);
    }
    resetButton =
        addRenderableWidget(
            Button.builder(Component.translatable("setting.wrenchbridge.reset"), button -> reset())
                .bounds(width / 2 - 104, height - 32, 100, 20)
                .build());
    resetButton.setTooltip(
        Tooltip.create(Component.translatable("setting.wrenchbridge.reset_hint")));
    addRenderableWidget(
        Button.builder(Component.translatable("gui.done"), button -> onClose())
            .bounds(width / 2 + 4, height - 32, 100, 20)
            .build());
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

  public void extractRenderState(
      GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
    super.extractRenderState(graphics, mouseX, mouseY, partial);
    graphics.centeredText(font, title, width / 2, 16, 0xFFFFFFFF);
    graphics.centeredText(
        font,
        Component.translatable("screen.wrenchbridge.settings_saved"),
        width / 2,
        34,
        0xFFB5C8D5);
    graphics.centeredText(
        font,
        Component.translatable("screen.wrenchbridge.settings_key_hint"),
        width / 2,
        height - 66,
        0xFFB5C8D5);
    if (failed)
      graphics.centeredText(
          font,
          Component.translatable("setting.wrenchbridge.save_failed"),
          width / 2,
          height - 52,
          0xFFFF7777);
  }

  public boolean isPauseScreen() {
    return false;
  }
}
