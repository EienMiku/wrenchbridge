package dev.wrenchbridge;

import java.util.List;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IWorldReader;
import net.minecraft.world.World;

public class UniversalWrenchItem extends Item {
  public UniversalWrenchItem(Properties properties) {
    super(properties);
  }

  @Override
  public boolean doesSneakBypassUse(
      ItemStack stack, IWorldReader level, BlockPos pos, PlayerEntity player) {
    return Targets.resolve(level.getBlockState(pos), level.getBlockEntity(pos))
        != Targets.Kind.UNKNOWN;
  }

  @Override
  public ActionResult<ItemStack> use(World level, PlayerEntity player, Hand hand) {
    ItemStack wrench = player.getItemInHand(hand);
    if (VirtualModes.isNative(wrench) && VirtualModes.hasNativeMenu(wrench)) {
      if (!level.isClientSide
          && player instanceof net.minecraft.entity.player.ServerPlayerEntity serverPlayer)
        VirtualModes.openNativeMenu(serverPlayer, hand);
      return ActionResult.sidedSuccess(wrench, level.isClientSide);
    }
    return ActionResult.pass(wrench);
  }

  @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
  @Override
  public void appendHoverText(
      ItemStack stack, World context, List<ITextComponent> lines, ITooltipFlag flag) {
    lines.add(
        new net.minecraft.util.text.TranslationTextComponent("tooltip.wrenchbridge.dismantle")
            .withStyle(TextFormatting.GRAY));
    lines.add(
        new net.minecraft.util.text.TranslationTextComponent(
                "tooltip.wrenchbridge.modes",
                new net.minecraft.util.text.KeybindTextComponent("key.wrenchbridge.modes")
                    .withStyle(TextFormatting.YELLOW))
            .withStyle(TextFormatting.GRAY));
    lines.add(
        new net.minecraft.util.text.TranslationTextComponent("tooltip.wrenchbridge.native")
            .withStyle(TextFormatting.GRAY));
    if (VirtualModes.isNative(stack)) {
      var id = net.minecraft.util.ResourceLocation.tryParse(VirtualModes.item(stack));
      ITextComponent selection =
          id != null && net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(id)
              ? NativeModeNames.choice(
                  new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id)),
                  VirtualModes.mode(stack))
              : new net.minecraft.util.text.StringTextComponent(VirtualModes.item(stack));
      lines.add(
          new net.minecraft.util.text.TranslationTextComponent(
                  "tooltip.wrenchbridge.profile", selection.copy().withStyle(TextFormatting.AQUA))
              .withStyle(TextFormatting.GRAY));
    }
  }
}
