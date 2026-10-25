package dev.wrenchbridge;

import dev.wrenchbridge.core.ClassProbe;
import java.lang.reflect.Method;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class BridgeEvents {
    private static final ThreadLocal<Boolean> REENTERING = ThreadLocal.withInitial(() -> false);
    private static final ClassValue<Boolean> CREATE_BREAK_CHECK = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("onSneakWrenched", BlockState.class, UseOnContext.class)
                        .getDeclaringClass().getName().equals("com.simibubi.create.content.equipment.wrench.IWrenchable");
            } catch (NoSuchMethodException | LinkageError | RuntimeException error) { return false; }
        }
    };
    private BridgeEvents() {}
    public static boolean isDispatching() { return REENTERING.get(); }

    public static boolean ownsPneumaticClick(PlayerInteractEvent.RightClickBlock event) {
        if (!SharingControl.allows(event.getEntity(), event.getItemStack())) return false;
        if (!event.getEntity().isShiftKeyDown()) return false;
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (Targets.resolve(state, event.getLevel().getBlockEntity(event.getPos())) != Targets.Kind.PNEUMATIC) return false;
        return VirtualModes.isNative(event.getItemStack()) || Tools.active(event.getItemStack())
                && !nativeScope(event.getItemStack(), state);
    }

    public static boolean ownsSupplementariesClick(PlayerInteractEvent.RightClickBlock event) {
        if (!SharingControl.allows(event.getEntity(), event.getItemStack())) return false;
        if (!event.getEntity().isShiftKeyDown()
                || !ClassProbe.is(event.getItemStack().getItem(), "net.mehvahdjukaar.supplementaries.common.items.WrenchItem")) return false;
        BlockState state = event.getLevel().getBlockState(event.getPos());
        return Tools.active(event.getItemStack()) && !nativeScope(event.getItemStack(), state)
                && Targets.resolve(state, event.getLevel().getBlockEntity(event.getPos())) != Targets.Kind.UNKNOWN;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void guardMode(PlayerInteractEvent.RightClickBlock event) {
        if (NativeBlockActions.input(event)) return;
        if (event.isCanceled() || REENTERING.get()) return;
        // A passive empty main hand must not let a machine consume an intentional offhand sneak use.
        // Return PASS so Minecraft's normal two-hand loop can try the held universal wrench next.
        ItemStack offhand = event.getEntity().getOffhandItem();
        if (event.getHand() == InteractionHand.MAIN_HAND && event.getItemStack().isEmpty()
                && event.getEntity().isShiftKeyDown() && offhand.getItem() instanceof UniversalWrenchItem
                && (VirtualModes.isNative(offhand) || Targets.resolve(event.getLevel().getBlockState(event.getPos()),
                        event.getLevel().getBlockEntity(event.getPos())) != Targets.Kind.UNKNOWN)) {
            event.setCanceled(true); event.setCancellationResult(InteractionResult.PASS); return;
        }
        if (!SharingControl.allows(event.getEntity(), event.getItemStack())) return;
        if (VirtualModes.isNative(event.getItemStack())) {
            if (interactionDenied(event) || !event.getLevel().isClientSide && denied((ServerPlayer) event.getEntity(), event)) {
                event.setCanceled(true); event.setCancellationResult(InteractionResult.FAIL);
            }
            return;
        }
        if (!event.getEntity().isShiftKeyDown()
                || !Tools.candidate(event.getItemStack())) return;
        BlockState state = event.getLevel().getBlockState(event.getPos());
        Targets.Kind target = Targets.resolve(state, event.getLevel().getBlockEntity(event.getPos()));
        if (target == Targets.Kind.UNKNOWN
                || !event.getItemStack().is(WrenchBridge.WRENCH.get()) && nativeOwns(event, state)) return;
        // Native tag listeners may run before our adapter. Check intent and protection before those listeners.
        boolean denied = !Tools.active(event.getItemStack()) || interactionDenied(event);
        if (!denied && !event.getLevel().isClientSide && event.getEntity() instanceof ServerPlayer player) {
            ServerLevel level = player.serverLevel();
            denied = player.isSpectator() || !player.mayBuild() || !level.mayInteract(player, event.getPos())
                    || !player.mayUseItemAt(event.getPos(), event.getFace(), event.getItemStack())
                    || !nativeCreateBreakCheck(target, state) && !breakAllowed(player, level, event.getPos());
        }
        if (denied) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    private static boolean denied(ServerPlayer player, PlayerInteractEvent.RightClickBlock event) {
        return player.isSpectator() || !player.mayBuild() || !player.serverLevel().mayInteract(player, event.getPos())
                || !player.mayUseItemAt(event.getPos(), event.getFace(), event.getItemStack());
    }

    private static boolean interactionDenied(PlayerInteractEvent.RightClickBlock event) {
        return event.getUseBlock() == net.neoforged.neoforge.common.util.TriState.FALSE
                || event.getUseItem() == net.neoforged.neoforge.common.util.TriState.FALSE
                && event.getUseBlock() != net.neoforged.neoforge.common.util.TriState.TRUE;
    }
    private static boolean nativeScope(ItemStack tool, BlockState state) {
        if (BuiltInRegistries.ITEM.getKey(tool.getItem()).getNamespace()
                .equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace())) return true;
        if (ClassProbe.is(tool.getItem(), "mekanism.common.item.ItemConfigurator")
                && (ClassProbe.is(state.getBlock(), "mekanism.common.block.BlockMekanism")
                    || state.getBlock().getClass().getName().startsWith("mekanism."))) return true;
        if (ClassProbe.is(tool.getItem(), "org.cyclops.integrateddynamics.item.ItemWrench")
                && (ClassProbe.is(state.getBlock(), "org.cyclops.integrateddynamics.block.BlockCable")
                    || ClassProbe.is(state.getBlock(), "org.cyclops.integrateddynamics.core.block.BlockContainerCabled")
                    || ClassProbe.is(state.getBlock(), "org.cyclops.integrateddynamics.core.block.BlockWithEntityGuiCabled"))) return true;
        return ClassProbe.is(tool.getItem(), "mcjty.lib.api.smartwrench.SmartWrench")
                && ClassProbe.is(state.getBlock(), "mcjty.lib.blocks.BaseBlock");
    }

    private static boolean nativeOwns(PlayerInteractEvent.RightClickBlock event, BlockState state) {
        // These native handlers hardcode MAIN_HAND. Their bridge-scoped adapters accept the actual hand.
        return nativeScope(event.getItemStack(), state) && !(event.getHand() == InteractionHand.OFF_HAND && Tools.active(event.getItemStack())
                && (ClassProbe.is(state.getBlock(), "appeng.block.AEBaseBlock")
                    || ClassProbe.is(state.getBlock(), "mcjty.lib.blocks.BaseBlock")
                    || ClassProbe.is(state.getBlock(), "org.cyclops.integrateddynamics.core.block.BlockWithEntityGuiCabled")));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void rightClick(PlayerInteractEvent.RightClickBlock event) {
        if (NativeBlockActions.input(event)) return;
        if (!SharingControl.allows(event.getEntity(), event.getItemStack())) return;
        if (event.isCanceled() || REENTERING.get()) return;
        if (WrenchUseTransaction.hasDeferredCreate(event) && interactionDenied(event)) {
            // A native HIGH listener may have been deferred, including tools in their own scope.
            event.setCanceled(true); event.setCancellationResult(InteractionResult.FAIL); return;
        }
        WrenchUseTransaction.runCreate(event);
        if (event.isCanceled()) return;
        if (VirtualModes.isNative(event.getItemStack())) {
            boolean disabled = interactionDenied(event);
            event.setCanceled(true);
            if (disabled) { event.setCancellationResult(InteractionResult.FAIL); return; }
            if (event.getLevel().isClientSide) { event.setCancellationResult(InteractionResult.SUCCESS); return; }
            try {
                REENTERING.set(true);
                event.setCancellationResult(VirtualModes.useOn((ServerPlayer) event.getEntity(), event.getHand(), event.getHitVec()));
            } finally { REENTERING.remove(); }
            return;
        }
        if (!event.getEntity().isShiftKeyDown() || !Tools.active(event.getItemStack())) return;
        BlockState state = event.getLevel().getBlockState(event.getPos());
        Targets.Kind target = Targets.resolve(state, event.getLevel().getBlockEntity(event.getPos()));
        if (target == Targets.Kind.UNKNOWN) return; // Unknown mods can still use our conventional wrench tags natively.
        if (!event.getItemStack().is(WrenchBridge.WRENCH.get())
                && nativeOwns(event, state)) return;
        if (interactionDenied(event)) { event.setCanceled(true); event.setCancellationResult(InteractionResult.FAIL); return; }
        if (event.getLevel().isClientSide) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        ServerPlayer player = (ServerPlayer) event.getEntity();
        ServerLevel level = player.serverLevel();
        if (player.isSpectator() || !player.mayBuild() || !level.mayInteract(player, event.getPos())
                || !player.mayUseItemAt(event.getPos(), event.getFace(), event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        // One selected, known protocol per click. Denials and exceptions never trigger a second removal strategy.
        InteractionResult result;
        try (var feedback = WrenchContext.serverAction(player)) {
            REENTERING.set(true);
            result = dispatch(player, event.getHand(), event.getHitVec(), event.getItemStack());
        } catch (ReflectiveOperationException | RuntimeException error) {
            WrenchBridge.LOG.warn("Could not apply {} wrench protocol at {}", target, event.getPos(), error);
            player.displayClientMessage(Component.translatable("message.wrenchbridge.adapter_failed", target.name()), true);
            result = InteractionResult.FAIL;
        } finally { REENTERING.remove(); }
        event.setCanceled(true);
        event.setCancellationResult(result);
    }

    static InteractionResult dispatch(ServerPlayer player, InteractionHand hand, BlockHitResult hit, ItemStack source) throws ReflectiveOperationException {
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(hit.getBlockPos());
        Targets.Kind kind = Targets.resolve(state, level.getBlockEntity(hit.getBlockPos()));
        if (!nativeCreateBreakCheck(kind, state) && !breakAllowed(player, level, hit.getBlockPos())) return InteractionResult.FAIL;
        return switch (kind) {
            case ANVIL -> anvil(player, level, hit.getBlockPos(), source);
            case CREATE -> create(player, level, hand, hit, source);
            case PNEUMATIC -> pneumatic(player, level, hand, hit);
            case PRETTY -> prettyProxy().getItem().useOn(VirtualModes.context(player, hand, hit, prettyProxy()));
            default -> pipeline(player, level, hand, hit, new ItemStack(WrenchBridge.WRENCH.get()));
        };
    }

    static InteractionResult nativeBlockUse(ServerPlayer player, InteractionHand hand, BlockHitResult hit, ItemStack source) throws ReflectiveOperationException {
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(hit.getBlockPos());
        if (ClassProbe.is(state.getBlock(), "com.simibubi.create.content.equipment.wrench.IWrenchable")) {
            // Create normally handles other mods' wrench tags in RightClickBlock. An explicit simulated
            // wrench reaches the same public entry without replaying a click or changing the held item.
            Method method = state.getBlock().getClass().getMethod("onWrenched", BlockState.class, UseOnContext.class);
            return (InteractionResult) method.invoke(state.getBlock(), state, VirtualModes.context(player, hand, hit, source));
        }
        if (ClassProbe.is(level.getBlockEntity(hit.getBlockPos()), "appeng.blockentity.AEBaseBlockEntity")) {
            Class<?> hook = Class.forName("appeng.hooks.WrenchHook");
            return WrenchContext.runServer(player, hand, player.getItemInHand(hand), source, () -> {
                try { return (InteractionResult) ClassProbe.compatibleMethod(hook, "onPlayerUseBlock", player, level, hand, hit)
                        .invoke(null, player, level, hand, hit); }
                catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
            });
        }
        return WrenchContext.runServer(player, hand, player.getItemInHand(hand), source, () -> {
            var result = state.useItemOn(source, level, player, hand, hit);
            if (result == net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION)
                return state.useWithoutItem(level, player, hit);
            return result.result();
        });
    }

    private static InteractionResult anvil(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack tool)
            throws ReflectiveOperationException {
        Class<?> hammer = Class.forName("dev.dubhe.anvilcraft.item.AnvilHammerItem");
        Method method = ClassProbe.compatibleMethod(hammer, "breakBlock", player, pos, level, tool);
        method.invoke(null, player, pos, level, tool);
        // The bridge already predicts the click's swing. The hammer's own packet path has no
        // vanilla use-result swing, so do not echo an extra animation back to the actor.
        return InteractionResult.CONSUME;
    }

    private static InteractionResult create(ServerPlayer player, ServerLevel level, InteractionHand hand, BlockHitResult hit, ItemStack source)
            throws ReflectiveOperationException {
        Object block = level.getBlockState(hit.getBlockPos()).getBlock();
        Method method = block.getClass().getMethod("onSneakWrenched", BlockState.class, UseOnContext.class);
        return (InteractionResult) method.invoke(block, level.getBlockState(hit.getBlockPos()),
                VirtualModes.context(player, hand, hit, source));
    }

    private static InteractionResult pneumatic(ServerPlayer player, ServerLevel level, InteractionHand hand, BlockHitResult hit)
            throws ReflectiveOperationException {
        Object block = level.getBlockState(hit.getBlockPos()).getBlock();
        Method method = block.getClass().getMethod("onWrenched", Level.class, Player.class, BlockPos.class,
                net.minecraft.core.Direction.class, InteractionHand.class);
        boolean used = (Boolean) method.invoke(block, level, player, hit.getBlockPos(), hit.getDirection(),
                hand);
        if (used) {
            // The native wrench normally supplies this feedback around the public block protocol.
            // Shared dismantling uses that protocol directly, so reuse the same native sound sender.
            var nativeWrench = BuiltInRegistries.ITEM.get(ResourceLocation.parse("pneumaticcraft:pneumatic_wrench"));
            try {
                ClassProbe.compatibleMethod(nativeWrench.getClass(), "playWrenchSound", level, hit.getBlockPos())
                        .invoke(nativeWrench, level, hit.getBlockPos());
            } catch (ReflectiveOperationException | RuntimeException error) {
                WrenchBridge.LOG.debug("Native pneumatic feedback was unavailable", error);
            }
        }
        return used ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    private static ItemStack prettyProxy() {
        var id = ResourceLocation.parse("prettypipes:wrench");
        return BuiltInRegistries.ITEM.containsKey(id) ? new ItemStack(BuiltInRegistries.ITEM.get(id)) : ItemStack.EMPTY;
    }

    private static InteractionResult pipeline(ServerPlayer player, ServerLevel level, InteractionHand hand, BlockHitResult hit, ItemStack proxy) {
        if (proxy.isEmpty()) return InteractionResult.FAIL;
        try {
            return WrenchContext.runServer(player, hand, player.getItemInHand(hand), proxy, () -> {
                var event = new PlayerInteractEvent.RightClickBlock(player, hand, hit.getBlockPos(), hit);
                NeoForge.EVENT_BUS.post(event);
                if (event.isCanceled()) return event.getCancellationResult();
                if (interactionDenied(event)) return InteractionResult.FAIL;
                UseOnContext context = VirtualModes.context(player, hand, hit, proxy);
                if (event.getUseItem() != net.neoforged.neoforge.common.util.TriState.FALSE) {
                    InteractionResult first = proxy.onItemUseFirst(context);
                    if (first != InteractionResult.PASS) return first;
                }
                var state = level.getBlockState(hit.getBlockPos());
                var result = state.useItemOn(proxy, level, player, hand, hit);
                if (result == net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION)
                    return state.useWithoutItem(level, player, hit);
                return result.result();
            });
        } finally {
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
    }

    static boolean breakAllowed(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return WrenchUseTransaction.permission(player, level, pos, () ->
                !NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), player)).isCanceled());
    }

    /** Only the native default implementation is known to post its own cancellable BreakEvent. */
    static boolean nativeCreateBreakCheck(Targets.Kind target, BlockState state) {
        return target == Targets.Kind.CREATE && CREATE_BREAK_CHECK.get(state.getBlock().getClass());
    }

    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wrenchbridge")
                .then(Commands.literal("diagnose").executes(ctx -> {
                    int tools = 0;
                    for (var item : BuiltInRegistries.ITEM) if (Tools.candidate(new ItemStack(item))) tools++;
                    final int count = tools;
                    ctx.getSource().sendSuccess(() -> Component.translatable("message.wrenchbridge.diagnose", count), false);
                    return tools;
                })));
    }
}
