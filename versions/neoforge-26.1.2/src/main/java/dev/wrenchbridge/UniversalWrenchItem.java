package dev.wrenchbridge;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.neoforged.neoforge.common.ItemAbility;

public final class UniversalWrenchItem extends Item {
  public UniversalWrenchItem(Properties properties) {
    super(properties);
  }

  @Override
  public boolean canPerformAction(
      net.minecraft.world.item.ItemInstance instance, ItemAbility ability) {
    return instance instanceof ItemStack stack
        && WrenchContext.forced(stack)
        && (ability == ItemAbility.get("wrench_dismantle")
            || ability == ItemAbility.get("wrench_rotate")
            || ability == ItemAbility.get("wrench_connect"));
  }

  @Override
  public boolean doesSneakBypassUse(
      ItemStack stack, LevelReader level, BlockPos pos, Player player) {
    return Targets.resolve(level.getBlockState(pos), level.getBlockEntity(pos))
        != Targets.Kind.UNKNOWN;
  }

  @Override
  public InteractionResult use(Level level, Player player, InteractionHand hand) {
    ItemStack wrench = player.getItemInHand(hand);
    if (VirtualModes.isNative(wrench) && VirtualModes.hasNativeMenu(wrench)) {
      if (!level.isClientSide()
          && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
        VirtualModes.openNativeMenu(serverPlayer, hand);
      return InteractionResult.SUCCESS;
    }
    return InteractionResult.PASS;
  }

  @Override
  public void appendHoverText(
      ItemStack stack,
      TooltipContext context,
      net.minecraft.world.item.component.TooltipDisplay display,
      java.util.function.Consumer<Component> lines,
      TooltipFlag flag) {
    lines.accept(
        Component.translatable("tooltip.wrenchbridge.dismantle").withStyle(ChatFormatting.GRAY));
    lines.accept(
        Component.translatable(
                "tooltip.wrenchbridge.modes",
                Component.keybind("key.wrenchbridge.modes").withStyle(ChatFormatting.YELLOW))
            .withStyle(ChatFormatting.GRAY));
    lines.accept(
        Component.translatable("tooltip.wrenchbridge.native").withStyle(ChatFormatting.GRAY));
    if (VirtualModes.isNative(stack)) {
      var id = net.minecraft.resources.Identifier.tryParse(VirtualModes.item(stack));
      Component selection =
          id != null && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)
              ? NativeModeNames.choice(
                  new ItemStack(
                      net.minecraft.core.registries.BuiltInRegistries.ITEM
                          .get(id)
                          .orElseThrow()
                          .value()),
                  VirtualModes.mode(stack))
              : Component.literal(VirtualModes.item(stack));
      lines.accept(
          Component.translatable(
                  "tooltip.wrenchbridge.profile", selection.copy().withStyle(ChatFormatting.AQUA))
              .withStyle(ChatFormatting.GRAY));
    }
    if (net.neoforged.fml.loading.FMLEnvironment.getDist()
        == net.neoforged.api.distmarker.Dist.CLIENT)
      dev.wrenchbridge.client.SourceViewClient.appendTooltip(stack, context, lines, flag);
  }

  @Override
  public boolean isBarVisible(ItemStack stack) {
    return net.neoforged.fml.loading.FMLEnvironment.getDist()
            == net.neoforged.api.distmarker.Dist.CLIENT
        && dev.wrenchbridge.client.SourceViewClient.barVisible(stack);
  }

  @Override
  public int getBarWidth(ItemStack stack) {
    return net.neoforged.fml.loading.FMLEnvironment.getDist()
            == net.neoforged.api.distmarker.Dist.CLIENT
        ? dev.wrenchbridge.client.SourceViewClient.barWidth(stack)
        : 0;
  }

  @Override
  public int getBarColor(ItemStack stack) {
    return net.neoforged.fml.loading.FMLEnvironment.getDist()
            == net.neoforged.api.distmarker.Dist.CLIENT
        ? dev.wrenchbridge.client.SourceViewClient.barColor(stack)
        : 0;
  }
}
