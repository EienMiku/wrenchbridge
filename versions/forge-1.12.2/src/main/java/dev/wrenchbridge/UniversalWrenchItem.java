package dev.wrenchbridge;

import java.util.List;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraft.world.IBlockAccess;

public class UniversalWrenchItem extends Item {
    public UniversalWrenchItem() {
        setRegistryName(WrenchBridge.ID, "universal_wrench");
        setTranslationKey("wrenchbridge.universal_wrench");
        setMaxStackSize(1); setCreativeTab(CreativeTabs.TOOLS);
    }
    @Override public boolean doesSneakBypassUse(ItemStack stack, IBlockAccess world, BlockPos pos, EntityPlayer player) {
        return BridgeEvents.knownTarget(world.getBlockState(pos).getBlock());
    }
    @Override public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        if (world.isRemote) dev.wrenchbridge.client.ClientHooks.open();
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }
    @Override public void addInformation(ItemStack stack, World world, List<String> lines, ITooltipFlag flag) {
        lines.add(I18n.translateToLocal("tooltip.wrenchbridge.dismantle"));
        lines.add(I18n.translateToLocal("tooltip.wrenchbridge.modes"));
        lines.add(I18n.translateToLocal("tooltip.wrenchbridge.native"));
    }
}
