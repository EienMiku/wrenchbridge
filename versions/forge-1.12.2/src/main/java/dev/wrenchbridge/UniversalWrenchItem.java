package dev.wrenchbridge;

import java.util.List;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentKeybind;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class UniversalWrenchItem extends Item {
  public UniversalWrenchItem() {
    setRegistryName(WrenchBridge.ID, "universal_wrench");
    setTranslationKey("wrenchbridge.universal_wrench");
    setMaxStackSize(1);
    setCreativeTab(CreativeTabs.TOOLS);
  }

  @Override
  public boolean doesSneakBypassUse(
      ItemStack stack, IBlockAccess world, BlockPos pos, EntityPlayer player) {
    return BridgeEvents.knownTarget(world.getBlockState(pos).getBlock());
  }

  @Override
  public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
    ItemStack held = player.getHeldItem(hand);
    if (VirtualModes.isNative(held) && VirtualModes.hasNativeMenu(held)) {
      if (!world.isRemote && player instanceof net.minecraft.entity.player.EntityPlayerMP)
        VirtualModes.openNativeMenu(
            (net.minecraft.entity.player.EntityPlayerMP) player, hand, null);
      return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, held);
    }
    return new ActionResult<ItemStack>(EnumActionResult.PASS, held);
  }

  @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
  @Override
  public void addInformation(ItemStack stack, World world, List<String> lines, ITooltipFlag flag) {
    lines.add(TextFormatting.GRAY + I18n.translateToLocal("tooltip.wrenchbridge.dismantle"));
    lines.add(
        new TextComponentTranslation(
                "tooltip.wrenchbridge.modes",
                new TextComponentKeybind("key.wrenchbridge.modes")
                    .setStyle(new Style().setColor(TextFormatting.YELLOW)))
            .setStyle(new Style().setColor(TextFormatting.GRAY))
            .getFormattedText());
    lines.add(TextFormatting.GRAY + I18n.translateToLocal("tooltip.wrenchbridge.native"));
    dev.wrenchbridge.client.SourceViewClient.appendTooltip(stack, world, lines, flag);
  }

  @Override
  public boolean showDurabilityBar(ItemStack stack) {
    return net.minecraftforge.fml.common.FMLCommonHandler.instance().getSide().isClient()
        && dev.wrenchbridge.client.SourceViewClient.barVisible(stack);
  }

  @Override
  public double getDurabilityForDisplay(ItemStack stack) {
    return net.minecraftforge.fml.common.FMLCommonHandler.instance().getSide().isClient()
        ? dev.wrenchbridge.client.SourceViewClient.barFraction(stack)
        : 1;
  }

  @Override
  public int getRGBDurabilityForDisplay(ItemStack stack) {
    return net.minecraftforge.fml.common.FMLCommonHandler.instance().getSide().isClient()
        ? dev.wrenchbridge.client.SourceViewClient.barColor(stack)
        : 0;
  }
}
