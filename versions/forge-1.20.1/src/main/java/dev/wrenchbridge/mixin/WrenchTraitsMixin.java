package dev.wrenchbridge.mixin;

import dev.wrenchbridge.UniversalWrenchItem;
import dev.wrenchbridge.WrenchContext;
import dev.wrenchbridge.WrenchTagKeys;
import java.util.stream.Stream;
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
    if (!WrenchTagKeys.contains(tag)) return;
    ItemStack self = (ItemStack) (Object) this;
    if (WrenchContext.forced(self)) result.setReturnValue(true);
    else if (self.getItem() instanceof UniversalWrenchItem) result.setReturnValue(false);
  }

  @Inject(method = "getTags", at = @At("RETURN"), cancellable = true)
  private void wrenchbridge$tagList(CallbackInfoReturnable<Stream<TagKey<Item>>> result) {
    ItemStack self = (ItemStack) (Object) this;
    if (!(self.getItem() instanceof UniversalWrenchItem) && !WrenchContext.forced(self)) return;
    Stream<TagKey<Item>> original =
        result.getReturnValue().filter(tag -> !WrenchTagKeys.contains(tag));
    if (!WrenchContext.forced(self)) result.setReturnValue(original);
    else result.setReturnValue(Stream.concat(original, WrenchTagKeys.ITEMS.stream()).distinct());
  }
}
