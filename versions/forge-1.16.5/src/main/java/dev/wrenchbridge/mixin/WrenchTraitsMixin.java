package dev.wrenchbridge.mixin;

import dev.wrenchbridge.*;
import java.util.Set;
import net.minecraft.item.Item;
import net.minecraft.tags.ITag;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public abstract class WrenchTraitsMixin {
  @Inject(method = "is", at = @At("HEAD"), cancellable = true)
  private void wrenchbridge$tag(ITag<Item> tag, CallbackInfoReturnable<Boolean> result) {
    if (tag instanceof ITag.INamedTag
        && WrenchTagKeys.contains(((ITag.INamedTag<?>) tag).getName())) {
      if (WrenchContext.forcedItem(this)) result.setReturnValue(true);
      else if ((Object) this instanceof UniversalWrenchItem) result.setReturnValue(false);
    }
  }

  @Inject(method = "getTags", at = @At("RETURN"), cancellable = true, remap = false)
  private void wrenchbridge$tags(CallbackInfoReturnable<Set<ResourceLocation>> result) {
    if (!WrenchContext.forcedItem(this) && !((Object) this instanceof UniversalWrenchItem)) return;
    Set<ResourceLocation> tags = new java.util.HashSet<>(result.getReturnValue());
    if (WrenchContext.forcedItem(this)) {
      tags.addAll(WrenchTagKeys.ITEMS);
    } else if ((Object) this instanceof UniversalWrenchItem) tags.removeIf(WrenchTagKeys::contains);
    result.setReturnValue(tags);
  }
}
