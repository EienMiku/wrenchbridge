package dev.wrenchbridge;

import dev.wrenchbridge.core.ClassProbe;
import java.lang.reflect.Method;
import net.minecraft.block.BlockState;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class BridgeEvents {
  private static final ThreadLocal<Boolean> REENTERING = ThreadLocal.withInitial(() -> false);

  private BridgeEvents() {}

  public static boolean ownsPneumaticClick(PlayerInteractEvent.RightClickBlock event) {
    if (!SharingControl.allows(event.getPlayer(), event.getItemStack())) return false;
    if (!event.getPlayer().isShiftKeyDown()) return false;
    BlockState state = event.getWorld().getBlockState(event.getPos());
    if (Targets.resolve(state, event.getWorld().getBlockEntity(event.getPos()))
        != Targets.Kind.PNEUMATIC) return false;
    return VirtualModes.isNative(event.getItemStack())
        || Tools.active(event.getItemStack()) && !nativeScope(event.getItemStack(), state);
  }

  public static boolean ownsSupplementariesClick(PlayerInteractEvent.RightClickBlock event) {
    if (!SharingControl.allows(event.getPlayer(), event.getItemStack())) return false;
    if (!event.getPlayer().isShiftKeyDown()
        || !ClassProbe.is(
            event.getItemStack().getItem(),
            "net.mehvahdjukaar.supplementaries.common.items.WrenchItem")) return false;
    BlockState state = event.getWorld().getBlockState(event.getPos());
    return Tools.active(event.getItemStack())
        && !nativeScope(event.getItemStack(), state)
        && Targets.resolve(state, event.getWorld().getBlockEntity(event.getPos()))
            != Targets.Kind.UNKNOWN;
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void guardMode(PlayerInteractEvent.RightClickBlock event) {
    if (event.isCanceled() || REENTERING.get()) return;
    // A passive empty main hand must not let a machine consume an intentional offhand sneak use.
    // Return PASS so Minecraft's normal two-hand loop can try the held universal wrench next.
    ItemStack offhand = event.getPlayer().getOffhandItem();
    if (event.getHand() == Hand.MAIN_HAND
        && event.getItemStack().isEmpty()
        && event.getPlayer().isShiftKeyDown()
        && offhand.getItem() instanceof UniversalWrenchItem
        && (VirtualModes.isNative(offhand)
            || Targets.resolve(
                    event.getWorld().getBlockState(event.getPos()),
                    event.getWorld().getBlockEntity(event.getPos()))
                != Targets.Kind.UNKNOWN)) {
      event.setCanceled(true);
      event.setCancellationResult(ActionResultType.PASS);
      return;
    }
    if (!SharingControl.allows(event.getPlayer(), event.getItemStack())) return;
    if (VirtualModes.isNative(event.getItemStack())) {
      if (interactionDenied(event)
          || !event.getWorld().isClientSide
              && denied((ServerPlayerEntity) event.getPlayer(), event)) {
        event.setCanceled(true);
        event.setCancellationResult(ActionResultType.FAIL);
      }
      return;
    }
    if (!event.getPlayer().isShiftKeyDown() || !Tools.candidate(event.getItemStack())) return;
    BlockState state = event.getWorld().getBlockState(event.getPos());
    if (Targets.resolve(state, event.getWorld().getBlockEntity(event.getPos()))
            == Targets.Kind.UNKNOWN
        || event.getItemStack().getItem() != WrenchBridge.WRENCH.get() && nativeOwns(event, state))
      return;
    // Native tag listeners may run before our adapter. Check intent and protection before those
    // listeners.
    boolean denied = !Tools.active(event.getItemStack()) || interactionDenied(event);
    if (!denied
        && !event.getWorld().isClientSide
        && event.getPlayer() instanceof ServerPlayerEntity player) {
      ServerWorld level = player.getLevel();
      denied =
          player.isSpectator()
              || !player.mayBuild()
              || !level.mayInteract(player, event.getPos())
              || !player.mayUseItemAt(event.getPos(), event.getFace(), event.getItemStack())
              || !breakAllowed(player, level, event.getPos());
    }
    if (denied) {
      event.setCanceled(true);
      event.setCancellationResult(ActionResultType.FAIL);
    }
  }

  private static boolean denied(
      ServerPlayerEntity player, PlayerInteractEvent.RightClickBlock event) {
    return player.isSpectator()
        || !player.mayBuild()
        || !player.getLevel().mayInteract(player, event.getPos())
        || !player.mayUseItemAt(event.getPos(), event.getFace(), event.getItemStack())
        || player.isShiftKeyDown() && !breakAllowed(player, player.getLevel(), event.getPos());
  }

  private static boolean interactionDenied(PlayerInteractEvent.RightClickBlock event) {
    return event.getUseBlock() == net.minecraftforge.eventbus.api.Event.Result.DENY
        || event.getUseItem() == net.minecraftforge.eventbus.api.Event.Result.DENY
            && event.getUseBlock() != net.minecraftforge.eventbus.api.Event.Result.ALLOW;
  }

  private static boolean nativeScope(ItemStack tool, BlockState state) {
    if (net.minecraftforge.registries.ForgeRegistries.ITEMS
        .getKey(tool.getItem())
        .getNamespace()
        .equals(
            net.minecraftforge.registries.ForgeRegistries.BLOCKS
                .getKey(state.getBlock())
                .getNamespace())) return true;
    if (ClassProbe.is(tool.getItem(), "mekanism.common.item.ItemConfigurator")
        && (ClassProbe.is(state.getBlock(), "mekanism.common.block.BlockMekanism")
            || state.getBlock().getClass().getName().startsWith("mekanism."))) return true;
    if (ClassProbe.is(tool.getItem(), "org.cyclops.integrateddynamics.item.ItemWrench")
        && (ClassProbe.is(state.getBlock(), "org.cyclops.integrateddynamics.block.BlockCable")
            || ClassProbe.is(
                state.getBlock(), "org.cyclops.integrateddynamics.core.block.BlockContainerCabled")
            || ClassProbe.is(
                state.getBlock(),
                "org.cyclops.integrateddynamics.core.block.BlockWithEntityGuiCabled"))) return true;
    return ClassProbe.is(tool.getItem(), "mcjty.lib.api.smartwrench.SmartWrench")
        && ClassProbe.is(state.getBlock(), "mcjty.lib.blocks.BaseBlock");
  }

  private static boolean nativeOwns(PlayerInteractEvent.RightClickBlock event, BlockState state) {
    // These native handlers hardcode MAIN_HAND. Their bridge-scoped adapters accept the actual
    // hand.
    return nativeScope(event.getItemStack(), state)
        && !(event.getHand() == Hand.OFF_HAND
            && Tools.active(event.getItemStack())
            && (ClassProbe.is(state.getBlock(), "appeng.block.AEBaseBlock")
                || ClassProbe.is(state.getBlock(), "mcjty.lib.blocks.BaseBlock")
                || ClassProbe.is(
                    state.getBlock(),
                    "org.cyclops.integrateddynamics.core.block.BlockWithEntityGuiCabled")));
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void rightClick(PlayerInteractEvent.RightClickBlock event) {
    if (!SharingControl.allows(event.getPlayer(), event.getItemStack())) return;
    if (event.isCanceled() || REENTERING.get()) return;
    if (VirtualModes.isNative(event.getItemStack())) {
      boolean disabled = interactionDenied(event);
      event.setCanceled(true);
      if (disabled) {
        event.setCancellationResult(ActionResultType.FAIL);
        return;
      }
      if (event.getWorld().isClientSide) {
        event.setCancellationResult(ActionResultType.SUCCESS);
        return;
      }
      try {
        REENTERING.set(true);
        event.setCancellationResult(
            VirtualModes.useOn(
                (ServerPlayerEntity) event.getPlayer(), event.getHand(), event.getHitVec()));
      } finally {
        REENTERING.remove();
      }
      return;
    }
    if (!event.getPlayer().isShiftKeyDown() || !Tools.active(event.getItemStack())) return;
    BlockState state = event.getWorld().getBlockState(event.getPos());
    Targets.Kind target = Targets.resolve(state, event.getWorld().getBlockEntity(event.getPos()));
    if (target == Targets.Kind.UNKNOWN)
      return; // Unknown mods can still use our conventional wrench tags natively.
    if (event.getItemStack().getItem() != WrenchBridge.WRENCH.get() && nativeOwns(event, state))
      return;
    if (interactionDenied(event)) {
      event.setCanceled(true);
      event.setCancellationResult(ActionResultType.FAIL);
      return;
    }
    if (event.getWorld().isClientSide) {
      event.setCanceled(true);
      event.setCancellationResult(ActionResultType.SUCCESS);
      return;
    }
    ServerPlayerEntity player = (ServerPlayerEntity) event.getPlayer();
    ServerWorld level = player.getLevel();
    if (player.isSpectator()
        || !player.mayBuild()
        || !level.mayInteract(player, event.getPos())
        || !player.mayUseItemAt(event.getPos(), event.getFace(), event.getItemStack())) {
      event.setCanceled(true);
      event.setCancellationResult(ActionResultType.FAIL);
      return;
    }
    // One selected, known protocol per click. Denials and exceptions never trigger a second removal
    // strategy.
    ActionResultType result;
    try (var feedback = WrenchContext.serverAction(player)) {
      REENTERING.set(true);
      result = dispatch(player, event.getHand(), event.getHitVec(), event.getItemStack());
    } catch (ReflectiveOperationException | RuntimeException error) {
      WrenchBridge.LOG.warn(
          "Could not apply {} wrench protocol at {}", target, event.getPos(), error);
      player.displayClientMessage(
          new net.minecraft.util.text.TranslationTextComponent(
              "message.wrenchbridge.adapter_failed", target.name()),
          true);
      result = ActionResultType.FAIL;
    } finally {
      REENTERING.remove();
    }
    event.setCanceled(true);
    event.setCancellationResult(result);
  }

  static ActionResultType dispatch(
      ServerPlayerEntity player, Hand hand, BlockRayTraceResult hit, ItemStack source)
      throws ReflectiveOperationException {
    ServerWorld level = player.getLevel();
    Targets.Kind kind =
        Targets.resolve(
            level.getBlockState(hit.getBlockPos()), level.getBlockEntity(hit.getBlockPos()));
    return switch (kind) {
      case ANVIL -> anvil(player, level, hit.getBlockPos(), source);
      case CREATE -> create(player, level, hand, hit, source);
      case PNEUMATIC -> pneumatic(player, level, hand, hit);
      case PRETTY ->
          prettyProxy().getItem().useOn(VirtualModes.context(player, hand, hit, prettyProxy()));
      default -> pipeline(player, level, hand, hit, new ItemStack(WrenchBridge.WRENCH.get()));
    };
  }

  static ActionResultType nativeBlockUse(
      ServerPlayerEntity player, Hand hand, BlockRayTraceResult hit, ItemStack source)
      throws ReflectiveOperationException {
    ServerWorld level = player.getLevel();
    BlockState state = level.getBlockState(hit.getBlockPos());
    if (ClassProbe.is(
        state.getBlock(), "com.simibubi.create.content.equipment.wrench.IWrenchable")) {
      // Create normally handles other mods' wrench tags in RightClickBlock. An explicit simulated
      // wrench reaches the same public entry without replaying a click or changing the held item.
      Method method =
          state
              .getBlock()
              .getClass()
              .getMethod("onWrenched", BlockState.class, ItemUseContext.class);
      return (ActionResultType)
          method.invoke(state.getBlock(), state, VirtualModes.context(player, hand, hit, source));
    }
    if (ClassProbe.is(
        level.getBlockEntity(hit.getBlockPos()), "appeng.blockentity.AEBaseBlockEntity")) {
      Class<?> hook = Class.forName("appeng.hooks.WrenchHook");
      return WrenchContext.runServer(
          player,
          hand,
          player.getItemInHand(hand),
          source,
          () -> {
            try {
              return (ActionResultType)
                  ClassProbe.compatibleMethod(hook, "onPlayerUseBlock", player, level, hand, hit)
                      .invoke(null, player, level, hand, hit);
            } catch (ReflectiveOperationException error) {
              throw new IllegalStateException(error);
            }
          });
    }
    return WrenchContext.runServer(
        player,
        hand,
        player.getItemInHand(hand),
        source,
        () -> {
          return state.use(level, player, hand, hit);
        });
  }

  private static ActionResultType anvil(
      ServerPlayerEntity player, ServerWorld level, BlockPos pos, ItemStack tool)
      throws ReflectiveOperationException {
    Class<?> hammer = Class.forName("dev.dubhe.anvilcraft.item.AnvilHammerItem");
    Method method = ClassProbe.compatibleMethod(hammer, "breakBlock", player, pos, level, tool);
    method.invoke(null, player, pos, level, tool);
    // The bridge already predicts the click's swing. The hammer's own packet path has no
    // vanilla use-result swing, so do not echo an extra animation back to the actor.
    return ActionResultType.CONSUME;
  }

  private static ActionResultType create(
      ServerPlayerEntity player,
      ServerWorld level,
      Hand hand,
      BlockRayTraceResult hit,
      ItemStack source)
      throws ReflectiveOperationException {
    Object block = level.getBlockState(hit.getBlockPos()).getBlock();
    Method method =
        block.getClass().getMethod("onSneakWrenched", BlockState.class, ItemUseContext.class);
    return (ActionResultType)
        method.invoke(
            block,
            level.getBlockState(hit.getBlockPos()),
            VirtualModes.context(player, hand, hit, source));
  }

  private static ActionResultType pneumatic(
      ServerPlayerEntity player, ServerWorld level, Hand hand, BlockRayTraceResult hit)
      throws ReflectiveOperationException {
    Object block = level.getBlockState(hit.getBlockPos()).getBlock();
    Method method =
        block
            .getClass()
            .getMethod(
                "onWrenched",
                World.class,
                PlayerEntity.class,
                BlockPos.class,
                net.minecraft.util.Direction.class,
                Hand.class);
    boolean used =
        (Boolean) method.invoke(block, level, player, hit.getBlockPos(), hit.getDirection(), hand);
    if (used) {
      // The native wrench normally supplies this feedback around the public block protocol.
      // Shared dismantling uses that protocol directly, so reuse the same native sound sender.
      var nativeWrench =
          net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
              new ResourceLocation("pneumaticcraft:pneumatic_wrench"));
      try {
        ClassProbe.compatibleMethod(
                nativeWrench.getClass(), "playWrenchSound", level, hit.getBlockPos())
            .invoke(nativeWrench, level, hit.getBlockPos());
      } catch (ReflectiveOperationException | RuntimeException error) {
        WrenchBridge.LOG.debug("Native pneumatic feedback was unavailable", error);
      }
    }
    return used ? ActionResultType.SUCCESS : ActionResultType.PASS;
  }

  private static ItemStack prettyProxy() {
    var id = new ResourceLocation("prettypipes:wrench");
    return net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(id)
        ? new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id))
        : ItemStack.EMPTY;
  }

  private static ActionResultType pipeline(
      ServerPlayerEntity player,
      ServerWorld level,
      Hand hand,
      BlockRayTraceResult hit,
      ItemStack proxy) {
    if (proxy.isEmpty()) return ActionResultType.FAIL;
    try {
      return WrenchContext.runServer(
          player,
          hand,
          player.getItemInHand(hand),
          proxy,
          () -> {
            var event =
                new PlayerInteractEvent.RightClickBlock(player, hand, hit.getBlockPos(), hit);
            MinecraftForge.EVENT_BUS.post(event);
            if (event.isCanceled()) return event.getCancellationResult();
            if (interactionDenied(event)) return ActionResultType.FAIL;
            ItemUseContext context = VirtualModes.context(player, hand, hit, proxy);
            if (event.getUseItem() != net.minecraftforge.eventbus.api.Event.Result.DENY) {
              ActionResultType first = proxy.getItem().onItemUseFirst(proxy, context);
              if (first != ActionResultType.PASS) return first;
            }
            var state = level.getBlockState(hit.getBlockPos());
            return state.use(level, player, hand, hit);
          });
    } finally {
      player.inventory.setChanged();
      player.containerMenu.broadcastChanges();
    }
  }

  private static boolean breakAllowed(ServerPlayerEntity player, ServerWorld level, BlockPos pos) {
    return !MinecraftForge.EVENT_BUS.post(
        new BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), player));
  }

  @SubscribeEvent
  public static void commands(RegisterCommandsEvent event) {
    event
        .getDispatcher()
        .register(
            Commands.literal("wrenchbridge")
                .then(
                    Commands.literal("diagnose")
                        .executes(
                            ctx -> {
                              int tools = 0;
                              for (var item : net.minecraftforge.registries.ForgeRegistries.ITEMS)
                                if (Tools.candidate(new ItemStack(item))) tools++;
                              final int count = tools;
                              ctx.getSource()
                                  .sendSuccess(
                                      new net.minecraft.util.text.TranslationTextComponent(
                                          "message.wrenchbridge.diagnose", count),
                                      false);
                              return tools;
                            })));
  }
}
