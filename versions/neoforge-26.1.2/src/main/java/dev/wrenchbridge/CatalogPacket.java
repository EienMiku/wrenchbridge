package dev.wrenchbridge;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class CatalogPacket {
  private CatalogPacket() {}

  public record Request() implements CustomPacketPayload {
    static final Type<Request> TYPE = new Type<>(Identifier.parse("wrenchbridge:catalog_request"));
    static final StreamCodec<ByteBuf, Request> CODEC = StreamCodec.unit(new Request());

    @Override
    public Type<Request> type() {
      return TYPE;
    }
  }

  public record Reply(List<String> rows) implements CustomPacketPayload {
    static final Type<Reply> TYPE = new Type<>(Identifier.parse("wrenchbridge:catalog_reply"));
    static final StreamCodec<ByteBuf, Reply> CODEC =
        ByteBufCodecs.stringUtf8(256).apply(ByteBufCodecs.list(256)).map(Reply::new, Reply::rows);

    @Override
    public Type<Reply> type() {
      return TYPE;
    }
  }

  public static void register(RegisterPayloadHandlersEvent event) {
    var registrar = event.registrar("5");
    registrar.playToServer(
        Request.TYPE,
        Request.CODEC,
        (packet, context) -> {
          if (context.player() instanceof ServerPlayer player && !player.isSpectator())
            PacketDistributor.sendToPlayer(player, new Reply(StoredTools.catalog(player)));
        });
    registrar.playToClient(
        Reply.TYPE,
        Reply.CODEC,
        (packet, context) -> dev.wrenchbridge.client.ClientHooks.catalog(packet.rows));
  }
}
