package dev.wrenchbridge.verification;

import com.mojang.authlib.GameProfile;
import dev.wrenchbridge.StoredTools;
import dev.wrenchbridge.Tools;
import dev.wrenchbridge.WrenchBridge;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.items.IItemHandler;

/** Opt-in review probe. Requires the existing WrenchBridge verification fixture; never ship this class. */
public final class BorrowReturnProbe {
    private BorrowReturnProbe() {}

    public static List<Map<String, Object>> run(ServerLevel level) throws Exception {
        Class<?> fixture = Class.forName("dev.wrenchbridge.verification.VerificationMod");
        Class<?> handlerClass = Class.forName("dev.wrenchbridge.verification.VerificationMod$MenuHandler");
        var constructor = handlerClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Field activeHandler = field(fixture, "menuHandler");
        Object previousHandler = activeHandler.get(null);
        @SuppressWarnings("unchecked")
        Supplier<Item> bagItem = (Supplier<Item>) field(fixture, "MENU_BAG").get(null);
        ServerPlayer player = FakePlayerFactory.get(level,
                new GameProfile(UUID.fromString("a8465f26-1434-4bae-a960-e61a0d4e9c40"), "WBReturnProbe"));
        if (!player.getInventory().isEmpty() || player.containerMenu != player.inventoryMenu)
            throw new IllegalStateException("Review fake player must have an empty inventory and no open menu");
        player.moveTo(0.5, 100, 0.5, 0, 0);
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            for (String scenario : List.of("ordinary-normal", "ordinary-return-denied",
                    "ordinary-return-throws", "ordinary-mutates-throws", "ordinary-after-accept-throws",
                    "ordinary-action-throws", "ordinary-consumed", "menu-return-throws",
                    "menu-mutates-throws", "menu-after-accept-throws")) {
                IItemHandler handler = (IItemHandler) constructor.newInstance();
                activeHandler.set(null, handler);
                player.getInventory().clearContent();
                for (int slot = 0; slot < 36; slot++)
                    player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(WrenchBridge.WRENCH.get()));
                player.getInventory().setItem(8, new ItemStack(bagItem.get()));
                String marker = "WrenchBridge return probe " + UUID.randomUUID();
                ItemStack tool = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("wrenchbridge:mode_wrench")));
                tool.set(DataComponents.CUSTOM_NAME, Component.literal(marker));
                if (!Tools.candidate(tool) || !handler.insertItem(0, tool, false).isEmpty())
                    throw new IllegalStateException("Could not seed one real test wrench");
                String id = Tools.id(tool);
                var menuSource = scenario.startsWith("menu-") ? StoredTools.menuSource(player, id) : null;
                if (scenario.startsWith("menu-") && menuSource == null)
                    throw new IllegalStateException("Menu control source was not found");
                field(handlerClass, "denyReturn").setBoolean(handler, scenario.endsWith("return-denied"));
                field(handlerClass, "throwReturn").setBoolean(handler, scenario.endsWith("return-throws"));
                field(handlerClass, "mutateThenThrow").setBoolean(handler, scenario.endsWith("mutates-throws"));
                field(handlerClass, "throwAfterReturn").setBoolean(handler, scenario.endsWith("after-accept-throws"));
                boolean[] invoked = {false};
                String exception = "";
                try {
                    if (menuSource != null) {
                        menuSource.use(extracted -> {
                            invoked[0] = true;
                            extracted.setDamageValue(extracted.getDamageValue() + 1);
                            if (scenario.endsWith("action-throws")) throw new IllegalStateException("expected action failure");
                            if (scenario.endsWith("consumed")) extracted.setCount(0);
                            return true;
                        }, false);
                    } else {
                        StoredTools.use(player, id, extracted -> {
                            invoked[0] = true;
                            extracted.setDamageValue(extracted.getDamageValue() + 1);
                            if (scenario.endsWith("action-throws")) throw new IllegalStateException("expected action failure");
                            if (scenario.endsWith("consumed")) extracted.setCount(0);
                            return true;
                        }, false);
                    }
                } catch (RuntimeException failure) {
                    exception = failure.getClass().getName() + ": " + failure.getMessage();
                }
                int handlerCount = 0;
                for (int slot = 0; slot < handler.getSlots(); slot++) handlerCount += count(handler.getStackInSlot(slot), marker);
                int inventoryCount = 0;
                for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
                    inventoryCount += count(player.getInventory().getItem(slot), marker);
                var drops = level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(16),
                        entity -> count(entity.getItem(), marker) > 0);
                int droppedCount = drops.stream().mapToInt(entity -> entity.getItem().getCount()).sum();
                List<ItemStack> restored = new ArrayList<>();
                for (int slot = 0; slot < handler.getSlots(); slot++) restored.add(handler.getStackInSlot(slot));
                for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) restored.add(player.getInventory().getItem(slot));
                for (ItemEntity entity : drops) restored.add(entity.getItem());
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("scenario", scenario);
                result.put("native_action_ran", invoked[0]);
                result.put("exception", exception);
                result.put("handler_count", handlerCount);
                result.put("player_inventory_count", inventoryCount);
                result.put("dropped_count", droppedCount);
                result.put("conserved", handlerCount + inventoryCount + droppedCount == (scenario.endsWith("consumed") ? 0 : 1));
                result.put("post_action_state_preserved", restored.stream().filter(stack -> count(stack, marker) > 0).allMatch(stack -> stack.getDamageValue() == 1));
                results.add(result);
                drops.forEach(ItemEntity::discard);
            }
            return results;
        } finally {
            player.getInventory().clearContent();
            activeHandler.set(null, previousHandler);
        }
    }

    private static int count(ItemStack stack, String marker) {
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        return !stack.isEmpty() && name != null && name.getString().equals(marker) ? stack.getCount() : 0;
    }

    private static Field field(Class<?> owner, String name) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
