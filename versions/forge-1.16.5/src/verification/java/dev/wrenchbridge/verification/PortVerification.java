package dev.wrenchbridge.verification;

import dev.wrenchbridge.*;
import dev.wrenchbridge.core.NativeModes;
import java.nio.file.*;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.*;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.registries.DeferredRegister;

@Mod("wrenchbridge_verification")
public final class PortVerification {
  private static final DeferredRegister<Block> BLOCKS =
      DeferredRegister.create(
          net.minecraftforge.registries.ForgeRegistries.BLOCKS, "wrenchbridge_verification");
  private static final DeferredRegister<Item> ITEMS =
      DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.ITEMS, "wrenchbridge");
  private static final java.util.function.Supplier<Machine> MACHINE =
      BLOCKS.register(
          "machine",
          () -> new Machine(Block.Properties.of(net.minecraft.block.material.Material.METAL)));
  private static final java.util.function.Supplier<Item> TOOL =
      ITEMS.register("foreign_wrench", () -> new Item(new Item.Properties().stacksTo(1)));
  private static final java.util.function.Supplier<? extends Item> MODE_TOOL =
      ITEMS.register(
          "mode_wrench", () -> new ModeTool(new Item.Properties().stacksTo(1).durability(64)));
  private static final java.util.function.Supplier<Item> BAG =
      ITEMS.register("verification_bag", () -> new Bag());
  private static final net.minecraftforge.items.ItemStackHandler BAG_HANDLER =
      new net.minecraftforge.items.ItemStackHandler(1);

  public static final class Bag extends Item {
    public Bag() {
      super(new Item.Properties().stacksTo(1));
    }

    @Override
    public net.minecraftforge.common.capabilities.ICapabilityProvider initCapabilities(
        ItemStack stack, CompoundNBT data) {
      return new net.minecraftforge.common.capabilities.ICapabilityProvider() {
        @Override
        public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(
            net.minecraftforge.common.capabilities.Capability<T> cap, Direction side) {
          return cap == net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY
              ? net.minecraftforge.common.util.LazyOptional.of(() -> BAG_HANDLER).cast()
              : net.minecraftforge.common.util.LazyOptional.empty();
        }
      };
    }
  }

  private static boolean deny, denyUse, tryReplace;
  private static int nativeCalls, simulatedCalls;
  private static Hand lastNativeHand, lastSimulatedHand, lastClickHand;

  public PortVerification() {
    IEventBus bus =
        net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
    BLOCKS.register(bus);
    ITEMS.register(bus);
    MinecraftForge.EVENT_BUS.addListener(
        EventPriority.HIGHEST,
        false,
        net.minecraftforge.event.world.BlockEvent.BreakEvent.class,
        e -> {
          if (deny) e.setCanceled(true);
        });
    MinecraftForge.EVENT_BUS.addListener(
        EventPriority.HIGH,
        false,
        PlayerInteractEvent.RightClickBlock.class,
        e -> {
          if (denyUse) e.setUseBlock(net.minecraftforge.eventbus.api.Event.Result.DENY);
        });
    MinecraftForge.EVENT_BUS.addListener(
        (FMLServerStartedEvent e) -> {
          if (!Boolean.getBoolean("wrenchbridge.verify")) return;
          String result;
          try {
            result =
                "PASS: "
                    + verifyOffhand(
                        net.minecraftforge.fml.server.ServerLifecycleHooks.getCurrentServer()
                            .overworld())
                    + verifyContainers(
                        net.minecraftforge.fml.server.ServerLifecycleHooks.getCurrentServer()
                            .overworld())
                    + verifyNativeMods(
                        net.minecraftforge.fml.server.ServerLifecycleHooks.getCurrentServer()
                            .overworld())
                    + verifyNativeMenus(
                        net.minecraftforge.fml.server.ServerLifecycleHooks.getCurrentServer()
                            .overworld());
          } catch (Throwable error) {
            WrenchBridge.LOG.error("Port verification failed", error);
            result = "FAIL: " + error;
          }
          try {
            Files.write(
                Paths.get("verification-result.txt"),
                result.getBytes(java.nio.charset.StandardCharsets.UTF_8));
          } catch (Exception error) {
            throw new RuntimeException(error);
          }
          WrenchBridge.LOG.info(result);
          net.minecraftforge.fml.server.ServerLifecycleHooks.getCurrentServer().halt(false);
        });
  }

  public enum Mode {
    CONFIG,
    WRENCH
  }

  public static final class ModeTool extends Item {
    public ModeTool(Properties p) {
      super(p);
    }

    public Mode getMode(ItemStack s) {
      return s.getOrCreateTag().copy().getBoolean("wrench") ? Mode.WRENCH : Mode.CONFIG;
    }

    public void setMode(ItemStack s, PlayerEntity p, Mode mode) {
      CompoundNBT d = s.getOrCreateTag().copy();
      d.putBoolean("wrench", mode == Mode.WRENCH);
      s.setTag(d);
    }

    @Override
    public ActionResultType useOn(net.minecraft.item.ItemUseContext c) {
      if (getMode(c.getItemInHand()) != Mode.CONFIG) return ActionResultType.PASS;
      check(
          c.getPlayer().getItemInHand(c.getHand()).getItem() == WrenchBridge.WRENCH.get(),
          "native item was equipped");
      c.getItemInHand().setDamageValue(c.getItemInHand().getDamageValue() + 1);
      simulatedCalls++;
      lastSimulatedHand = c.getHand();
      if (tryReplace) c.getPlayer().setItemInHand(c.getHand(), new ItemStack(Items.DIAMOND));
      return ActionResultType.SUCCESS;
    }
  }

  public static final class Machine extends Block {
    public Machine(Properties p) {
      super(p.strength(1));
    }

    @Override
    public ActionResultType use(
        BlockState state,
        World level,
        BlockPos pos,
        PlayerEntity player,
        Hand hand,
        BlockRayTraceResult hit) {
      ItemStack stack = player.getItemInHand(hand);
      if (player.isShiftKeyDown()
          && stack.getItem().is(net.minecraft.tags.ItemTags.bind("c:tools/wrench"))) {
        if (!level.isClientSide) {
          nativeCalls++;
          lastNativeHand = hand;
          level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        return level.isClientSide ? ActionResultType.SUCCESS : ActionResultType.CONSUME;
      }
      return ActionResultType.PASS;
    }
  }

  private static void check(boolean b, String m) {
    if (!b) throw new AssertionError(m);
  }

  private static void select(ModePacket packet, ServerPlayerEntity player) throws Exception {
    var method =
        ModePacket.class.getDeclaredMethod("select", ModePacket.class, ServerPlayerEntity.class);
    method.setAccessible(true);
    method.invoke(null, packet, player);
  }

  private static ActionResultType click(
      ServerPlayerEntity player, ServerWorld level, BlockRayTraceResult hit) {
    for (Hand hand : Hand.values()) {
      lastClickHand = hand;
      var result = player.gameMode.useItemOn(player, level, player.getItemInHand(hand), hand, hit);
      if (result.consumesAction() || result == ActionResultType.FAIL) return result;
      var stack = player.getItemInHand(hand);
      if (!stack.isEmpty()) {
        result = player.gameMode.useItem(player, level, stack, hand);
        if (result.consumesAction()) return result;
      }
    }
    return ActionResultType.PASS;
  }

  private static String verifyContainers(ServerWorld level) throws Exception {
    var player = FakePlayerFactory.getMinecraft(level);
    var hit =
        new BlockRayTraceResult(
            Vector3d.atCenterOf(new BlockPos(4, 100, 0)),
            Direction.UP,
            new BlockPos(4, 100, 0),
            false);
    for (Hand hand : Hand.values()) {
      player.inventory.clearContent();
      player.setShiftKeyDown(true);
      ItemStack wrench = new ItemStack(WrenchBridge.WRENCH.get()),
          bag = new ItemStack(BAG.get()),
          source = new ItemStack(MODE_TOOL.get());
      player.setItemInHand(hand, wrench);
      player.inventory.setItem(8, bag);
      BAG_HANDLER.setStackInSlot(0, source);
      check(
          StoredTools.catalog(player).contains("wrenchbridge:mode_wrench|CONFIG"),
          "container source not discovered");
      check(
          VirtualModes.select(player, hand, "wrenchbridge:mode_wrench", "CONFIG"),
          "container selection failed");
      for (int slot = 0; slot < 36; slot++)
        if (slot != 8 && !(hand == Hand.MAIN_HAND && slot == player.inventory.selected))
          player.inventory.setItem(slot, new ItemStack(Items.STONE));
      level.setBlockAndUpdate(hit.getBlockPos(), MACHINE.get().defaultBlockState());
      player.gameMode.useItemOn(player, level, wrench, hand, hit);
      check(
          player.getItemInHand(hand) == wrench
              && BAG_HANDLER.getStackInSlot(0).getDamageValue() == 1,
          "full-inventory container simulation moved item or lost cost");
      check(
          NativeModes.current(MODE_TOOL.get(), BAG_HANDLER.getStackInSlot(0)).equals("CONFIG"),
          "container source mode changed");
      var bound = StoredTools.menuSource(player, "wrenchbridge:mode_wrench");
      check(bound != null, "menu source not bound");
      bound.use(
          tool -> {
            tool.setDamageValue(7);
            return true;
          },
          false);
      check(
          BAG_HANDLER.getStackInSlot(0).getDamageValue() == 7
              && player.getItemInHand(hand) == wrench,
          "menu transaction did not persist");
      player.inventory.setItem(8, ItemStack.EMPTY);
      check(!bound.available(), "removed container kept a valid menu binding");
      int before = simulatedCalls;
      player.gameMode.useItemOn(player, level, wrench, hand, hit);
      check(before == simulatedCalls, "missing source was synthesized");
      check(!WrenchContext.serverOnly() && WrenchContext.hand() == null, "context leaked");
    }
    return "; container main/offhand discovery, full inventory, real cost, mode preservation, menu"
               + " transactions, detached source rejection";
  }

  private static String verifyOffhand(ServerWorld level) throws Exception {
    ServerPlayerEntity player = FakePlayerFactory.getMinecraft(level);
    player.inventory.clearContent();
    player.setShiftKeyDown(true);
    player.setPose(net.minecraft.entity.Pose.CROUCHING);
    player.gameMode.setGameModeForPlayer(GameType.SURVIVAL);
    BlockPos pos = new BlockPos(4, 100, 0);
    player.moveTo(4.5, 101, 2.5, 180, 0);
    BlockRayTraceResult hit =
        new BlockRayTraceResult(Vector3d.atCenterOf(pos), Direction.UP, pos, false);
    ItemStack main = new ItemStack(Items.STICK);
    ItemStack off = new ItemStack(WrenchBridge.WRENCH.get());
    player.setItemInHand(Hand.MAIN_HAND, main);
    player.setItemInHand(Hand.OFF_HAND, off);
    check(
        dev.wrenchbridge.Tools.universalHand(player) == Hand.OFF_HAND,
        "G did not find offhand universal wrench");
    check(
        off.getItem().use(level, player, Hand.OFF_HAND).getResult()
            == net.minecraft.util.ActionResultType.PASS,
        "offhand air use opened the mode palette");
    for (ItemStack stack : java.util.Arrays.asList(off, new ItemStack(TOOL.get()))) {
      player.setItemInHand(Hand.OFF_HAND, stack);
      level.setBlockAndUpdate(pos, MACHINE.get().defaultBlockState());
      int calls = nativeCalls;
      var action = click(player, level, hit);
      check(
          level.isEmptyBlock(pos) && nativeCalls == calls + 1 && lastNativeHand == Hand.OFF_HAND,
          "offhand shared dismantling failed or ran twice: "
              + dev.wrenchbridge.Tools.id(stack)
              + " result="
              + action
              + " calls="
              + (nativeCalls - calls)
              + " lastHand="
              + lastNativeHand
              + " remaining="
              + level.getBlockState(pos));
      check(
          player.getMainHandItem() == main && player.getOffhandItem() == stack,
          "offhand dismantling moved either hand");
      level.setBlockAndUpdate(pos, MACHINE.get().defaultBlockState());
      try {
        deny = true;
        click(player, level, hit);
      } finally {
        deny = false;
      }
      check(
          !level.isEmptyBlock(pos) && nativeCalls == calls + 1,
          "offhand bypassed block protection");
      try {
        denyUse = true;
        click(player, level, hit);
      } finally {
        denyUse = false;
      }
      check(
          !level.isEmptyBlock(pos) && nativeCalls == calls + 1,
          "offhand bypassed interaction denial");
    }
    player.setItemInHand(Hand.OFF_HAND, off);
    ItemStack donor = new ItemStack(MODE_TOOL.get());
    player.inventory.setItem(9, donor);
    // The wire payload must preserve which hand opened the palette.
    var encoded = io.netty.buffer.Unpooled.buffer();
    ModePacket packet = new ModePacket("wrenchbridge:mode_wrench", "CONFIG", true);
    try {
      ModePacket.encode(packet, new net.minecraft.network.PacketBuffer(encoded));
      check(
          ModePacket.decode(new net.minecraft.network.PacketBuffer(encoded)).equals(packet),
          "mode packet lost its hand");
    } finally {
      encoded.release();
    }
    select(packet, player);
    check(
        VirtualModes.mode(off).equals("CONFIG")
            && NativeModes.current(MODE_TOOL.get(), donor).equals("CONFIG"),
        "offhand selection modified a native tool");
    int calls = simulatedCalls;
    try {
      tryReplace = true;
      click(player, level, hit);
    } finally {
      tryReplace = false;
    }
    check(
        simulatedCalls == calls + 1
            && lastSimulatedHand == Hand.OFF_HAND
            && donor.getDamageValue() == 1,
        "offhand simulation lost the hand or real source cost");
    check(
        player.getMainHandItem() == main && player.getOffhandItem() == off,
        "offhand simulation replaced a held item");
    for (int slot = 0; slot < player.inventory.getContainerSize(); slot++)
      check(
          player.inventory.getItem(slot).getItem() != Items.DIAMOND,
          "offhand simulation created a conversion output");
    ItemStack before = off.copy();
    select(new ModePacket("wrenchbridge:mode_wrench", "WRENCH"), player);
    select(new ModePacket("wrenchbridge:mode_wrench", "INVALID", true), player);
    check(
        ItemStack.matches(off, before), "wrong-hand or invalid selection changed offhand profile");
    ItemStack other = new ItemStack(WrenchBridge.WRENCH.get());
    player.setItemInHand(Hand.MAIN_HAND, other);
    check(
        dev.wrenchbridge.Tools.universalHand(player) == Hand.MAIN_HAND,
        "G did not prefer main universal wrench");
    select(new ModePacket("wrenchbridge:mode_wrench", "WRENCH", true), player);
    check(
        VirtualModes.item(other).isEmpty() && VirtualModes.mode(off).equals("WRENCH"),
        "offhand packet changed the main wrench");
    // Both hands can dismantle: only the main hand should execute.
    level.setBlockAndUpdate(pos, MACHINE.get().defaultBlockState());
    calls = nativeCalls;
    click(player, level, hit);
    check(
        level.isEmptyBlock(pos) && nativeCalls == calls + 1 && lastNativeHand == Hand.MAIN_HAND,
        "both hands executed one dismantling click");
    select(new ModePacket("wrenchbridge:mode_wrench", "CONFIG"), player);
    level.setBlockAndUpdate(pos, MACHINE.get().defaultBlockState());
    calls = simulatedCalls;
    click(player, level, hit);
    check(
        !level.isEmptyBlock(pos)
            && simulatedCalls == calls + 1
            && lastSimulatedHand == Hand.MAIN_HAND,
        "offhand dismantled after main-hand configuration consumed the click");
    player.setItemInHand(Hand.OFF_HAND, ItemStack.EMPTY);
    before = other.copy();
    select(new ModePacket("wrenchbridge:mode_wrench", "WRENCH", true), player);
    check(ItemStack.matches(other, before), "stale offhand selection fell back to the main hand");
    // Exercise source discovery from either opposite hand independently of the client's hand
    // priority.
    player.setItemInHand(Hand.OFF_HAND, donor);
    player.inventory.setItem(9, ItemStack.EMPTY);
    int damage = donor.getDamageValue();
    player.gameMode.useItemOn(player, level, other, Hand.MAIN_HAND, hit);
    check(
        donor.getDamageValue() == damage + 1
            && player.getMainHandItem() == other
            && player.getOffhandItem() == donor,
        "main-hand simulation moved or failed to charge an offhand donor");
    player.setItemInHand(Hand.OFF_HAND, off);
    player.setItemInHand(Hand.MAIN_HAND, donor);
    select(new ModePacket("wrenchbridge:mode_wrench", "CONFIG", true), player);
    damage = donor.getDamageValue();
    player.gameMode.useItemOn(player, level, off, Hand.OFF_HAND, hit);
    check(
        donor.getDamageValue() == damage + 1
            && player.getMainHandItem() == donor
            && player.getOffhandItem() == off,
        "offhand simulation moved or failed to charge a main-hand donor");
    check(
        dev.wrenchbridge.WrenchContext.hand() == null
            && !dev.wrenchbridge.WrenchContext.forced(off),
        "wrench context leaked after offhand use");
    player.inventory.clearContent();
    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    return "; offhand: shared and simulated actions, correct hand, real costs, G targeting, packet"
               + " roundtrip and rejection, main-hand priority, single action, protection";
  }

  private static String verifyNativeMods(ServerWorld level) throws Exception {
    if (!net.minecraftforge.fml.ModList.get().isLoaded("mekanism")) return "";
    var p = FakePlayerFactory.getMinecraft(level);
    BlockPos pos = new BlockPos(5, 100, 0);
    var hit = new BlockRayTraceResult(Vector3d.atCenterOf(pos), Direction.UP, pos, false);
    int cases = 0;
    String[] tools = {
      "wrenchbridge:universal_wrench",
      "mekanism:configurator",
      "appliedenergistics2:certus_quartz_wrench"
    };
    String[] targets = {"mekanism:basic_energy_cube", "appliedenergistics2:interface"};
    for (Hand hand : Hand.values())
      for (boolean simulate : new boolean[] {false, true})
        for (String tool : tools) {
          if (simulate && tool.startsWith("wrenchbridge:")) continue;
          for (String target : targets) {
            if (!simulate && tool.split(":")[0].equals(target.split(":")[0])) continue;
            p.inventory.clearContent();
            p.setShiftKeyDown(true);
            p.setPose(net.minecraft.entity.Pose.CROUCHING);
            ItemStack donor =
                new ItemStack(
                    net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                        new ResourceLocation(tool)));
            check(!donor.isEmpty() && Tools.candidate(donor), "native tool not detected: " + tool);
            if (tool.equals("mekanism:configurator"))
              check(
                  NativeModes.select(donor.getItem(), donor, p, "WRENCH"),
                  "Mekanism wrench mode unavailable");
            String mode = NativeModes.current(donor.getItem(), donor);
            ItemStack held = simulate ? new ItemStack(WrenchBridge.WRENCH.get()) : donor;
            p.setItemInHand(hand, held);
            if (simulate) {
              p.inventory.setItem(9, donor);
              check(VirtualModes.select(p, hand, tool, mode), "native profile rejected");
            }
            var block =
                net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(
                    new ResourceLocation(target));
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(pos, block.defaultBlockState());
            block.setPlacedBy(level, pos, level.getBlockState(pos), p, new ItemStack(block));
            var result = click(p, level, hit);
            check(
                level.isEmptyBlock(pos),
                "native pair failed: "
                    + hand
                    + " "
                    + tool
                    + " -> "
                    + target
                    + " simulated="
                    + simulate
                    + " result="
                    + result);
            check(
                p.getItemInHand(hand) == held
                    && (!simulate
                        || p.inventory.getItem(9) == donor
                            && NativeModes.current(donor.getItem(), donor).equals(mode)),
                "native pair moved a tool or changed mode");
            level.setBlockAndUpdate(pos, block.defaultBlockState());
            try {
              deny = true;
              click(p, level, hit);
            } finally {
              deny = false;
            }
            check(
                !level.isEmptyBlock(pos),
                "native pair bypassed protection: "
                    + tool
                    + " -> "
                    + target
                    + " simulate="
                    + simulate);
            cases++;
          }
        }
    for (Hand hand : Hand.values()) {
      p.inventory.clearContent();
      p.setShiftKeyDown(false);
      p.setPose(net.minecraft.entity.Pose.STANDING);
      ItemStack held = new ItemStack(WrenchBridge.WRENCH.get()),
          donor =
              new ItemStack(
                  net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                      new ResourceLocation("mekanism:configurator")));
      p.setItemInHand(hand, held);
      p.inventory.setItem(9, donor);
      String original = NativeModes.current(donor.getItem(), donor);
      check(
          VirtualModes.select(p, hand, "mekanism:configurator", "ROTATE"),
          "Mekanism rotate mode missing");
      var block =
          net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(
              new ResourceLocation("mekanism:enrichment_chamber"));
      level.setBlockAndUpdate(pos, block.defaultBlockState());
      var rotate = new BlockRayTraceResult(Vector3d.atCenterOf(pos), Direction.EAST, pos, false);
      p.gameMode.useItemOn(p, level, held, hand, rotate);
      var state = level.getBlockState(pos);
      var facing = net.minecraft.state.properties.BlockStateProperties.FACING;
      var horizontal = net.minecraft.state.properties.BlockStateProperties.HORIZONTAL_FACING;
      check(
          (state.hasProperty(facing) ? state.getValue(facing) : state.getValue(horizontal))
                  == Direction.EAST
              && p.getItemInHand(hand) == held,
          "native Mekanism rotation failed");
      check(
          NativeModes.current(donor.getItem(), donor).equals(original),
          "native rotation changed donor mode");
    }
    return "; real Mekanism/AE2: "
        + cases
        + " main/offhand physical/simulated dismantle pairs with protection, native rotation and"
        + " mode preservation";
  }

  private static String verifyNativeMenus(ServerWorld level) throws Exception {
    if (!net.minecraftforge.fml.ModList.get().isLoaded("appliedenergistics2")) return "";
    var player = FakePlayerFactory.getMinecraft(level);
    Class<?> hostType = Class.forName("appeng.api.implementations.guiobjects.INetworkTool");
    Class<?> menuType = Class.forName("appeng.container.me.networktool.NetworkToolContainer");
    for (Hand hand : Hand.values()) {
      player.inventory.clearContent();
      for (int i = 0; i < 36; i++) player.inventory.setItem(i, new ItemStack(Items.STONE, 64));
      ItemStack held = new ItemStack(WrenchBridge.WRENCH.get()), bag = new ItemStack(BAG.get());
      player.setItemInHand(hand, held);
      player.inventory.setItem(8, bag);
      BAG_HANDLER.setStackInSlot(
          0,
          new ItemStack(
              net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                  new ResourceLocation("appliedenergistics2:network_tool"))));
      check(
          VirtualModes.select(player, hand, "appliedenergistics2:network_tool", ""),
          "stored network profile unavailable");
      check(
          ContainerMenus.open(player, hand, null) == ActionResultType.SUCCESS,
          "native network menu open rejected");
      Object host = ContainerMenus.guiObject(held, player, level, null);
      var menu =
          (net.minecraft.inventory.container.Container)
              menuType
                  .getConstructor(
                      int.class, net.minecraft.entity.player.PlayerInventory.class, hostType)
                  .newInstance(77, player.inventory, host);
      player.containerMenu = menu;
      ItemStack cards =
          new ItemStack(
              net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                  new ResourceLocation("appliedenergistics2:speed_card")),
              3);
      player.inventory.setCarried(cards);
      menu.clicked(0, 0, net.minecraft.inventory.container.ClickType.PICKUP, player);
      check(
          player.inventory.getCarried().isEmpty() && menu.getSlot(0).getItem().getCount() == 3,
          "native card insertion failed");
      check(
          String.valueOf(BAG_HANDLER.getStackInSlot(0).getTag()).contains("speed_card"),
          "card insertion did not persist in bag");
      menu.clicked(0, 0, net.minecraft.inventory.container.ClickType.PICKUP, player);
      check(
          player.inventory.getCarried().getCount() == 3
              && !String.valueOf(BAG_HANDLER.getStackInSlot(0).getTag()).contains("speed_card"),
          "card removal did not persist in bag");
      player.inventory.setCarried(ItemStack.EMPTY);
      menuType.getMethod("toggleFacadeMode").invoke(menu);
      check(
          BAG_HANDLER.getStackInSlot(0).getTag().getBoolean("hideFacades"),
          "native facade setting not persisted");
      check(player.getItemInHand(hand) == held, "native menu replaced held wrench");
      player.containerMenu = player.inventoryMenu;
      ContainerMenus.forget(player);
    }
    return "; native AE2 menus: main/offhand, full inventory, real stored tool and persisted native"
               + " card changes";
  }
}
