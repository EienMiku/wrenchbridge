package dev.wrenchbridge.mixin;

import dev.wrenchbridge.*;
import dev.wrenchbridge.core.ToolRules;
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
        && (ToolRules.WRENCH_TAGS.contains(((ITag.INamedTag<?>) tag).getName().toString())
            || ((ITag.INamedTag<?>) tag).getName().toString().equals("mekanism:configurators"))) {
      if (WrenchContext.forcedItem(this)) result.setReturnValue(true);
      else if ((Object) this instanceof UniversalWrenchItem) result.setReturnValue(false);
    }
  }

  @Inject(method = "getTags", at = @At("RETURN"), cancellable = true, remap = false)
  private void wrenchbridge$tags(CallbackInfoReturnable<Set<ResourceLocation>> result) {
    Set<ResourceLocation> tags = new java.util.HashSet<>(result.getReturnValue());
    if (WrenchContext.forcedItem(this)) {
      for (String name : ToolRules.WRENCH_TAGS) tags.add(new ResourceLocation(name));
      tags.add(new ResourceLocation("mekanism:configurators"));
    } else if ((Object) this instanceof UniversalWrenchItem)
      tags.removeIf(
          id ->
              ToolRules.WRENCH_TAGS.contains(id.toString())
                  || id.toString().equals("mekanism:configurators"));
    result.setReturnValue(tags);
  }
}
