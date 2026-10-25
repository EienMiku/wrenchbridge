package dev.wrenchbridge.mixin;

import dev.wrenchbridge.WrenchTraitScope;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** A zero state lets unrelated holder lookups return before consulting tags or thread locals. */
@Mixin(value = Item.class, remap = false)
public abstract class WrenchItemScopeMixin implements WrenchTraitScope {
  @Unique private volatile int wrenchbridge$traitState;

  @Override
  public final int wrenchbridge$traitState() {
    return wrenchbridge$traitState;
  }

  @Override
  public final synchronized void wrenchbridge$markUniversal() {
    wrenchbridge$traitState |= UNIVERSAL;
  }

  @Override
  public final synchronized void wrenchbridge$retainTraits() {
    wrenchbridge$traitState += 2;
  }

  @Override
  public final synchronized void wrenchbridge$releaseTraits() {
    if (wrenchbridge$traitState < 2)
      throw new IllegalStateException("Unbalanced wrench trait scope");
    wrenchbridge$traitState -= 2;
  }
}
