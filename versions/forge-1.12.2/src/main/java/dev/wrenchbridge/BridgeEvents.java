package dev.wrenchbridge;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public final class BridgeEvents {
  private static final ThreadLocal<Boolean> REENTERING = ThreadLocal.withInitial(() -> false);
  private static final Set<String> NAMESPACES =
      new HashSet<String>(
          Arrays.asList(
              "appliedenergistics2",
              "ae2",
              "mekanism",
              "thermalfoundation",
              "thermalexpansion",
              "thermaldynamics",
              "enderio",
              "rftools",
              "rftoolscontrol",
              "xnet",
              "refinedstorage",
              "integrateddynamics",
              "integratedtunnels",
              "pneumaticcraft",
              "buildcraftcore",
              "buildcraftfactory",
              "buildcrafttransport",
              "buildcraftbuilders",
              "ic2"));

  private BridgeEvents() {}

  public static boolean knownTarget(Block block) {
    return NAMESPACES.contains(ForgeRegistries.BLOCKS.getKey(block).getNamespace());
  }

  private static boolean interactionDenied(PlayerInteractEvent.RightClickBlock event) {
    return event.getUseBlock() == net.minecraftforge.fml.common.eventhandler.Event.Result.DENY
        || event.getUseItem() == net.minecraftforge.fml.common.eventhandler.Event.Result.DENY;
  }

  private static boolean ownTarget(ItemStack tool, Block block) {
    return ForgeRegistries.ITEMS
        .getKey(tool.getItem())
        .getNamespace()
        .equals(ForgeRegistries.BLOCKS.getKey(block).getNamespace());
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void guard(PlayerInteractEvent.RightClickBlock e) {
    if (e.isCanceled() || REENTERING.get()) return;
    EntityPlayerMP p =
        e.getEntityPlayer() instanceof EntityPlayerMP ? (EntityPlayerMP) e.getEntityPlayer() : null;
    ItemStack held = e.getItemStack(), off = e.getEntityPlayer().getHeldItemOffhand();
    if (e.getHand() == EnumHand.MAIN_HAND
        && held.isEmpty()
        && e.getEntityPlayer().isSneaking()
        && off.getItem() instanceof UniversalWrenchItem
        && (VirtualModes.isNative(off)
            || knownTarget(e.getWorld().getBlockState(e.getPos()).getBlock()))) {
      e.setCanceled(true);
      e.setCancellationResult(EnumActionResult.PASS);
      return;
    }
    if (!SharingControl.allows(e.getEntityPlayer(), e.getItemStack())) return;
    boolean nativeMode = VirtualModes.isNative(held);
    if (!nativeMode
        && (!e.getEntityPlayer().isSneaking()
            || !Tools.candidate(held)
            || !knownTarget(e.getWorld().getBlockState(e.getPos()).getBlock())
            || held.getItem() != WrenchBridge.WRENCH
                && ownTarget(held, e.getWorld().getBlockState(e.getPos()).getBlock()))) return;
    boolean denied = interactionDenied(e) || !nativeMode && !Tools.active(held);
    if (p != null) {
      denied |=
          p.isSpectator()
              || !p.capabilities.allowEdit
              || !e.getWorld().isBlockModifiable(p, e.getPos())
              || !p.canPlayerEdit(e.getPos(), e.getFace(), held);
      if (!denied && p.isSneaking())
        denied =
            MinecraftForge.EVENT_BUS.post(
                new BlockEvent.BreakEvent(
                    e.getWorld(), e.getPos(), e.getWorld().getBlockState(e.getPos()), p));
    }
    if (denied) {
      e.setCanceled(true);
      e.setCancellationResult(EnumActionResult.FAIL);
    }
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void use(PlayerInteractEvent.RightClickBlock e) {
    if (!SharingControl.allows(e.getEntityPlayer(), e.getItemStack())) return;
    if (e.isCanceled() || REENTERING.get()) return;
    boolean nativeMode = VirtualModes.isNative(e.getItemStack());
    if (!nativeMode
        && (!e.getEntityPlayer().isSneaking()
            || !Tools.active(e.getItemStack())
            || !knownTarget(e.getWorld().getBlockState(e.getPos()).getBlock())
            || e.getItemStack().getItem() != WrenchBridge.WRENCH
                && ownTarget(e.getItemStack(), e.getWorld().getBlockState(e.getPos()).getBlock())))
      return;
    boolean disabled = interactionDenied(e);
    e.setCanceled(true);
    if (disabled) {
      e.setCancellationResult(EnumActionResult.FAIL);
      return;
    }
    if (e.getWorld().isRemote) {
      e.setCancellationResult(EnumActionResult.SUCCESS);
      return;
    }
    EntityPlayerMP player = (EntityPlayerMP) e.getEntityPlayer();
    net.minecraft.util.math.RayTraceResult hit =
        new net.minecraft.util.math.RayTraceResult(
            e.getHitVec() == null ? new Vec3d(e.getPos()).add(.5, .5, .5) : e.getHitVec(),
            e.getFace(),
            e.getPos());
    try {
      REENTERING.set(true);
      e.setCancellationResult(
          nativeMode
              ? VirtualModes.useOn(player, e.getHand(), hit)
              : dispatch(player, e.getHand(), hit, e.getItemStack()));
    } catch (RuntimeException error) {
      WrenchBridge.LOG.warn("Wrench protocol failed at {}", e.getPos(), error);
      e.setCancellationResult(EnumActionResult.FAIL);
    } finally {
      REENTERING.remove();
    }
  }

  static EnumActionResult dispatch(
      EntityPlayerMP player,
      EnumHand hand,
      net.minecraft.util.math.RayTraceResult hit,
      ItemStack source) {
    return WrenchContext.runServer(
        player,
        hand,
        new ItemStack(WrenchBridge.WRENCH),
        () -> {
          PlayerInteractEvent.RightClickBlock replay =
              new PlayerInteractEvent.RightClickBlock(
                  player, hand, hit.getBlockPos(), hit.sideHit, hit.hitVec);
          MinecraftForge.EVENT_BUS.post(replay);
          if (replay.isCanceled()) return replay.getCancellationResult();
          if (interactionDenied(replay)) return EnumActionResult.FAIL;
          return blockUse(player, hand, hit);
        });
  }

  static EnumActionResult blockUse(
      EntityPlayerMP player, EnumHand hand, net.minecraft.util.math.RayTraceResult hit) {
    net.minecraft.block.state.IBlockState state = player.world.getBlockState(hit.getBlockPos());
    return state
            .getBlock()
            .onBlockActivated(
                player.world,
                hit.getBlockPos(),
                state,
                player,
                hand,
                hit.sideHit,
                (float) (hit.hitVec.x - hit.getBlockPos().getX()),
                (float) (hit.hitVec.y - hit.getBlockPos().getY()),
                (float) (hit.hitVec.z - hit.getBlockPos().getZ()))
        ? EnumActionResult.SUCCESS
        : EnumActionResult.PASS;
  }

  @SubscribeEvent
  public static void logout(
      net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent e) {
    if (e.player instanceof EntityPlayerMP) StoredTools.forget((EntityPlayerMP) e.player);
  }

  @SubscribeEvent
  public static void tick(net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent e) {
    if (e.phase == net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END
        && e.player instanceof EntityPlayerMP) ContainerMenus.tick((EntityPlayerMP) e.player);
  }
}
