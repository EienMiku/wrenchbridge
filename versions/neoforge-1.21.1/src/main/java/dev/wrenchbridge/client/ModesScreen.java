package dev.wrenchbridge.client;

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
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ModesScreen extends Screen {
    private record Choice(ItemStack icon, String itemId, String mode, Component label, Component hint) {}
    private final List<Choice> choices = new ArrayList<>();
    private final List<Button> rows = new ArrayList<>();
    private final InteractionHand hand;
    private EditBox search;
    private Button previous, next;
    private ModeMenuLayout layout;
    private int page;
    private int pageSize;

    public ModesScreen(InteractionHand hand) {
        super(Component.translatable("screen.wrenchbridge.title"));
        this.hand = hand;
    }

    @Override public void tick() {
        super.tick();
        if (minecraft.player == null || !minecraft.player.getItemInHand(hand).is(WrenchBridge.WRENCH.get())) onClose();
    }

    @Override protected void init() {
        String query = search == null ? "" : search.getValue();
        int firstVisible = page * pageSize;
        rows.clear();
        layout = ModeMenuLayout.forScreen(width, height);
        pageSize = layout.rows;
        page = firstVisible / pageSize;
        boolean requestCatalog = choices.isEmpty();
        if (requestCatalog) {
            choices.add(new Choice(new ItemStack(WrenchBridge.WRENCH.get()), "wrenchbridge:universal_wrench", "",
                    Component.translatable("screen.wrenchbridge.shared"),
                    Component.translatable("screen.wrenchbridge.shared_hint")));
            Set<String> seen = new HashSet<>();
            if (minecraft.player != null) {
                for (int slot = 0; slot < minecraft.player.getInventory().getContainerSize(); slot++) {
                    if (slot >= 36 && slot != 40) continue;
                    ItemStack stack = minecraft.player.getInventory().getItem(slot);
                    if (!Tools.candidate(stack) || stack.is(WrenchBridge.WRENCH.get()) || !seen.add(Tools.id(stack))) continue;
                    choices.add(nativeChoice(stack, Tools.id(stack), "", false));
                    for (String mode : NativeModes.choices(stack.getItem(), stack, minecraft.player)) {
                        choices.add(nativeChoice(stack, Tools.id(stack), mode, false));
                    }
                }
            }
        }
        search = new EditBox(font, layout.left, layout.searchY, layout.width, 20,
                Component.translatable("screen.wrenchbridge.search"));
        search.setMaxLength(100);
        search.setHint(Component.translatable("screen.wrenchbridge.search"));
        search.setValue(query);
        search.setResponder(value -> { page = 0; updateRows(); });
        addRenderableWidget(search);
        for (int i = 0; i < pageSize; i++) {
            final int row = i;
            rows.add(addRenderableWidget(Button.builder(Component.empty(), button -> choose(row))
                    .bounds(layout.left + 24, layout.rowY(i), layout.width - 24, ModeMenuLayout.ROW_HEIGHT).build()));
        }
        previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> { page--; updateRows(); })
                .bounds(layout.left, layout.footerY, 35, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.wrenchbridge.previous"))).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), button -> { page++; updateRows(); })
                .bounds(layout.left + layout.width - 35, layout.footerY, 35, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.wrenchbridge.next"))).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width / 2 + 4, layout.doneY, 100, 20).build());
        updateRows();
        addRenderableWidget(Button.builder(Component.translatable("screen.wrenchbridge.settings"),
                button -> minecraft.setScreen(new SharingSettingsScreen(this)))
                .bounds(width / 2 - 104, layout.doneY, 100, 20).build());
        setInitialFocus(search);
        if (requestCatalog && minecraft.player != null)
            PacketDistributor.sendToServer(new dev.wrenchbridge.CatalogPacket.Request());
    }

    private static Choice nativeChoice(ItemStack stack, String itemId, String mode, boolean stored) {
        Component label = NativeModeNames.choice(stack, mode);
        var hint = Component.translatable("screen.wrenchbridge.simulate_hint");
        if (mode.isEmpty()) hint.append("\n").append(Component.translatable("screen.wrenchbridge.remembered_hint"));
        if (stored) hint.append("\n").append(Component.translatable("screen.wrenchbridge.storage_hint")
                .withStyle(ChatFormatting.GRAY));
        return new Choice(stack.copy(), itemId, mode, label, hint);
    }

    public void catalog(List<String> entries) {
        Set<String> seen = new HashSet<>();
        for (Choice choice : choices) seen.add(choice.itemId + "|" + choice.mode);
        for (String entry : entries) {
            int separator = entry.indexOf('|');
            if (separator < 1 || !seen.add(entry)) continue;
            var id = net.minecraft.resources.ResourceLocation.tryParse(entry.substring(0, separator));
            if (id == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)) continue;
            ItemStack stack = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id));
            choices.add(nativeChoice(stack, id.toString(), entry.substring(separator + 1), true));
        }
        updateRows();
    }

    private List<Choice> filtered() {
        String query = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT);
        return choices.stream().filter(choice -> choice.label.getString().toLowerCase(Locale.ROOT).contains(query)
                || choice.itemId.contains(query) || choice.mode.toLowerCase(Locale.ROOT).contains(query)).toList();
    }

    private int pages(int count) { return Math.max(1, (count + pageSize - 1) / pageSize); }

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
                button.setTooltip(Tooltip.create(choice.label.copy().append("\n").append(choice.hint)));
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
        if (minecraft.player != null && minecraft.player.getItemInHand(hand).is(WrenchBridge.WRENCH.get()))
            PacketDistributor.sendToServer(new ModePacket(choice.itemId, choice.mode, hand == InteractionHand.OFF_HAND));
        onClose();
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Screen.render applies the background blur itself; draw our text afterwards, exactly once.
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height < 220 ? 10 : 16, 0xFFFFFF);
        if (height >= 220) graphics.drawCenteredString(font, Component.translatable("screen.wrenchbridge.description"),
                width / 2, 32, 0xB5C8D5);
        List<Choice> list = filtered();
        for (int row = 0; row < rows.size(); row++) {
            int index = page * pageSize + row;
            if (index < list.size()) graphics.renderItem(list.get(index).icon, layout.left + 2, layout.rowY(row) + 2);
        }
        graphics.drawCenteredString(font, Component.literal((page + 1) + " / " + pages(list.size())),
                width / 2, layout.footerY + 6, 0xFFFFFF);
        if (list.isEmpty()) graphics.drawCenteredString(font, Component.translatable("screen.wrenchbridge.no_results"),
                width / 2, layout.listY + 8, 0xB5C8D5);
        else if (choices.size() == 1) graphics.drawCenteredString(font, Component.translatable("screen.wrenchbridge.carry"),
                width / 2, layout.listY + 32, 0xE9BD70);
    }

    @Override public boolean isPauseScreen() { return false; }
}
