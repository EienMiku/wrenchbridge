package dev.wrenchbridge.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import dev.wrenchbridge.ModePacket;
import dev.wrenchbridge.NativeModeNames;
import dev.wrenchbridge.Tools;
import dev.wrenchbridge.WrenchBridge;
import dev.wrenchbridge.core.ModeMenuLayout;
import dev.wrenchbridge.core.NativeModes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;

public final class ModesScreen extends Screen {
  private static final class Choice {
    public final ItemStack icon;
    public final String itemId;
    public final String mode;
    public final ITextComponent label;
    public final ITextComponent hint;

    public Choice(
        ItemStack icon, String itemId, String mode, ITextComponent label, ITextComponent hint) {
      this.icon = icon;
      this.itemId = itemId;
      this.mode = mode;
      this.label = label;
      this.hint = hint;
    }

    public ItemStack icon() {
      return icon;
    }

    public String itemId() {
      return itemId;
    }

    public String mode() {
      return mode;
    }

    public ITextComponent label() {
      return label;
    }

    public ITextComponent hint() {
      return hint;
    }

    public boolean equals(Object o) {
      if (!(o instanceof Choice)) return false;
      Choice other = (Choice) o;
      return java.util.Objects.equals(icon, other.icon)
          && java.util.Objects.equals(itemId, other.itemId)
          && java.util.Objects.equals(mode, other.mode)
          && java.util.Objects.equals(label, other.label)
          && java.util.Objects.equals(hint, other.hint);
    }

    public int hashCode() {
      return java.util.Objects.hash(icon, itemId, mode, label, hint);
    }
  }

  private final List<Choice> choices = new ArrayList<>();
  private final List<Button> rows = new ArrayList<>();
  private final Hand hand;
  private TextFieldWidget search;
  private Button previous, next;
  private ModeMenuLayout layout;
  private int page;
  private int pageSize;

  public ModesScreen(Hand hand) {
    super(new net.minecraft.util.text.TranslationTextComponent("screen.wrenchbridge.title"));
    this.hand = hand;
  }

  @Override
  public void tick() {
    super.tick();
    if (minecraft.player == null
        || minecraft.player.getItemInHand(hand).getItem() != WrenchBridge.WRENCH.get()) onClose();
  }

  @Override
  protected void init() {
    String query = search == null ? "" : search.getValue();
    int firstVisible = page * pageSize;
    rows.clear();
    layout = ModeMenuLayout.forScreen(width, height);
    pageSize = layout.rows;
    page = firstVisible / pageSize;
    boolean requestCatalog = choices.isEmpty();
    if (requestCatalog) {
      choices.add(
          new Choice(
              new ItemStack(WrenchBridge.WRENCH.get()),
              "wrenchbridge:universal_wrench",
              "",
              new net.minecraft.util.text.TranslationTextComponent("screen.wrenchbridge.shared"),
              new net.minecraft.util.text.TranslationTextComponent(
                  "screen.wrenchbridge.shared_hint")));
      Set<String> seen = new HashSet<>();
      if (minecraft.player != null) {
        for (int slot = 0; slot < minecraft.player.inventory.getContainerSize(); slot++) {
          if (slot >= 36 && slot != 40) continue;
          ItemStack stack = minecraft.player.inventory.getItem(slot);
          if (!Tools.candidate(stack)
              || stack.getItem() == WrenchBridge.WRENCH.get()
              || !seen.add(Tools.id(stack))) continue;
          choices.add(nativeChoice(stack, Tools.id(stack), "", false));
          for (String mode : NativeModes.choices(stack.getItem(), stack, minecraft.player)) {
            choices.add(nativeChoice(stack, Tools.id(stack), mode, false));
          }
        }
      }
    }
    search =
        new TextFieldWidget(
            font,
            layout.left,
            layout.searchY,
            layout.width,
            20,
            new net.minecraft.util.text.TranslationTextComponent("screen.wrenchbridge.search"));
    search.setMaxLength(100);

    search.setValue(query);
    search.setResponder(
        value -> {
          page = 0;
          updateRows();
        });
    addButton(search);
    for (int i = 0; i < pageSize; i++) {
      final int row = i;
      rows.add(
          addButton(
              new Button(
                  layout.left + 24,
                  layout.rowY(i),
                  layout.width - 24,
                  ModeMenuLayout.ROW_HEIGHT,
                  new net.minecraft.util.text.StringTextComponent(""),
                  button -> choose(row))));
    }
    previous =
        addButton(
            new Button(
                layout.left,
                layout.footerY,
                35,
                20,
                new net.minecraft.util.text.StringTextComponent("<"),
                button -> {
                  page--;
                  updateRows();
                }));
    next =
        addButton(
            new Button(
                layout.left + layout.width - 35,
                layout.footerY,
                35,
                20,
                new net.minecraft.util.text.StringTextComponent(">"),
                button -> {
                  page++;
                  updateRows();
                }));
    addButton(
        new Button(
            width / 2 - 50,
            layout.doneY,
            100,
            20,
            new net.minecraft.util.text.TranslationTextComponent("gui.done"),
            button -> onClose()));
    updateRows();
    setInitialFocus(search);
    if (requestCatalog && minecraft.player != null) dev.wrenchbridge.CatalogPacket.request();
  }

  private static Choice nativeChoice(ItemStack stack, String itemId, String mode, boolean stored) {
    ITextComponent label = NativeModeNames.choice(stack, mode);
    var hint =
        new net.minecraft.util.text.TranslationTextComponent("screen.wrenchbridge.simulate_hint");
    if (mode.isEmpty())
      hint.append("\n")
          .append(
              new net.minecraft.util.text.TranslationTextComponent(
                  "screen.wrenchbridge.remembered_hint"));
    if (stored)
      hint.append("\n")
          .append(
              new net.minecraft.util.text.TranslationTextComponent(
                      "screen.wrenchbridge.storage_hint")
                  .withStyle(TextFormatting.GRAY));
    return new Choice(stack.copy(), itemId, mode, label, hint);
  }

  public void catalog(List<String> entries) {
    Set<String> seen = new HashSet<>();
    for (Choice choice : choices) seen.add(choice.itemId + "|" + choice.mode);
    for (String entry : entries) {
      int separator = entry.indexOf('|');
      if (separator < 1 || !seen.add(entry)) continue;
      var id = net.minecraft.util.ResourceLocation.tryParse(entry.substring(0, separator));
      if (id == null || !net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(id))
        continue;
      ItemStack stack =
          new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id));
      choices.add(nativeChoice(stack, id.toString(), entry.substring(separator + 1), true));
    }
    updateRows();
  }

  private List<Choice> filtered() {
    String query = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT);
    return choices.stream()
        .filter(
            choice ->
                choice.label.getString().toLowerCase(Locale.ROOT).contains(query)
                    || choice.itemId.contains(query)
                    || choice.mode.toLowerCase(Locale.ROOT).contains(query))
        .collect(java.util.stream.Collectors.toList());
  }

  private int pages(int count) {
    return Math.max(1, (count + pageSize - 1) / pageSize);
  }

  private void updateRows() {
    List<Choice> list = filtered();
    page = Math.max(0, Math.min(page, pages(list.size()) - 1));
    for (int row = 0; row < rows.size(); row++) {
      int index = page * pageSize + row;
      Button button = rows.get(row);
      button.visible = index < list.size();
      if (button.visible) {
        Choice choice = list.get(index);
        button.setMessage(choice.label);
      }
    }
    if (previous != null) previous.active = page > 0;
    if (next != null) next.active = page + 1 < pages(list.size());
  }

  private void choose(int row) {
    List<Choice> list = filtered();
    int index = page * pageSize + row;
    if (index >= list.size()) return;
    Choice choice = list.get(index);
    if (minecraft.player != null
        && minecraft.player.getItemInHand(hand).getItem() == WrenchBridge.WRENCH.get())
      dev.wrenchbridge.ModePacket.send(
          new ModePacket(choice.itemId, choice.mode, hand == Hand.OFF_HAND));
    onClose();
  }

  @Override
  public void render(MatrixStack graphics, int mouseX, int mouseY, float partialTick) {
    renderBackground(graphics);
    super.render(graphics, mouseX, mouseY, partialTick);
    drawCenteredString(graphics, font, title, width / 2, height < 220 ? 10 : 16, 0xFFFFFF);
    if (height >= 220)
      drawCenteredString(
          graphics,
          font,
          new net.minecraft.util.text.TranslationTextComponent("screen.wrenchbridge.description"),
          width / 2,
          32,
          0xB5C8D5);
    List<Choice> list = filtered();
    for (int row = 0; row < rows.size(); row++) {
      int index = page * pageSize + row;
      if (index < list.size())
        minecraft
            .getItemRenderer()
            .renderAndDecorateItem(list.get(index).icon, layout.left + 2, layout.rowY(row) + 2);
    }
    drawCenteredString(
        graphics,
        font,
        new net.minecraft.util.text.StringTextComponent((page + 1) + " / " + pages(list.size())),
        width / 2,
        layout.footerY + 6,
        0xFFFFFF);
    for (int row = 0; row < rows.size(); row++)
      if (rows.get(row).visible && rows.get(row).isHovered()) {
        Choice c = list.get(page * pageSize + row);
        renderComponentTooltip(graphics, java.util.Arrays.asList(c.label, c.hint), mouseX, mouseY);
      }
    if (list.isEmpty())
      drawCenteredString(
          graphics,
          font,
          new net.minecraft.util.text.TranslationTextComponent("screen.wrenchbridge.no_results"),
          width / 2,
          layout.listY + 8,
          0xB5C8D5);
    else if (choices.size() == 1)
      drawCenteredString(
          graphics,
          font,
          new net.minecraft.util.text.TranslationTextComponent("screen.wrenchbridge.carry"),
          width / 2,
          layout.listY + 32,
          0xE9BD70);
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }
}
