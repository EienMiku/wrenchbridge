package dev.wrenchbridge.verification;

import dev.wrenchbridge.*;
import dev.wrenchbridge.core.NativeModes;
import java.nio.file.*;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.*;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.items.*;

@Mod(
    modid = "wrenchbridge_verification",
    name = "Wrench Bridge port verification",
    version = "1",
    dependencies = "required-after:wrenchbridge")
public final class PortVerification {
  static final Block MACHINE =
      new Machine().setRegistryName("mekanism", "wrenchbridge_verification_machine");
  static final Item FOREIGN = new Item().setRegistryName("wrenchbridge", "foreign_wrench");
  static final ModeTool MODE = new ModeTool();
  static final Bag BAG = new Bag();
  static final ItemStackHandler CONTENTS = new ItemStackHandler(1);
  static boolean deny, denyUse, replace;
  static int calls, simulated;
  static EnumHand last;

  public enum Mode {
    CONFIG,
    WRENCH
  }

  public static final class ModeTool extends Item {
    public ModeTool() {
      setRegistryName("wrenchbridge", "mode_wrench");
      setMaxStackSize(1);
      setMaxDamage(64);
    }

    public Mode getMode(ItemStack s) {
      return s.hasTagCompound() && s.getTagCompound().getBoolean("config")
          ? Mode.CONFIG
          : Mode.WRENCH;
    }

    public void setMode(ItemStack s, EntityPlayer p, Mode m) {
      NBTTagCompound tag = s.hasTagCompound() ? s.getTagCompound() : new NBTTagCompound();
      tag.setBoolean("config", m == Mode.CONFIG);
      s.setTagCompound(tag);
    }

    @Override
    public EnumActionResult onItemUse(
        EntityPlayer p,
        World world,
        BlockPos pos,
        EnumHand hand,
        EnumFacing f,
        float x,
        float y,
        float z) {
      ItemStack source = WrenchContext.nativeHand(p, hand);
      if (getMode(source) != Mode.CONFIG) return EnumActionResult.PASS;
      check(
          p.getHeldItem(hand).getItem() == WrenchBridge.WRENCH,
          "actual hand changed during native call");
      source.setItemDamage(source.getItemDamage() + 1);
      simulated++;
      last = hand;
      if (replace) p.setHeldItem(hand, new ItemStack(net.minecraft.init.Items.DIAMOND));
      return EnumActionResult.SUCCESS;
    }
  }

  public static final class Bag extends Item {
    public Bag() {
      setRegistryName("wrenchbridge", "verification_bag");
      setMaxStackSize(1);
    }

    public ICapabilityProvider initCapabilities(ItemStack stack, NBTTagCompound data) {
      return new ICapabilityProvider() {
        public boolean hasCapability(Capability<?> c, EnumFacing f) {
          return c == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY;
        }

        public <T> T getCapability(Capability<T> c, EnumFacing f) {
          return c == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY ? (T) CONTENTS : null;
        }
      };
    }
  }

  public static final class Machine extends Block {
    Machine() {
      super(Material.IRON);
    }

    public boolean onBlockActivated(
        World w,
        BlockPos pos,
        IBlockState state,
        EntityPlayer p,
        EnumHand hand,
        EnumFacing face,
        float x,
        float y,
        float z) {
      ItemStack held = p.getHeldItem(hand);
      if (p.isSneaking()
          && (WrenchContext.forcedItem(held.getItem()) || held.getItem() == FOREIGN)) {
        if (!w.isRemote) {
          calls++;
          last = hand;
          w.setBlockToAir(pos);
        }
        return true;
      }
      return false;
    }
  }

  @Mod.EventHandler
  public void pre(FMLPreInitializationEvent e) {
    MinecraftForge.EVENT_BUS.register(this);
  }

  @Mod.EventBusSubscriber(modid = "wrenchbridge_verification")
  public static final class Registry {
    @SubscribeEvent
    public static void blocks(RegistryEvent.Register<Block> e) {
      e.getRegistry().register(MACHINE);
    }

    @SubscribeEvent
    public static void items(RegistryEvent.Register<Item> e) {
      e.getRegistry().registerAll(FOREIGN, MODE, BAG);
    }
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public void protect(BlockEvent.BreakEvent e) {
    if (deny) e.setCanceled(true);
  }

  @SubscribeEvent(priority = EventPriority.HIGH)
  public void interaction(PlayerInteractEvent.RightClickBlock e) {
    if (denyUse) e.setUseBlock(Event.Result.DENY);
  }

  static void check(boolean condition, String text) {
    if (!condition) throw new AssertionError(text);
  }

  static EnumActionResult click(EntityPlayerMP p, BlockPos pos) {
    for (EnumHand hand : EnumHand.values()) {
      EnumActionResult result =
          p.interactionManager.processRightClickBlock(
              p, p.world, p.getHeldItem(hand), hand, pos, EnumFacing.UP, .5F, .5F, .5F);
      if (result != EnumActionResult.PASS) return result;
      result = p.interactionManager.processRightClick(p, p.world, p.getHeldItem(hand), hand);
      if (result != EnumActionResult.PASS) return result;
    }
    return EnumActionResult.PASS;
  }

  static String verify(net.minecraft.world.WorldServer world) throws Exception {
    EntityPlayerMP p = FakePlayerFactory.getMinecraft(world);
    p.interactionManager.setGameType(GameType.SURVIVAL);
    p.setSneaking(true);
    p.setPosition(4.5, 101, 2.5);
    BlockPos pos = new BlockPos(4, 100, 0);
    net.minecraft.inventory.InventoryCrafting grid =
        new net.minecraft.inventory.InventoryCrafting(
            new net.minecraft.inventory.Container() {
              public boolean canInteractWith(EntityPlayer player) {
                return true;
              }
            },
            3,
            3);
    grid.setInventorySlotContents(1, new ItemStack(net.minecraft.init.Items.GOLD_INGOT));
    grid.setInventorySlotContents(5, new ItemStack(net.minecraft.init.Items.IRON_INGOT));
    grid.setInventorySlotContents(4, new ItemStack(net.minecraft.init.Items.STICK));
    grid.setInventorySlotContents(6, new ItemStack(net.minecraft.init.Items.STICK));
    net.minecraft.item.crafting.IRecipe recipe =
        net.minecraftforge.fml.common.registry.ForgeRegistries.RECIPES.getValue(
            new ResourceLocation("wrenchbridge:universal_wrench"));
    check(
        recipe != null && recipe.matches(grid, world),
        "mixed ingot/rod recipe missing or mismatched");
    for (EnumHand hand : EnumHand.values()) {
      p.inventory.clear();
      p.setSneaking(true);
      ItemStack wrench = new ItemStack(WrenchBridge.WRENCH);
      p.setHeldItem(hand, wrench);
      check(Tools.universalHand(p) == hand, "G targets wrong hand");
      check(
          WrenchBridge.WRENCH.onItemRightClick(world, p, hand).getType() == EnumActionResult.PASS,
          "air use opened palette");
      world.setBlockState(pos, MACHINE.getDefaultState());
      int before = calls;
      EnumActionResult action = click(p, pos);
      check(
          world.isAirBlock(pos) && calls == before + 1 && last == hand,
          "shared dismantling wrong hand or repeated: hand="
              + hand
              + " action="
              + action
              + " calls="
              + (calls - before)
              + " last="
              + last
              + " state="
              + world.getBlockState(pos)
              + " active="
              + Tools.active(wrench)
              + " edit="
              + p.capabilities.allowEdit);
      world.setBlockState(pos, MACHINE.getDefaultState());
      try {
        deny = true;
        click(p, pos);
      } finally {
        deny = false;
      }
      check(!world.isAirBlock(pos), "break protection bypassed");
      try {
        denyUse = true;
        click(p, pos);
      } finally {
        denyUse = false;
      }
      check(!world.isAirBlock(pos), "use protection bypassed");
      ItemStack donor = new ItemStack(MODE);
      p.inventory.setInventorySlotContents(9, donor);
      check(VirtualModes.select(p, hand, "wrenchbridge:mode_wrench", "CONFIG"), "profile rejected");
      int used = simulated;
      try {
        replace = true;
        click(p, pos);
      } finally {
        replace = false;
      }
      check(
          p.getHeldItem(hand) == wrench
              && simulated == used + 1
              && last == hand
              && donor.getItemDamage() == 1,
          "simulation changed hand, lost cost, or ran twice");
      check(NativeModes.current(MODE, donor).equals("WRENCH"), "native donor mode changed");
      check(
          !VirtualModes.select(p, hand, "wrenchbridge:mode_wrench", "INVALID"),
          "invalid profile accepted");
      EnumHand other = hand == EnumHand.MAIN_HAND ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND;
      check(
          !VirtualModes.select(p, other, "wrenchbridge:mode_wrench", "CONFIG"),
          "profile applied to ordinary item");
      ModePacket
          packet = new ModePacket("wrenchbridge:mode_wrench", "CONFIG", hand == EnumHand.OFF_HAND),
          decoded = new ModePacket();
      io.netty.buffer.ByteBuf buffer = io.netty.buffer.Unpooled.buffer();
      packet.toBytes(buffer);
      decoded.fromBytes(buffer);
      buffer.release();
      check(
          decoded.itemId.equals(packet.itemId)
              && decoded.mode.equals(packet.mode)
              && decoded.offhand == packet.offhand,
          "packet roundtrip lost hand");
      p.inventory.setInventorySlotContents(9, ItemStack.EMPTY);
      p.inventory.setInventorySlotContents(8, new ItemStack(BAG));
      CONTENTS.setStackInSlot(0, donor);
      check(
          StoredTools.catalog(p).contains("wrenchbridge:mode_wrench|CONFIG"),
          "container source missing");
      for (int i = 0; i < 36; i++)
        if (i != 8 && !(hand == EnumHand.MAIN_HAND && i == p.inventory.currentItem))
          p.inventory.setInventorySlotContents(i, new ItemStack(net.minecraft.init.Items.STICK));
      click(p, pos);
      check(
          CONTENTS.getStackInSlot(0).getItemDamage() == 2 && p.getHeldItem(hand) == wrench,
          "full-inventory source lost or cost not persisted");
      StoredTools.MenuSource binding = StoredTools.menuSource(p, "wrenchbridge:mode_wrench");
      check(binding != null, "menu source missing");
      binding.use(
          tool -> {
            tool.setItemDamage(7);
            return true;
          },
          false);
      check(CONTENTS.getStackInSlot(0).getItemDamage() == 7, "menu transaction lost data");
      p.inventory.setInventorySlotContents(8, ItemStack.EMPTY);
      check(!binding.available(), "removed bag kept valid menu source");
      check(!WrenchContext.serverOnly() && WrenchContext.hand() == null, "native context leaked");
    }
    return "main/offhand shared and simulated actions, real cost, mode preservation, replacement"
               + " rejection, full inventory, container transactions, stale hand and protection"
               + " checks";
  }

  @Mod.EventHandler
  public void start(FMLServerStartedEvent e) {
    if (!Boolean.getBoolean("wrenchbridge.verify")) return;
    net.minecraft.server.MinecraftServer server =
        net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
    String result;
    try {
      result =
          "PASS: "
              + verify(server.getWorld(0))
              + nativeMods(server.getWorld(0))
              + nativeMenus(server.getWorld(0));
    } catch (Throwable t) {
      WrenchBridge.LOG.error("Port verification failed", t);
      result = "FAIL: " + t;
    }
    try {
      Files.write(
          Paths.get("verification-result.txt"),
          result.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
    WrenchBridge.LOG.info(result);
    server.initiateShutdown();
  }

  static String nativeMods(net.minecraft.world.WorldServer world) throws Exception {
    if (!net.minecraftforge.fml.common.Loader.isModLoaded("mekanism")) return "";
    EntityPlayerMP p = FakePlayerFactory.getMinecraft(world);
    BlockPos pos = new BlockPos(5, 100, 0);
    int cases = 0;
    String[]
        tools =
            {
              "wrenchbridge:universal_wrench",
              "mekanism:configurator",
              "appliedenergistics2:certus_quartz_wrench"
            },
        targets = {"mekanism:energycube", "appliedenergistics2:interface"};
    for (EnumHand hand : EnumHand.values())
      for (boolean simulate : new boolean[] {false, true})
        for (String id : tools) {
          if (simulate && id.startsWith("wrenchbridge:")) continue;
          for (String target : targets) {
            if (!simulate && id.split(":")[0].equals(target.split(":")[0])) continue;
            p.inventory.clear();
            p.setSneaking(true);
            ItemStack donor =
                new ItemStack(
                    net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.getValue(
                        new ResourceLocation(id)));
            check(!donor.isEmpty() && Tools.candidate(donor), "native tool not detected: " + id);
            if (id.equals("mekanism:configurator"))
              check(
                  NativeModes.select(donor.getItem(), donor, p, "WRENCH"),
                  "native wrench mode missing");
            String mode = NativeModes.current(donor.getItem(), donor);
            ItemStack held = simulate ? new ItemStack(WrenchBridge.WRENCH) : donor;
            p.setHeldItem(hand, held);
            if (simulate) {
              p.inventory.setInventorySlotContents(9, donor);
              check(VirtualModes.select(p, hand, id, mode), "native profile rejected");
            }
            Block block =
                net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.getValue(
                    new ResourceLocation(target));
            check(block != null, "missing native target " + target);
            world.setBlockToAir(pos);
            world.setBlockState(pos, block.getDefaultState());
            block.onBlockPlacedBy(world, pos, world.getBlockState(pos), p, new ItemStack(block));
            EnumActionResult result = click(p, pos);
            check(
                world.isAirBlock(pos),
                "native pair failed: "
                    + hand
                    + " "
                    + id
                    + " -> "
                    + target
                    + " simulation="
                    + simulate
                    + " result="
                    + result);
            check(
                p.getHeldItem(hand) == held
                    && (!simulate
                        || p.inventory.getStackInSlot(9) == donor
                            && NativeModes.current(donor.getItem(), donor).equals(mode)),
                "native pair changed source or held tool");
            world.setBlockState(pos, block.getDefaultState());
            try {
              deny = true;
              click(p, pos);
            } finally {
              deny = false;
            }
            check(!world.isAirBlock(pos), "native protection bypassed");
            cases++;
          }
        }
    return "; real AE2/Mekanism: "
        + cases
        + " physical/simulated main/offhand pairs with protection";
  }

  static String nativeMenus(net.minecraft.world.WorldServer world) throws Exception {
    if (!net.minecraftforge.fml.common.Loader.isModLoaded("appliedenergistics2")) return "";
    EntityPlayerMP p = FakePlayerFactory.getMinecraft(world);
    Class<?> hostType = Class.forName("appeng.api.implementations.guiobjects.INetworkTool"),
        menuType = Class.forName("appeng.container.implementations.ContainerNetworkTool");
    Object api = Class.forName("appeng.api.AEApi").getMethod("instance").invoke(null);
    Object defs = api.getClass().getMethod("definitions").invoke(api);
    Object mats = defs.getClass().getMethod("materials").invoke(defs);
    Object speed = mats.getClass().getMethod("cardSpeed").invoke(mats);
    ItemStack cards =
        (ItemStack)
            ((java.util.Optional<?>)
                    speed.getClass().getMethod("maybeStack", int.class).invoke(speed, 3))
                .get();
    for (EnumHand hand : EnumHand.values()) {
      p.inventory.clear();
      for (int i = 0; i < 36; i++)
        p.inventory.setInventorySlotContents(i, new ItemStack(net.minecraft.init.Items.STICK, 64));
      ItemStack held = new ItemStack(WrenchBridge.WRENCH);
      p.setHeldItem(hand, held);
      p.inventory.setInventorySlotContents(8, new ItemStack(BAG));
      CONTENTS.setStackInSlot(
          0,
          new ItemStack(
              net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.getValue(
                  new ResourceLocation("appliedenergistics2:network_tool"))));
      check(
          VirtualModes.select(p, hand, "appliedenergistics2:network_tool", ""),
          "network profile not detected");
      check(
          ContainerMenus.open(p, hand, null) == EnumActionResult.SUCCESS,
          "native menu open failed");
      Object host = ContainerMenus.guiObject(held, p, world, BlockPos.ORIGIN);
      net.minecraft.inventory.Container menu =
          (net.minecraft.inventory.Container)
              menuType
                  .getConstructor(InventoryPlayer.class, hostType)
                  .newInstance(p.inventory, host);
      p.openContainer = menu;
      p.inventory.setItemStack(cards.copy());
      menu.slotClick(0, 0, net.minecraft.inventory.ClickType.PICKUP, p);
      check(
          p.inventory.getItemStack().isEmpty() && menu.getSlot(0).getStack().getCount() == 3,
          "native card insertion failed");
      Object fresh = ContainerMenus.guiObject(held, p, world, BlockPos.ORIGIN);
      IItemHandler inventory =
          (IItemHandler) fresh.getClass().getMethod("getInventory").invoke(fresh);
      check(inventory.getStackInSlot(0).getCount() == 3, "native cards not persisted in bag");
      menu.slotClick(0, 0, net.minecraft.inventory.ClickType.PICKUP, p);
      check(p.inventory.getItemStack().getCount() == 3, "native card removal failed");
      p.inventory.setItemStack(ItemStack.EMPTY);
      fresh = ContainerMenus.guiObject(held, p, world, BlockPos.ORIGIN);
      inventory = (IItemHandler) fresh.getClass().getMethod("getInventory").invoke(fresh);
      check(inventory.getStackInSlot(0).isEmpty(), "removed native cards not persisted");
      menuType.getMethod("toggleFacadeMode").invoke(menu);
      check(
          CONTENTS.getStackInSlot(0).getTagCompound().getBoolean("hideFacades"),
          "native settings not persisted in bag");
      check(p.getHeldItem(hand) == held, "native menu changed hand");
      p.openContainer = p.inventoryContainer;
      ContainerMenus.forget(p);
    }
    return "; native AE2 menus: both hands, full inventory, stored real tool, card"
               + " insertion/removal and facade setting persisted";
  }
}
