package dev.wrenchbridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.wrenchbridge.WrenchContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.IPacket;
import net.minecraft.server.management.PlayerList;
import net.minecraft.util.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Restore the actor to native feedback broadcasts only while the bridge owns a server-only action.
 */
@Mixin(value = ServerWorld.class)
public abstract class ServerFeedbackMixin {
  @ModifyVariable(
      method = {
        "playSound(Lnet/minecraft/entity/player/PlayerEntity;DDDLnet/minecraft/util/SoundEvent;Lnet/minecraft/util/SoundCategory;FF)V",
        "playSound(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/util/SoundEvent;Lnet/minecraft/util/SoundCategory;FF)V"
      },
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0)
  private PlayerEntity wrenchbridge$includeUnpredictedActor(PlayerEntity excluded) {
    return WrenchContext.feedbackExclusion(excluded);
  }

  @WrapOperation(
      method =
          "levelEvent(Lnet/minecraft/entity/player/PlayerEntity;ILnet/minecraft/util/math/BlockPos;I)V",
      at =
          @At(
              value = "INVOKE",
              remap = true,
              target =
                  "Lnet/minecraft/server/management/PlayerList;broadcast(Lnet/minecraft/entity/player/PlayerEntity;DDDDLnet/minecraft/util/RegistryKey;Lnet/minecraft/network/IPacket;)V"))
  private void wrenchbridge$trackLevelFeedback(
      PlayerList players,
      PlayerEntity excluded,
      double x,
      double y,
      double z,
      double range,
      RegistryKey<World> dimension,
      IPacket<?> packet,
      Operation<Void> original) {
    WrenchContext.levelFeedback(excluded, x, y, z, range, dimension, packet);
    original.call(players, excluded, x, y, z, range, dimension, packet);
  }
}
