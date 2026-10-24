package dev.wrenchbridge.client;

import dev.wrenchbridge.ModePacket;
import dev.wrenchbridge.Tools;
import dev.wrenchbridge.WrenchBridge;
import dev.wrenchbridge.core.NativeModes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public final class ModesScreen extends Screen {
    private record Choice(ItemStack icon, String itemId, String mode, Component label) {}
    private final List<Choice> choices = new ArrayList<>();
    private final List<Button> rows = new ArrayList<>();
    private EditBox search;
    private int page;
    private int pageSize;
    private int footer;
    public ModesScreen() { super(Component.translatable("screen.wrenchbridge.title")); }

    @Override protected void init() {
        choices.clear(); rows.clear(); page = 0;
        pageSize = Math.max(2, Math.min(7, (height - 155) / 25));
        footer = 80 + pageSize * 25 + 6;
        choices.add(new Choice(new ItemStack(WrenchBridge.WRENCH.get()), "wrenchbridge:universal_wrench", "",
                Component.translatable("screen.wrenchbridge.shared")));
        Set<String> seen = new HashSet<>();
        if (minecraft.player != null) {
            for (int slot = 0; slot < minecraft.player.getInventory().getContainerSize(); slot++) {
                if (slot >= 36 && slot != net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND) continue;
                ItemStack stack = minecraft.player.getInventory().getItem(slot);
                if (!Tools.candidate(stack) || stack.is(WrenchBridge.WRENCH.get()) || !seen.add(Tools.id(stack))) continue;
                choices.add(new Choice(stack.copy(), Tools.id(stack), "", stack.getHoverName()));
                for (String mode : NativeModes.choices(stack.getItem(), stack, minecraft.player)) {
                    choices.add(new Choice(stack.copy(), Tools.id(stack), mode,
                            Component.literal("  ").append(stack.getHoverName()).append(" · " + friendly(mode))));
                }
            }
        }
        int left = width / 2 - 160;
        search = new EditBox(font, left, 48, 320, 20, Component.translatable("screen.wrenchbridge.search"));
        search.setMaxLength(100);
        search.setResponder(value -> { page = 0; updateRows(); });
        addRenderableWidget(search);
        for (int i = 0; i < pageSize; i++) {
            final int row = i;
            rows.add(addRenderableWidget(Button.builder(Component.empty(), button -> choose(row))
                    .bounds(left + 24, 80 + i * 25, 296, 22).build()));
        }
        addRenderableWidget(Button.builder(Component.literal("<"), button -> { page = Math.max(0, page - 1); updateRows(); })
                .bounds(left, footer, 35, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> {
            page = Math.min(Math.max(0, (filtered().size() - 1) / pageSize), page + 1); updateRows();
        }).bounds(left + 285, footer, 35, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width / 2 - 50, footer + 30, 100, 20).build());
        updateRows();
        setInitialFocus(search);
        ClientPacketDistributor.sendToServer(new dev.wrenchbridge.CatalogPacket.Request());
    }
    public void catalog(List<String> entries) {
        Set<String> seen = new HashSet<>();
        for (Choice choice : choices) seen.add(choice.itemId + "|" + choice.mode);
        for (String entry : entries) {
            int separator = entry.indexOf('|');
            if (separator < 1 || !seen.add(entry)) continue;
            var id = net.minecraft.resources.Identifier.tryParse(entry.substring(0, separator));
            if (id == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)) continue;
            ItemStack stack = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id).orElseThrow().value());
            String mode = entry.substring(separator + 1);
            Component label = stack.getHoverName().copy();
            if (!mode.isEmpty()) label = label.copy().append(" · " + friendly(mode));
            label = label.copy().append(Component.translatable("screen.wrenchbridge.storage"));
            choices.add(new Choice(stack, id.toString(), mode, label));
        }
        updateRows();
    }
    private static String friendly(String mode) {
        return Component.translatableWithFallback("mode.wrenchbridge." + mode.toLowerCase(Locale.ROOT),
                mode.replace('_', ' ')).getString();
    }
    private List<Choice> filtered() {
        String query = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT);
        return choices.stream().filter(choice -> choice.label.getString().toLowerCase(Locale.ROOT).contains(query)
                || choice.itemId.contains(query) || choice.mode.toLowerCase(Locale.ROOT).contains(query)).toList();
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
        List<Choice> list = filtered();
        int index = page * pageSize + row;
        if (index >= list.size()) return;
        Choice choice = list.get(index);
        ClientPacketDistributor.sendToServer(new ModePacket(choice.itemId, choice.mode));
        onClose();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 16, 0xFFFFFF);
        graphics.centeredText(font, Component.translatable("screen.wrenchbridge.description"), width / 2, 32, 0xB5C8D5);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        List<Choice> list = filtered();
        for (int row = 0; row < pageSize; row++) {
            int index = page * pageSize + row;
            if (index < list.size()) graphics.item(list.get(index).icon, width / 2 - 159, 83 + row * 25);
        }
        graphics.centeredText(font, Component.literal((page + 1) + " / " + Math.max(1, (list.size() + pageSize - 1) / pageSize)),
                width / 2, footer + 6, 0xFFFFFF);
        if (choices.size() == 1) graphics.centeredText(font, Component.translatable("screen.wrenchbridge.carry"), width / 2, footer - 11, 0xE9BD70);
    }
    @Override public boolean isPauseScreen() { return false; }
}
