package dev.wrenchbridge;

import dev.wrenchbridge.core.ClassProbe;
import java.lang.reflect.Method;
import net.minecraft.command.Commands;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResultType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.world.World;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;

public final class BridgeEvents {
    private static final ThreadLocal<Boolean> REENTERING = ThreadLocal.withInitial(() -> false);
    private BridgeEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void guardMode(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || REENTERING.get() || !event.getEntity().isShiftKeyDown()
                || !Tools.candidate(event.getItemStack())) return;
        BlockState state = event.getWorld().getBlockState(event.getPos());
        if (Targets.resolve(state, event.getWorld().getBlockEntity(event.getPos())) == Targets.Kind.UNKNOWN
                || event.getItemStack().getItem() != WrenchBridge.WRENCH.get() && nativeScope(event.getItemStack(), state)) return;
        // Native tag listeners may run before our adapter. Check intent and protection before those listeners.
        boolean denied = !Tools.active(event.getItemStack()) || interactionDenied(event);
        if (!denied && !event.getWorld().isClientSide) {
            ServerPlayerEntity player = (ServerPlayerEntity) event.getEntity();
            ServerWorld level = player.getLevel();
            denied = player.isSpectator() || !player.mayBuild() || !level.mayInteract(player, event.getPos())
                    || !player.mayUseItemAt(event.getPos(), event.getFace(), event.getItemStack())
                    || !breakAllowed(player, level, event.getPos());
        }
        if (denied) {
            event.setCanceled(true);
            event.setCancellationResult(ActionResultType.FAIL);
        }
    }

    private static boolean interactionDenied(PlayerInteractEvent.RightClickBlock event) {
        return event.getUseBlock() == net.minecraftforge.eventbus.api.Event.Result.DENY || event.getUseItem() == net.minecraftforge.eventbus.api.Event.Result.DENY;
    }
    private static boolean nativeScope(ItemStack tool, BlockState state) {
        if (ForgeRegistries.ITEMS.getKey(tool.getItem()).getNamespace()
                .equals(ForgeRegistries.BLOCKS.getKey(state.getBlock()).getNamespace())) return true;
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
        if (event.isCanceled() || REENTERING.get() || event.getHand() != Hand.MAIN_HAND
                || !event.getEntity().isShiftKeyDown() || !Tools.active(event.getItemStack())) return;
        BlockState state = event.getWorld().getBlockState(event.getPos());
        Targets.Kind target = Targets.resolve(state, event.getWorld().getBlockEntity(event.getPos()));
        if (target == Targets.Kind.UNKNOWN) return; // Unknown mods can still use our conventional wrench tags natively.
        if (event.getItemStack().getItem() != WrenchBridge.WRENCH.get()
                && nativeScope(event.getItemStack(), state)) return;
        if (interactionDenied(event)) { event.setCanceled(true); event.setCancellationResult(ActionResultType.FAIL); return; }
        if (event.getWorld().isClientSide) {
            event.setCanceled(true);
            event.setCancellationResult(ActionResultType.SUCCESS);
            return;
        }
        ServerPlayerEntity player = (ServerPlayerEntity) event.getEntity();
        ServerWorld level = player.getLevel();
        if (player.isSpectator() || !player.mayBuild() || !level.mayInteract(player, event.getPos())
                || !player.mayUseItemAt(event.getPos(), event.getFace(), event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(ActionResultType.FAIL);
            return;
        }
        // One selected, known protocol per click. Denials and exceptions never trigger a second removal strategy.
        ActionResultType result;
        try {
            REENTERING.set(true);
            switch (target) {
                case ANVIL: result = anvil(player, level, event.getPos(), event.getItemStack()); break;
                case CREATE: result = create(player, level, event.getHitVec()); break;
                case PNEUMATIC: result = pneumatic(player, level, event.getHitVec()); break;
                case PRETTY: result = pipeline(player, level, event.getHitVec(), prettyProxy()); break;
                default: result = pipeline(player, level, event.getHitVec(), new ItemStack(WrenchBridge.WRENCH.get()));
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            WrenchBridge.LOG.warn("Could not apply {} wrench protocol at {}", target, event.getPos(), error);
            player.displayClientMessage(new TranslationTextComponent("message.wrenchbridge.adapter_failed", target.name()), true);
            result = ActionResultType.FAIL;
        } finally { REENTERING.remove(); }
        event.setCanceled(true);
        event.setCancellationResult(result);
    }

    private static ActionResultType anvil(ServerPlayerEntity player, ServerWorld level, BlockPos pos, ItemStack tool)
            throws ReflectiveOperationException {
        Class<?> hammer = Class.forName("dev.dubhe.anvilcraft.item.AnvilHammerItem");
        Method method = ClassProbe.compatibleMethod(hammer, "breakBlock", player, pos, level, tool);
        method.invoke(null, player, pos, level, tool);
        return ActionResultType.SUCCESS;
    }

    private static ActionResultType create(ServerPlayerEntity player, ServerWorld level, BlockRayTraceResult hit)
            throws ReflectiveOperationException {
        Object block = level.getBlockState(hit.getBlockPos()).getBlock();
        Method method = block.getClass().getMethod("onSneakWrenched", BlockState.class, ItemUseContext.class);
        return (ActionResultType) method.invoke(block, level.getBlockState(hit.getBlockPos()),
                new ItemUseContext(player, Hand.MAIN_HAND, hit));
    }

    private static ActionResultType pneumatic(ServerPlayerEntity player, ServerWorld level, BlockRayTraceResult hit)
            throws ReflectiveOperationException {
        Object block = level.getBlockState(hit.getBlockPos()).getBlock();
        Method method = block.getClass().getMethod("onWrenched", World.class, PlayerEntity.class, BlockPos.class,
                net.minecraft.util.Direction.class, Hand.class);
        boolean used = (Boolean) method.invoke(block, level, player, hit.getBlockPos(), hit.getDirection(),
                Hand.MAIN_HAND);
        return used ? ActionResultType.SUCCESS : ActionResultType.PASS;
    }

    private static ItemStack prettyProxy() {
        ResourceLocation id = new ResourceLocation("prettypipes:wrench");
        return ForgeRegistries.ITEMS.containsKey(id) ? new ItemStack(ForgeRegistries.ITEMS.getValue(id)) : ItemStack.EMPTY;
    }

    private static ActionResultType pipeline(ServerPlayerEntity player, ServerWorld level, BlockRayTraceResult hit, ItemStack proxy) {
        if (proxy.isEmpty()) return ActionResultType.FAIL;
        ItemStack original = player.getMainHandItem();
        try {
            player.setItemInHand(Hand.MAIN_HAND, proxy);
            return player.gameMode.useItemOn(player, level, proxy, Hand.MAIN_HAND, hit);
        } finally {
            player.setItemInHand(Hand.MAIN_HAND, original);
            player.inventory.setChanged();
            player.containerMenu.broadcastChanges();
        }
    }

    @SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) StoredTools.forget((ServerPlayerEntity) event.getPlayer());
    }
    private static boolean breakAllowed(ServerPlayerEntity player, ServerWorld level, BlockPos pos) {
        return !MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), player));
    }

    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wrenchbridge")
                .then(Commands.literal("diagnose").executes(ctx -> {
                    int tools = 0;
                    for (net.minecraft.item.Item item : ForgeRegistries.ITEMS) if (Tools.candidate(new ItemStack(item))) tools++;
                    final int count = tools;
                    ctx.getSource().sendSuccess(new TranslationTextComponent("message.wrenchbridge.diagnose", count), false);
                    return tools;
                })));
    }
}
