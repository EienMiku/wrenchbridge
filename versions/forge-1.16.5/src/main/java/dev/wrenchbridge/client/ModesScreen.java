package dev.wrenchbridge.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import dev.wrenchbridge.ModePacket;
import dev.wrenchbridge.Tools;
import dev.wrenchbridge.WrenchBridge;
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
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

public final class ModesScreen extends Screen {
    private static final class Choice {
        final ItemStack icon; final String itemId, mode; final ITextComponent label;
        Choice(ItemStack icon, String itemId, String mode, ITextComponent label) {
            this.icon = icon; this.itemId = itemId; this.mode = mode; this.label = label;
        }
    }
    private final List<Choice> choices = new ArrayList<Choice>();
    private final List<Button> rows = new ArrayList<Button>();
    private TextFieldWidget search;
    private int page, pageSize, footer;
    public ModesScreen() { super(new TranslationTextComponent("screen.wrenchbridge.title")); }
    @Override protected void init() {
        choices.clear(); rows.clear(); page = 0;
        pageSize = Math.max(2, Math.min(7, (height - 155) / 25));
        footer = 80 + pageSize * 25 + 6;
        choices.add(new Choice(new ItemStack(WrenchBridge.WRENCH.get()), "wrenchbridge:universal_wrench", "",
                new TranslationTextComponent("screen.wrenchbridge.shared")));
        Set<String> seen = new HashSet<String>();
        if (minecraft.player != null) {
            for (int slot = 0; slot < minecraft.player.inventory.getContainerSize(); slot++) {
                if (slot >= 36 && slot != 40) continue;
                ItemStack stack = minecraft.player.inventory.getItem(slot);
                if (!Tools.candidate(stack) || stack.getItem() == WrenchBridge.WRENCH.get() || !seen.add(Tools.id(stack))) continue;
                choices.add(new Choice(stack.copy(), Tools.id(stack), "", stack.getHoverName()));
                for (String mode : NativeModes.choices(stack.getItem(), stack, minecraft.player))
                    choices.add(new Choice(stack.copy(), Tools.id(stack), mode,
                            new StringTextComponent("  ").append(stack.getHoverName()).append(" · " + mode.replace('_', ' '))));
            }
        }
        int left = width / 2 - 160;
        search = new TextFieldWidget(font, left, 48, 320, 20, new TranslationTextComponent("screen.wrenchbridge.search"));
        search.setMaxLength(100);
        search.setResponder(value -> { page = 0; updateRows(); });
        addButton(search);
        for (int i = 0; i < pageSize; i++) {
            final int row = i;
            rows.add(addButton(new Button(left + 24, 80 + i * 25, 296, 22, new StringTextComponent(""), button -> choose(row))));
        }
        addButton(new Button(left, footer, 35, 20, new StringTextComponent("<"), button -> { page = Math.max(0, page - 1); updateRows(); }));
        addButton(new Button(left + 285, footer, 35, 20, new StringTextComponent(">"), button -> {
            page = Math.min(Math.max(0, (filtered().size() - 1) / pageSize), page + 1); updateRows();
        }));
        addButton(new Button(width / 2 - 50, footer + 30, 100, 20, new TranslationTextComponent("gui.done"), button -> onClose()));
        updateRows(); setInitialFocus(search);
        dev.wrenchbridge.CatalogPacket.request();
    }
    public void catalog(List<String> entries) {
        Set<String> seen = new HashSet<String>();
        for (Choice choice : choices) seen.add(choice.itemId + "|" + choice.mode);
        for (String entry : entries) {
            int separator=entry.indexOf('|');
            if (separator < 1 || !seen.add(entry)) continue;
            net.minecraft.util.ResourceLocation id;
            try { id = new net.minecraft.util.ResourceLocation(entry.substring(0, separator)); } catch (RuntimeException invalid) { continue; }
            if (!net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(id)) continue;
            ItemStack stack=new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id));
            String mode=entry.substring(separator+1);
            ITextComponent label=stack.getHoverName().copy();
            if (!mode.isEmpty()) label=label.copy().append(" · " + mode.replace('_', ' '));
            label=label.copy().append(new TranslationTextComponent("screen.wrenchbridge.storage"));
            choices.add(new Choice(stack,id.toString(),mode,label));
        }
        updateRows();
    }
    private List<Choice> filtered() {
        String query = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT);
        List<Choice> list = new ArrayList<Choice>();
        for (Choice choice : choices)
            if (choice.label.getString().toLowerCase(Locale.ROOT).contains(query) || choice.itemId.contains(query)
                    || choice.mode.toLowerCase(Locale.ROOT).contains(query)) list.add(choice);
        return list;
    }
    private void updateRows() {
        List<Choice> list = filtered();
        for (int row = 0; row < rows.size(); row++) {
            int index = page * pageSize + row;
            rows.get(row).visible = index < list.size();
            if (index < list.size()) rows.get(row).setMessage(list.get(index).label);
        }
    }
    private void choose(int row) {
        List<Choice> list = filtered(); int index = page * pageSize + row;
        if (index >= list.size()) return;
        Choice choice = list.get(index); ModePacket.send(new ModePacket(choice.itemId, choice.mode)); onClose();
    }
    @Override public void tick() { if (search != null) search.tick(); }
    @Override public void render(MatrixStack graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        drawCenteredString(graphics, font, title, width / 2, 16, 0xFFFFFF);
        drawCenteredString(graphics, font, new TranslationTextComponent("screen.wrenchbridge.description"), width / 2, 32, 0xB5C8D5);
        super.render(graphics, mouseX, mouseY, partialTick);
        List<Choice> list = filtered();
        for (int row = 0; row < pageSize; row++) {
            int index = page * pageSize + row;
            if (index < list.size()) minecraft.getItemRenderer().renderAndDecorateItem(list.get(index).icon, width / 2 - 159, 83 + row * 25);
        }
        drawCenteredString(graphics, font, (page + 1) + " / " + Math.max(1, (list.size() + pageSize - 1) / pageSize), width / 2, footer + 6, 0xFFFFFF);
        if (choices.size() == 1) drawCenteredString(graphics, font, new TranslationTextComponent("screen.wrenchbridge.carry"), width / 2, footer - 11, 0xE9BD70);
    }
    @Override public boolean isPauseScreen() { return false; }
}
