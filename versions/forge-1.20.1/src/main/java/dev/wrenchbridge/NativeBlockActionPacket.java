package dev.wrenchbridge;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

/** A bounded, typed request; the authenticated context is scoped to this synchronous action. */
public record NativeBlockActionPacket(
    ResourceLocation action, boolean offhand, BlockPos pos, int baseState, int selectedState) {
  private static final ThreadLocal<NetworkEvent.Context> CONTEXT = new ThreadLocal<>();

  static NetworkEvent.Context context() {
    return CONTEXT.get();
  }

  public InteractionHand hand() {
    return offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
  }

  public static NativeBlockActionPacket of(
      ResourceLocation action,
      InteractionHand hand,
      BlockPos pos,
      BlockState base,
      BlockState selected) {
    return new NativeBlockActionPacket(
        action,
        hand == InteractionHand.OFF_HAND,
        pos.immutable(),
        Block.getId(base),
        Block.getId(selected));
  }

  public static void send(NativeBlockActionPacket packet) {
    ModePacket.CHANNEL.sendToServer(packet);
  }

  public static void register() {
    ModePacket.CHANNEL
        .messageBuilder(NativeBlockActionPacket.class, 7, NetworkDirection.PLAY_TO_SERVER)
        .encoder(
            (p, b) -> {
              b.writeUtf(p.action.toString(), 64);
              b.writeBoolean(p.offhand);
              b.writeBlockPos(p.pos);
              b.writeVarInt(p.baseState);
              b.writeVarInt(p.selectedState);
            })
        .decoder(
            b ->
                new NativeBlockActionPacket(
                    new ResourceLocation(b.readUtf(64)),
                    b.readBoolean(),
                    b.readBlockPos(),
                    b.readVarInt(),
                    b.readVarInt()))
        .consumerMainThread(
            (p, c) -> {
              NetworkEvent.Context context = c.get();
              if (context.getSender() != null) execute(p, context);
              context.setPacketHandled(true);
            })
        .add();
  }

  public static net.minecraft.world.InteractionResult execute(
      NativeBlockActionPacket packet, NetworkEvent.Context context) {
    ServerPlayer player = context.getSender();
    if (player == null || !player.getServer().isSameThread())
      return net.minecraft.world.InteractionResult.FAIL;
    NetworkEvent.Context previous = CONTEXT.get();
    CONTEXT.set(context);
    try {
      return NativeBlockActions.execute(player, packet);
    } finally {
      if (previous == null) CONTEXT.remove();
      else CONTEXT.set(previous);
    }
  }
}
