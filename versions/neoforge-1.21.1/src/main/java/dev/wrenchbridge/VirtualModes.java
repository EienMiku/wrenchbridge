package dev.wrenchbridge;

import dev.wrenchbridge.core.ClassProbe;
import dev.wrenchbridge.core.NativeModes;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;

/** Only the universal wrench owns simulated profiles. Native items keep their identity and selected mode. */
public final class VirtualModes {
    private static final String KEY = "wrenchbridge_profile";
    private VirtualModes() {}
    private static CompoundTag data(ItemStack wrench) {
        return wrench.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(KEY);
    }
    public static String item(ItemStack wrench) { return data(wrench).getString("item"); }
    public static String mode(ItemStack wrench) { return data(wrench).getString("mode"); }
    public static boolean isNative(ItemStack wrench) { return wrench.getItem() instanceof UniversalWrenchItem && !item(wrench).isEmpty(); }

    public static boolean select(ServerPlayer player, InteractionHand hand, String itemId, String requestedMode) {
        ItemStack wrench = player.getItemInHand(hand);
        if (!(wrench.getItem() instanceof UniversalWrenchItem) || player.isSpectator()) return false;
        CompoundTag root = wrench.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag selected = root.getCompound(KEY);
        if (itemId.equals("wrenchbridge:universal_wrench") && requestedMode.isEmpty()) {
            selected.remove("item"); selected.remove("mode");
        } else {
            ItemStack source = StoredTools.peek(player, itemId);
            if (source.isEmpty()) return false;
            List<String> choices = NativeModes.choices(source.getItem(), source, player);
            String choice = requestedMode;
            if (choice.isEmpty() && !choices.isEmpty()) {
                choice = selected.getCompound("remembered").getString(itemId);
                if (!choices.contains(choice)) choice = NativeModes.current(source.getItem(), source);
            }
            if (!choice.isEmpty() && !choices.contains(choice)) return false;
            selected.putString("item", itemId); selected.putString("mode", choice);
            CompoundTag remembered = selected.getCompound("remembered");
            remembered.putString(itemId, choice); selected.put("remembered", remembered);
        }
        root.put(KEY, selected);
        wrench.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        player.getInventory().setChanged(); player.inventoryMenu.broadcastChanges(); player.containerMenu.broadcastChanges();
        return true;
    }

    public static InteractionResult useOn(ServerPlayer player, InteractionHand hand, BlockHitResult hit) {
        try (var feedback = WrenchContext.serverAction(player)) { return useNativeOn(player, hand, hit); }
    }

    private static InteractionResult useNativeOn(ServerPlayer player, InteractionHand hand, BlockHitResult hit) {
        ItemStack wrench = player.getItemInHand(hand);
        String id = item(wrench), selectedMode = mode(wrench);
        if (id.equals("ae2:network_tool") && !player.isShiftKeyDown()) return openNativeMenu(player, hand, hit);
        try {
            return StoredTools.use(player, id, source -> {
                Item originalItem = source.getItem();
                boolean pneumatic = ClassProbe.is(originalItem, "me.desht.pneumaticcraft.common.item.PneumaticWrenchItem");
                if (pneumatic && ((Number) originalItem.getClass().getMethod("getPressure", ItemStack.class)
                        .invoke(originalItem, source)).floatValue() <= 0.1F) return InteractionResult.FAIL;
                String originalMode = NativeModes.current(originalItem, source);
                boolean changeMode = !selectedMode.isEmpty() && !selectedMode.equals(originalMode);
                try {
                    if (changeMode && !NativeModes.select(originalItem, source, player, selectedMode)) return InteractionResult.FAIL;
                    var state = player.serverLevel().getBlockState(hit.getBlockPos());
                    Targets.Kind target = Targets.resolve(state,
                            player.serverLevel().getBlockEntity(hit.getBlockPos()));
                    // Validate a real source before preflight, including config modes that can consume costs.
                    // Only Create's unoverridden default dismantle callback supplies the check itself.
                    if (player.isShiftKeyDown() && !(Tools.active(source) && BridgeEvents.nativeCreateBreakCheck(target, state))
                            && !BridgeEvents.breakAllowed(player, player.serverLevel(), hit.getBlockPos())) return InteractionResult.FAIL;
                    // Match the physical tool's shared-dismantle routing before a native callback can switch modes
                    // or rotate the target. A pneumatic tool on its own machine must still run its air-cost path.
                    if (player.isShiftKeyDown() && Tools.active(source) && target != Targets.Kind.UNKNOWN
                            && !(pneumatic && target == Targets.Kind.PNEUMATIC))
                        return BridgeEvents.dispatch(player, hand, hit, source);
                    UseOnContext context = context(player, hand, hit, source);
                    InteractionResult result = source.onItemUseFirst(context);
                    if (result != InteractionResult.PASS) return result;
                    result = originalItem.useOn(context);
                    if (result != InteractionResult.PASS) return result;
                    if (pneumatic && Targets.resolve(player.serverLevel().getBlockState(hit.getBlockPos()),
                            player.serverLevel().getBlockEntity(hit.getBlockPos())) == Targets.Kind.PNEUMATIC)
                        return InteractionResult.PASS;
                    if (player.isShiftKeyDown() && Tools.active(source)) return BridgeEvents.dispatch(player, hand, hit, source);
                    if (ClassProbe.is(originalItem, "dev.dubhe.anvilcraft.item.AnvilHammerItem")) {
                        ClassProbe.compatibleMethod(originalItem.getClass(), "useBlock", player, hit.getBlockPos(), player.serverLevel(), source,
                                hand, hit).invoke(null, player, hit.getBlockPos(), player.serverLevel(), source, hand, hit);
                        return InteractionResult.SUCCESS;
                    }
                    // Configuration modes that pass must not fall through to another mod's static wrench-tag handler.
                    if (!Tools.active(source)) return InteractionResult.PASS;
                    return BridgeEvents.nativeBlockUse(player, hand, hit, source);
                } finally {
                    try {
                        if (changeMode && !source.isEmpty()) NativeModes.select(originalItem, source, player, originalMode);
                    } finally {
                        // A native tool may attempt to replace its held form. That is outside the simulation contract.
                        if (player.getItemInHand(hand) != wrench) {
                            player.setItemInHand(hand, wrench);
                            WrenchBridge.LOG.warn("Blocked a native held-item replacement during {} simulation", id);
                        }
                    }
                }
            }, InteractionResult.FAIL);
        } catch (ReflectiveOperationException | RuntimeException error) {
            WrenchBridge.LOG.warn("Native simulation rejected {} / {}", id, selectedMode, error);
            return InteractionResult.FAIL;
        }
    }

    public static boolean hasNativeMenu(ItemStack wrench) {
        ResourceLocation id = ResourceLocation.tryParse(item(wrench));
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return false;
        Item tool = BuiltInRegistries.ITEM.get(id);
        return ClassProbe.is(tool, "dev.dubhe.anvilcraft.item.AnvilHammerItem")
                || ClassProbe.is(tool, "appeng.items.tools.NetworkToolItem");
    }

    public static InteractionResult openNativeMenu(ServerPlayer player, InteractionHand hand) {
        try (var feedback = WrenchContext.serverAction(player)) { return openNativeMenu(player, hand, null); }
    }

    private static InteractionResult openNativeMenu(ServerPlayer player, InteractionHand hand, BlockHitResult hit) {
        String id = item(player.getItemInHand(hand));
        ItemStack source = StoredTools.peek(player, id);
        if (source.isEmpty()) return InteractionResult.FAIL;
        int slot = -1;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
            if (player.getInventory().getItem(i) == source) { slot = i; break; }
        if (slot < 0) {
            if (ClassProbe.is(source.getItem(), "appeng.items.tools.NetworkToolItem")) {
                try {
                    StoredTools.MenuSource stored = StoredTools.menuSource(player, id);
                    if (stored == null) return InteractionResult.FAIL;
                    java.util.UUID token = ContainerMenus.bind(stored);
                    return stored.use(tool -> NativeMenuContext.runContainer(player, hand, tool, token,
                            () -> openNetworkMenu(player, hand, hit, tool)), InteractionResult.FAIL);
                } catch (RuntimeException error) {
                    ContainerMenus.forget(player);
                    WrenchBridge.LOG.warn("Stored network tool menu rejected", error);
                    return InteractionResult.FAIL;
                }
            }
            player.displayClientMessage(Component.translatable("message.wrenchbridge.menu_source_inventory"), true);
            return InteractionResult.FAIL;
        }
        try {
            if (ClassProbe.is(source.getItem(), "dev.dubhe.anvilcraft.item.AnvilHammerItem")) {
                Class.forName("dev.dubhe.anvilcraft.item.AnvilHammerItem")
                        .getMethod("openPortableAnvil", net.minecraft.world.entity.player.Player.class, int.class).invoke(null, player, slot);
                return InteractionResult.SUCCESS;
            }
            if (ClassProbe.is(source.getItem(), "appeng.items.tools.NetworkToolItem")) {
                return NativeMenuContext.run(player, hand, slot, source, () -> openNetworkMenu(player, hand, hit, source));
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            WrenchBridge.LOG.warn("Native menu adapter rejected {}", id, error);
        }
        return InteractionResult.FAIL;
    }

    private static InteractionResult openNetworkMenu(ServerPlayer player, InteractionHand hand, BlockHitResult hit, ItemStack source) {
        if (hit == null) return source.getItem().use(player.serverLevel(), player, hand).getResult();
        InteractionResult result = source.onItemUseFirst(context(player, hand, hit, source));
        if (result != InteractionResult.PASS) return result;
        var state = player.serverLevel().getBlockState(hit.getBlockPos());
        var blockResult = state.useItemOn(source, player.serverLevel(), player, hand, hit);
        return blockResult == net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                ? state.useWithoutItem(player.serverLevel(), player, hit) : blockResult.result();
    }

    public static UseOnContext context(ServerPlayer player, InteractionHand hand, BlockHitResult hit, ItemStack source) {
        return new UseOnContext(player, hand, hit) {
            @Override public ItemStack getItemInHand() { return source; }
        };
    }
}
