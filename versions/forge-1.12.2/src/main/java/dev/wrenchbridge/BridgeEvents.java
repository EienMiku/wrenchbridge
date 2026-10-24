package dev.wrenchbridge;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public final class BridgeEvents {
    private static final ThreadLocal<Boolean> REENTERING = ThreadLocal.withInitial(() -> false);
    private static final Set<String> NAMESPACES = new HashSet<String>(Arrays.asList("appliedenergistics2", "ae2", "mekanism",
            "thermalfoundation", "thermalexpansion", "thermaldynamics", "enderio", "rftools", "rftoolscontrol", "xnet",
            "refinedstorage", "integrateddynamics", "integratedtunnels", "pneumaticcraft", "buildcraftcore", "buildcraftfactory",
            "buildcrafttransport", "buildcraftbuilders", "ic2"));
    private BridgeEvents() {}
    public static boolean knownTarget(Block block) {
        return NAMESPACES.contains(ForgeRegistries.BLOCKS.getKey(block).getNamespace());
    }
    private static boolean interactionDenied(PlayerInteractEvent.RightClickBlock event) {
        return event.getUseBlock() == net.minecraftforge.fml.common.eventhandler.Event.Result.DENY || event.getUseItem() == net.minecraftforge.fml.common.eventhandler.Event.Result.DENY;
    }
    private static boolean ownTarget(ItemStack tool, Block block) {
        return ForgeRegistries.ITEMS.getKey(tool.getItem()).getNamespace()
                .equals(ForgeRegistries.BLOCKS.getKey(block).getNamespace());
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void guard(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || REENTERING.get() || !event.getEntityPlayer().isSneaking()
                || !Tools.candidate(event.getItemStack())) return;
        Block block = event.getWorld().getBlockState(event.getPos()).getBlock();
        if (!knownTarget(block) || event.getItemStack().getItem() != WrenchBridge.WRENCH && ownTarget(event.getItemStack(), block)) return;
        boolean denied = !Tools.active(event.getItemStack()) || interactionDenied(event);
        if (!denied && !event.getWorld().isRemote) {
            EntityPlayerMP player = (EntityPlayerMP) event.getEntityPlayer();
            denied = player.isSpectator() || !player.capabilities.allowEdit || !event.getWorld().isBlockModifiable(player, event.getPos())
                    || !player.canPlayerEdit(event.getPos(), event.getFace(), event.getItemStack())
                    || MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(event.getWorld(), event.getPos(),
                        event.getWorld().getBlockState(event.getPos()), player));
        }
        if (denied) { event.setCanceled(true); event.setCancellationResult(EnumActionResult.FAIL); }
    }
    @SubscribeEvent public static void logout(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) StoredTools.forget((EntityPlayerMP) event.player);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void use(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || REENTERING.get() || event.getHand() != EnumHand.MAIN_HAND
                || !event.getEntityPlayer().isSneaking() || !Tools.active(event.getItemStack())) return;
        Block block = event.getWorld().getBlockState(event.getPos()).getBlock();
        if (!knownTarget(block) || event.getItemStack().getItem() != WrenchBridge.WRENCH && ownTarget(event.getItemStack(), block)) return;
        if (interactionDenied(event)) { event.setCanceled(true); event.setCancellationResult(EnumActionResult.FAIL); return; }
        event.setCanceled(true);
        if (event.getWorld().isRemote) { event.setCancellationResult(EnumActionResult.SUCCESS); return; }
        EntityPlayerMP player = (EntityPlayerMP) event.getEntityPlayer();
        if (player.isSpectator() || !player.capabilities.allowEdit || !event.getWorld().isBlockModifiable(player, event.getPos())
                || !player.canPlayerEdit(event.getPos(), event.getFace(), event.getItemStack())) {
            event.setCancellationResult(EnumActionResult.FAIL); return;
        }
        ItemStack original = player.getHeldItemMainhand();
        try {
            REENTERING.set(true);
            ItemStack proxy = new ItemStack(WrenchBridge.WRENCH);
            player.setHeldItem(EnumHand.MAIN_HAND, proxy);
            Vec3d hit = event.getHitVec();
            float x = hit == null ? 0.5F : (float) (hit.x - event.getPos().getX());
            float y = hit == null ? 0.5F : (float) (hit.y - event.getPos().getY());
            float z = hit == null ? 0.5F : (float) (hit.z - event.getPos().getZ());
            event.setCancellationResult(player.interactionManager.processRightClickBlock(player, event.getWorld(), proxy,
                    EnumHand.MAIN_HAND, event.getPos(), event.getFace(), x, y, z));
        } catch (RuntimeException error) {
            WrenchBridge.LOG.warn("Wrench protocol failed at {}", event.getPos(), error);
            event.setCancellationResult(EnumActionResult.FAIL);
        } finally {
            player.setHeldItem(EnumHand.MAIN_HAND, original);
            player.inventory.markDirty(); player.inventoryContainer.detectAndSendChanges();
            REENTERING.remove();
        }
    }
}
