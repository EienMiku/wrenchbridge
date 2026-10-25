package dev.wrenchbridge;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Small, typed native block-property request. No client-supplied tool stacks or arbitrary method names. */
public record NativeBlockActionPacket(ResourceLocation action, boolean offhand, BlockPos pos,
        int baseState, int selectedState) implements CustomPacketPayload {
    public static final Type<NativeBlockActionPacket> TYPE = new Type<>(ResourceLocation.parse("wrenchbridge:native_block_action"));
    public static final StreamCodec<ByteBuf, NativeBlockActionPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64).map(ResourceLocation::parse, ResourceLocation::toString), NativeBlockActionPacket::action,
            ByteBufCodecs.BOOL, NativeBlockActionPacket::offhand,
            BlockPos.STREAM_CODEC, NativeBlockActionPacket::pos,
            ByteBufCodecs.VAR_INT, NativeBlockActionPacket::baseState,
            ByteBufCodecs.VAR_INT, NativeBlockActionPacket::selectedState, NativeBlockActionPacket::new);
    public InteractionHand hand() { return offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND; }
    public static NativeBlockActionPacket of(ResourceLocation action, InteractionHand hand, BlockPos pos, BlockState base, BlockState selected) {
        return new NativeBlockActionPacket(action, hand == InteractionHand.OFF_HAND, pos.immutable(), Block.getId(base), Block.getId(selected));
    }
    @Override public Type<NativeBlockActionPacket> type() { return TYPE; }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(WrenchBridge.NETWORK_VERSION).playToServer(TYPE, CODEC, (packet, context) -> {
            if (context.player() instanceof ServerPlayer player) NativeBlockActions.execute(player, packet);
        });
    }
}
