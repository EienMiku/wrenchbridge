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
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;

public final class BridgeEvents {
    private static final ThreadLocal<Boolean> REENTERING = ThreadLocal.withInitial(() -> false);
    private BridgeEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void guardMode(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || REENTERING.get() || !event.getEntity().isShiftKeyDown()
                || !Tools.candidate(event.getItemStack())) return;
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (Targets.resolve(state, event.getLevel().getBlockEntity(event.getPos())) == Targets.Kind.UNKNOWN
                || !event.getItemStack().is(WrenchBridge.WRENCH.get()) && nativeScope(event.getItemStack(), state)) return;
        // Native tag listeners may run before our adapter. Check intent and protection before those listeners.
        boolean denied = !Tools.active(event.getItemStack()) || interactionDenied(event);
        if (!denied && !event.getLevel().isClientSide && event.getEntity() instanceof ServerPlayer player) {
            ServerLevel level = player.serverLevel();
            denied = player.isSpectator() || !player.mayBuild() || !level.mayInteract(player, event.getPos())
                    || !player.mayUseItemAt(event.getPos(), event.getFace(), event.getItemStack())
                    || !breakAllowed(player, level, event.getPos());
        }
        if (denied) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    private static boolean interactionDenied(PlayerInteractEvent.RightClickBlock event) {
        return event.getUseBlock() == net.minecraftforge.eventbus.api.Event.Result.DENY || event.getUseItem() == net.minecraftforge.eventbus.api.Event.Result.DENY;
    }
    private static boolean nativeScope(ItemStack tool, BlockState state) {
        if (BuiltInRegistries.ITEM.getKey(tool.getItem()).getNamespace()
                .equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace())) return true;
        if (ClassProbe.is(tool.getItem(), "mekanism.common.item.ItemConfigurator")
                && (ClassProbe.is(state.getBlock(), "mekanism.common.block.BlockMekanism")
                    || state.getBlock().getClass().getName().startsWith("mekanism."))) return true;
        if (ClassProbe.is(tool.getItem(), "org.cyclops.integrateddynamics.item.ItemWrench")
                && (ClassProbe.is(state.getBlock(), "org.cyclops.integrateddynamics.block.BlockCable")
                    || ClassProbe.is(state.getBlock(), "org.cyclops.integrateddynamics.core.block.BlockContainerCabled"))) return true;
        return ClassProbe.is(tool.getItem(), "mcjty.lib.api.smartwrench.SmartWrench")
                && ClassProbe.is(state.getBlock(), "mcjty.lib.blocks.BaseBlock");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void rightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || REENTERING.get() || event.getHand() != InteractionHand.MAIN_HAND
                || !event.getEntity().isShiftKeyDown() || !Tools.active(event.getItemStack())) return;
        BlockState state = event.getLevel().getBlockState(event.getPos());
        Targets.Kind target = Targets.resolve(state, event.getLevel().getBlockEntity(event.getPos()));
        if (target == Targets.Kind.UNKNOWN) return; // Unknown mods can still use our conventional wrench tags natively.
        if (!event.getItemStack().is(WrenchBridge.WRENCH.get())
                && nativeScope(event.getItemStack(), state)) return;
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
        try {
            REENTERING.set(true);
            result = switch (target) {
                case ANVIL -> anvil(player, level, event.getPos(), event.getItemStack());
                case CREATE -> create(player, level, event.getHitVec());
                case PNEUMATIC -> pneumatic(player, level, event.getHitVec());
                case PRETTY -> pipeline(player, level, event.getHitVec(), prettyProxy());
                default -> pipeline(player, level, event.getHitVec(), new ItemStack(WrenchBridge.WRENCH.get()));
            };
        } catch (ReflectiveOperationException | RuntimeException error) {
            WrenchBridge.LOG.warn("Could not apply {} wrench protocol at {}", target, event.getPos(), error);
            player.displayClientMessage(Component.translatable("message.wrenchbridge.adapter_failed", target.name()), true);
            result = InteractionResult.FAIL;
        } finally { REENTERING.remove(); }
        event.setCanceled(true);
        event.setCancellationResult(result);
    }

    private static InteractionResult anvil(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack tool)
            throws ReflectiveOperationException {
        Class<?> hammer = Class.forName("dev.dubhe.anvilcraft.item.AnvilHammerItem");
        Method method = ClassProbe.compatibleMethod(hammer, "breakBlock", player, pos, level, tool);
        method.invoke(null, player, pos, level, tool);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult create(ServerPlayer player, ServerLevel level, BlockHitResult hit)
            throws ReflectiveOperationException {
        Object block = level.getBlockState(hit.getBlockPos()).getBlock();
        Method method = block.getClass().getMethod("onSneakWrenched", BlockState.class, UseOnContext.class);
        return (InteractionResult) method.invoke(block, level.getBlockState(hit.getBlockPos()),
                new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    private static InteractionResult pneumatic(ServerPlayer player, ServerLevel level, BlockHitResult hit)
            throws ReflectiveOperationException {
        Object block = level.getBlockState(hit.getBlockPos()).getBlock();
        Method method = block.getClass().getMethod("onWrenched", Level.class, Player.class, BlockPos.class,
                net.minecraft.core.Direction.class, InteractionHand.class);
        boolean used = (Boolean) method.invoke(block, level, player, hit.getBlockPos(), hit.getDirection(),
                InteractionHand.MAIN_HAND);
        return used ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    private static ItemStack prettyProxy() {
        var id = new ResourceLocation("prettypipes:wrench");
        return BuiltInRegistries.ITEM.containsKey(id) ? new ItemStack(BuiltInRegistries.ITEM.get(id)) : ItemStack.EMPTY;
    }

    private static InteractionResult pipeline(ServerPlayer player, ServerLevel level, BlockHitResult hit, ItemStack proxy) {
        if (proxy.isEmpty()) return InteractionResult.FAIL;
        ItemStack original = player.getMainHandItem();
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, proxy);
            return player.gameMode.useItemOn(player, level, proxy, InteractionHand.MAIN_HAND, hit);
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, original);
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
    }

    @SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) StoredTools.forget(player);
    }
    private static boolean breakAllowed(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return !MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), player));
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
