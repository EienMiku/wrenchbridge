package dev.wrenchbridge.mixin;

import dev.wrenchbridge.WrenchContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(World.class)
public abstract class ServerFeedbackMixin {
  @ModifyVariable(
      method = {
        "playSound(Lnet/minecraft/entity/player/EntityPlayer;DDDLnet/minecraft/util/SoundEvent;Lnet/minecraft/util/SoundCategory;FF)V",
        "playEvent(Lnet/minecraft/entity/player/EntityPlayer;ILnet/minecraft/util/math/BlockPos;I)V"
      },
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0)
  private EntityPlayer wrenchbridge$actor(EntityPlayer p) {
    return WrenchContext.feedbackExclusion(p);
  }
}
