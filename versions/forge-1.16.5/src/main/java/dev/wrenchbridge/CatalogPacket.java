package dev.wrenchbridge;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.PacketDistributor;

public final class CatalogPacket {
    private CatalogPacket() {}
    public static final class Request {}
    public static final class Reply { final List<String> rows; Reply(List<String> rows) { this.rows=rows; } }
    public static void register() {
        net.minecraftforge.fml.network.simple.SimpleChannel channel = ModePacket.CHANNEL;
        channel.messageBuilder(Request.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder((packet, buffer) -> {}).decoder(buffer -> new Request())
                .consumer((packet, context) -> {
                    context.get().enqueueWork(() -> {
                    net.minecraft.entity.player.ServerPlayerEntity player = context.get().getSender();
                    if (player != null && !player.isSpectator()) channel.send(PacketDistributor.PLAYER.with(() -> player), new Reply(StoredTools.catalog(player)));
                    }); context.get().setPacketHandled(true);
                }).add();
        channel.messageBuilder(Reply.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((packet, buffer) -> { buffer.writeVarInt(packet.rows.size()); for (String row : packet.rows) buffer.writeUtf(row, 256); })
                .decoder(buffer -> new Reply(read(buffer)))
                .consumer((packet, context) -> {
                    context.get().enqueueWork(() -> {
                    dev.wrenchbridge.client.ClientHooks.catalog(packet.rows); }); context.get().setPacketHandled(true);
                }).add();
    }
    private static List<String> read(PacketBuffer buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > 256) throw new IllegalArgumentException("Invalid wrench catalog size");
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < size; i++) rows.add(buffer.readUtf(256));
        return rows;
    }
    public static void request() { ModePacket.CHANNEL.sendToServer(new Request()); }
}
