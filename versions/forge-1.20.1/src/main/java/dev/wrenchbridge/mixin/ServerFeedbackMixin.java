package dev.wrenchbridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.wrenchbridge.WrenchContext;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Restore the actor to native feedback broadcasts only while the bridge owns a server-only action.
 */
@Mixin(value = ServerLevel.class)
public abstract class ServerFeedbackMixin {
  @ModifyVariable(
      method = {
        "playSeededSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
        "playSeededSound(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V"
      },
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0)
  private Player wrenchbridge$includeUnpredictedActor(Player excluded) {
    return WrenchContext.feedbackExclusion(excluded);
  }

  @WrapOperation(
      method =
          "levelEvent(Lnet/minecraft/world/entity/player/Player;ILnet/minecraft/core/BlockPos;I)V",
      at =
          @At(
              value = "INVOKE",
              remap = true,
              target =
                  "Lnet/minecraft/server/players/PlayerList;broadcast(Lnet/minecraft/world/entity/player/Player;DDDDLnet/minecraft/resources/ResourceKey;Lnet/minecraft/network/protocol/Packet;)V"))
  private void wrenchbridge$trackLevelFeedback(
      PlayerList players,
      Player excluded,
      double x,
      double y,
      double z,
      double range,
      ResourceKey<Level> dimension,
      Packet<?> packet,
      Operation<Void> original) {
    WrenchContext.levelFeedback(excluded, x, y, z, range, dimension, packet);
    original.call(players, excluded, x, y, z, range, dimension, packet);
  }
}
