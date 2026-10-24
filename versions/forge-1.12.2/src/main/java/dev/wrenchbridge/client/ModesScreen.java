package dev.wrenchbridge.client;

import dev.wrenchbridge.ModePacket;
import dev.wrenchbridge.Tools;
import dev.wrenchbridge.WrenchBridge;
import dev.wrenchbridge.core.NativeModes;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

public final class ModesScreen extends GuiScreen {
  private static final class Choice {
    final ItemStack icon;
    final String itemId, mode, label;

    Choice(ItemStack icon, String itemId, String mode, String label) {
      this.icon = icon;
      this.itemId = itemId;
      this.mode = mode;
      this.label = label;
    }
  }

  private final List<Choice> choices = new ArrayList<Choice>();
  private final List<GuiButton> rows = new ArrayList<GuiButton>();
  private GuiTextField search;
  private final net.minecraft.util.EnumHand hand;
  private dev.wrenchbridge.core.ModeMenuLayout layout;

  public ModesScreen(net.minecraft.util.EnumHand hand) {
    this.hand = hand;
  }

  private int page, pageSize, footer;

  @Override
  public void initGui() {
    Keyboard.enableRepeatEvents(true);
    choices.clear();
    rows.clear();
    buttonList.clear();
    page = 0;
    layout = dev.wrenchbridge.core.ModeMenuLayout.forScreen(width, height);
    pageSize = layout.rows;
    footer = layout.footerY;
    choices.add(
        new Choice(
            new ItemStack(WrenchBridge.WRENCH),
            "wrenchbridge:universal_wrench",
            "",
            I18n.format("screen.wrenchbridge.shared")));
    Set<String> seen = new HashSet<String>();
    if (mc.player != null)
      for (int slot = 0; slot < mc.player.inventory.getSizeInventory(); slot++) {
        if (slot >= 36 && slot != 40) continue;
        ItemStack stack = mc.player.inventory.getStackInSlot(slot);
        if (!Tools.candidate(stack)
            || stack.getItem() == WrenchBridge.WRENCH
            || !seen.add(Tools.id(stack))) continue;
        choices.add(new Choice(stack.copy(), Tools.id(stack), "", stack.getDisplayName()));
        for (String mode : NativeModes.choices(stack.getItem(), stack, mc.player)) {
          String key = "mode.wrenchbridge." + mode.toLowerCase(Locale.ROOT);
          String label = I18n.hasKey(key) ? I18n.format(key) : mode.replace('_', ' ');
          choices.add(
              new Choice(
                  stack.copy(),
                  Tools.id(stack),
                  mode,
                  dev.wrenchbridge.NativeModeNames.choice(stack, mode).getFormattedText()));
        }
      }
    int left = layout.left;
    search = new GuiTextField(200, fontRenderer, left, layout.searchY, layout.width, 20);
    search.setMaxStringLength(100);
    search.setFocused(true);
    for (int row = 0; row < pageSize; row++) {
      GuiButton button =
          new GuiButton(
              row,
              left + 24,
              layout.rowY(row),
              layout.width - 24,
              dev.wrenchbridge.core.ModeMenuLayout.ROW_HEIGHT,
              "");
      rows.add(button);
      buttonList.add(button);
    }
    buttonList.add(new GuiButton(100, left, footer, 35, 20, "<"));
    buttonList.add(new GuiButton(101, left + layout.width - 35, footer, 35, 20, ">"));
    buttonList.add(
        new GuiButton(102, width / 2 - 50, layout.doneY, 100, 20, I18n.format("gui.done")));
    updateRows();
    dev.wrenchbridge.CatalogPacket.request();
  }

  public void catalog(List<String> entries) {
    Set<String> seen = new HashSet<String>();
    for (Choice choice : choices) seen.add(choice.itemId + "|" + choice.mode);
    for (String entry : entries) {
      int separator = entry.indexOf('|');
      if (separator < 1 || !seen.add(entry)) continue;
      net.minecraft.util.ResourceLocation id;
      try {
        id = new net.minecraft.util.ResourceLocation(entry.substring(0, separator));
      } catch (RuntimeException invalid) {
        continue;
      }
      if (!net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.containsKey(id)) continue;
      ItemStack stack =
          new ItemStack(net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.getValue(id));
      String mode = entry.substring(separator + 1);
      String label = stack.getDisplayName();
      if (!mode.isEmpty())
        label = dev.wrenchbridge.NativeModeNames.choice(stack, mode).getFormattedText();
      label += I18n.format("screen.wrenchbridge.storage");
      choices.add(new Choice(stack, id.toString(), mode, label));
    }
    updateRows();
  }

  private List<Choice> filtered() {
    String query = search == null ? "" : search.getText().toLowerCase(Locale.ROOT);
    List<Choice> list = new ArrayList<Choice>();
    for (Choice choice : choices)
      if (choice.label.toLowerCase(Locale.ROOT).contains(query)
          || choice.itemId.contains(query)
          || choice.mode.toLowerCase(Locale.ROOT).contains(query)) list.add(choice);
    return list;
  }

  private void updateRows() {
    List<Choice> list = filtered();
    for (int row = 0; row < rows.size(); row++) {
      int index = page * pageSize + row;
      GuiButton button = rows.get(row);
      button.visible = index < list.size();
      if (index < list.size()) button.displayString = list.get(index).label;
    }
  }

  @Override
  protected void actionPerformed(GuiButton button) throws IOException {
    if (button.id == 100) {
      page = Math.max(0, page - 1);
      updateRows();
      return;
    }
    if (button.id == 101) {
      page = Math.min(Math.max(0, (filtered().size() - 1) / pageSize), page + 1);
      updateRows();
      return;
    }
    if (button.id == 102) {
      mc.displayGuiScreen(null);
      return;
    }
    int index = page * pageSize + button.id;
    List<Choice> list = filtered();
    if (index < 0 || index >= list.size()) return;
    Choice choice = list.get(index);
    ModePacket.send(
        new ModePacket(choice.itemId, choice.mode, hand == net.minecraft.util.EnumHand.OFF_HAND));
    mc.displayGuiScreen(null);
  }

  @Override
  protected void keyTyped(char typed, int key) throws IOException {
    if (search.textboxKeyTyped(typed, key)) {
      page = 0;
      updateRows();
      return;
    }
    super.keyTyped(typed, key);
  }

  @Override
  protected void mouseClicked(int x, int y, int button) throws IOException {
    search.mouseClicked(x, y, button);
    super.mouseClicked(x, y, button);
  }

  @Override
  public void updateScreen() {
    if (search != null) search.updateCursorCounter();
    if (mc.player == null
        || hand == null
        || mc.player.getHeldItem(hand).getItem() != WrenchBridge.WRENCH) mc.displayGuiScreen(null);
  }

  @Override
  public void onGuiClosed() {
    Keyboard.enableRepeatEvents(false);
  }

  @Override
  public void drawScreen(int x, int y, float partialTick) {
    drawDefaultBackground();
    drawCenteredString(
        fontRenderer, I18n.format("screen.wrenchbridge.title"), width / 2, 16, 0xFFFFFF);
    drawCenteredString(
        fontRenderer, I18n.format("screen.wrenchbridge.description"), width / 2, 32, 0xB5C8D5);
    super.drawScreen(x, y, partialTick);
    search.drawTextBox();
    List<Choice> list = filtered();
    for (int row = 0; row < pageSize; row++) {
      int index = page * pageSize + row;
      if (index < list.size())
        mc.getRenderItem()
            .renderItemAndEffectIntoGUI(
                list.get(index).icon, layout.left + 2, layout.rowY(row) + 2);
    }
    drawCenteredString(
        fontRenderer,
        (page + 1) + " / " + Math.max(1, (list.size() + pageSize - 1) / pageSize),
        width / 2,
        footer + 6,
        0xFFFFFF);
    if (choices.size() == 1)
      drawCenteredString(
          fontRenderer, I18n.format("screen.wrenchbridge.carry"), width / 2, footer - 11, 0xE9BD70);
  }

  @Override
  public boolean doesGuiPauseGame() {
    return false;
  }
}
