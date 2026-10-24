package dev.wrenchbridge.verification.mixin;

import dev.wrenchbridge.verification.SoundObserver;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PlayerList.class, remap = false)
public abstract class SoundBroadcastObserverMixin {
    @Inject(method = "broadcast(Lnet/minecraft/world/entity/player/Player;DDDDLnet/minecraft/resources/ResourceKey;Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
    private void wrenchbridge$observe(Player excluded, double x, double y, double z, double range, ResourceKey<Level> dimension, Packet<?> packet, CallbackInfo info) {
        SoundObserver.broadcast(excluded, packet);
    }
}
