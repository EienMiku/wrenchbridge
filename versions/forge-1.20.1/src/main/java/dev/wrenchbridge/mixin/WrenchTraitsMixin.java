package dev.wrenchbridge.mixin;

import dev.wrenchbridge.UniversalWrenchItem;
import dev.wrenchbridge.WrenchContext;
import dev.wrenchbridge.core.ToolRules;
import java.util.stream.Stream;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ItemStack.class)
public abstract class WrenchTraitsMixin {
  @Inject(method = "is(Lnet/minecraft/tags/TagKey;)Z", at = @At("HEAD"), cancellable = true)
  private void wrenchbridge$traits(TagKey<Item> tag, CallbackInfoReturnable<Boolean> result) {
    ItemStack self = (ItemStack) (Object) this;
    if (!ToolRules.WRENCH_TAGS.contains(tag.location().toString())) return;
    if (WrenchContext.forced(self)) result.setReturnValue(true);
    else if (self.getItem() instanceof UniversalWrenchItem) result.setReturnValue(false);
  }

  @Inject(method = "getTags", at = @At("RETURN"), cancellable = true)
  private void wrenchbridge$tagList(CallbackInfoReturnable<Stream<TagKey<Item>>> result) {
    ItemStack self = (ItemStack) (Object) this;
    if (!(self.getItem() instanceof UniversalWrenchItem) && !WrenchContext.forced(self)) return;
    Stream<TagKey<Item>> original =
        result
            .getReturnValue()
            .filter(tag -> !ToolRules.WRENCH_TAGS.contains(tag.location().toString()));
    if (!WrenchContext.forced(self)) result.setReturnValue(original);
    else
      result.setReturnValue(
          Stream.concat(
                  original,
                  ToolRules.WRENCH_TAGS.stream()
                      .map(name -> TagKey.create(Registries.ITEM, new ResourceLocation(name))))
              .distinct());
  }
}
