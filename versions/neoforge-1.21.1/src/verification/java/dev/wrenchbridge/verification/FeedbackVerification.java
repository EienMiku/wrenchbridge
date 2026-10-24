package dev.wrenchbridge.verification;

import com.google.gson.*;
import dev.wrenchbridge.*;
import dev.wrenchbridge.core.ClassProbe;
import dev.wrenchbridge.core.NativeModes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Compare the actual native feedback emitters with main/offhand simulated actions. */
public final class FeedbackVerification {
    public record Sample(String tool, String block, String mode, boolean sneak) {}
    public static final List<Sample> SAMPLES = List.of(
            new Sample("ae2:network_tool", "ae2:interface", "", true),
            new Sample("ae2:certus_quartz_wrench", "ae2:interface", "", true),
            new Sample("mekanism:configurator", "mekanism:basic_energy_cube", "WRENCH", true),
            new Sample("create:wrench", "create:shaft", "", true),
            new Sample("anvilcraft:anvil_hammer", "anvilcraft:royal_steel_block", "", true),
            new Sample("pneumaticcraft:pneumatic_wrench", "pneumaticcraft:air_compressor", "", true),
            new Sample("prettypipes:wrench", "prettypipes:pipe", "", true),
            new Sample("refinedstorage:wrench", "refinedstorage:disk_drive", "", true),
            new Sample("enderio:yeta_wrench", "enderio:alloy_smelter", "", true),
            new Sample("powah:wrench", "powah:energy_cell_starter", "", true),
            new Sample("rftoolsbase:smartwrench", "rftoolsbase:machine_infuser", "", true),
            new Sample("integrateddynamics:wrench", "integrateddynamics:cable", "DEFAULT", true),
            new Sample("moderndynamics:wrench", "moderndynamics:item_pipe", "", true),
            new Sample("pipez:wrench", "pipez:item_pipe", "", true),
            new Sample("supplementaries:wrench", "minecraft:white_glazed_terracotta", "", false));
    private FeedbackVerification() {}
    private static final class Capture {
        final List<String> cues = new ArrayList<>();
        int actorExcluded;
        void packet(net.minecraft.world.entity.player.Player excluded, Packet<?> packet) {
            String cue = null;
            if (packet instanceof ClientboundSoundPacket sound) cue = "sound:" + sound.getSound().value().getLocation() + ":" + sound.getSource();
            if (packet instanceof ClientboundSoundEntityPacket sound) cue = "entity_sound:" + sound.getSound().value().getLocation() + ":" + sound.getSource();
            if (packet instanceof ClientboundLevelEventPacket event) cue = "level_event:" + event.getType();
            if (cue != null) { cues.add(cue); if (excluded != null && !(packet instanceof ClientboundLevelEventPacket)) actorExcluded++; }
        }
    }
    public static String verify(ServerLevel level) throws Exception {
        if (!net.neoforged.fml.ModList.get().isLoaded("mekanism")) return "";
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
        BlockPos pos = new BlockPos(18, 110, 0);
        player.moveTo(20.5, 110, 0.5, 90, 20);
        var rows = new JsonArray();
        var oldPackets = SoundObserver.listener; var oldNative = SoundObserver.nativeListener;
        try {
            for (Sample sample : SAMPLES) {
                Capture reference = null;
                String nativeState = null;
                int variants = sample.sneak && !sample.tool.startsWith("pipez:") ? 5 : 3;
                for (int attempt = 0; attempt < variants; attempt++) {
                    InteractionHand hand = attempt == 2 || attempt == 4 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                    player.getInventory().clearContent(); StoredTools.forget(player);
                    player.setShiftKeyDown(sample.sneak); player.setPose(sample.sneak ? net.minecraft.world.entity.Pose.CROUCHING : net.minecraft.world.entity.Pose.STANDING);
                    var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(sample.block));
                    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    var initial = block.defaultBlockState();
                    if (sample.tool.equals("pipez:wrench")) {
                        level.setBlockAndUpdate(pos.east(), Blocks.CHEST.defaultBlockState());
                        var east = (net.minecraft.world.level.block.state.properties.BooleanProperty) block.getStateDefinition().getProperty("east");
                        initial = initial.setValue(east, true);
                    }
                    level.setBlockAndUpdate(pos, initial);
                    block.setPlacedBy(level, pos, level.getBlockState(pos), player, new ItemStack(block.asItem()));
                    if (sample.tool.equals("pipez:wrench")) level.setBlockAndUpdate(pos.east(), Blocks.CHEST.defaultBlockState());
                    ItemStack donor = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(sample.tool)));
                    if (!sample.mode.isEmpty()) check(NativeModes.select(donor.getItem(), donor, player, sample.mode), "could not select " + sample);
                    if (sample.tool.startsWith("pneumaticcraft:")) {
                        Object optional = Class.forName("me.desht.pneumaticcraft.api.PNCCapabilities").getMethod("getAirHandler", ItemStack.class).invoke(null, donor);
                        Class.forName("me.desht.pneumaticcraft.api.tileentity.IAirHandler").getMethod("addAir", int.class).invoke(((Optional<?>) optional).orElseThrow(), 5000);
                    }
                    player.getCooldowns().removeCooldown(donor.getItem()); player.getCooldowns().removeCooldown(WrenchBridge.WRENCH.get());
                    ItemStack held = attempt == 0 ? donor : new ItemStack(WrenchBridge.WRENCH.get()); player.setItemInHand(hand, held);
                    if (attempt == 1 || attempt == 2) { player.getInventory().setItem(9, donor); check(VirtualModes.select(player, hand, sample.tool, sample.mode), "missing source " + sample); }
                    var hit = new BlockHitResult(Vec3.atCenterOf(pos).add(sample.tool.equals("pipez:wrench") ? 0.49 : 0, 0, 0), sample.tool.startsWith("supplementaries:") ? Direction.UP : Direction.EAST, pos, false);
                    Capture capture = new Capture(); SoundObserver.listener = capture::packet; SoundObserver.nativeListener = capture.cues::add;
                    try {
                        // This native hammer entry is normally reached through its own client packet.
                        if (attempt == 0 && sample.tool.startsWith("anvilcraft:"))
                            ClassProbe.compatibleMethod(donor.getItem().getClass(), "breakBlock", player, pos, level, donor).invoke(null, player, pos, level, donor);
                        else player.gameMode.useItemOn(player, level, held, hand, hit);
                    } finally { SoundObserver.listener = null; SoundObserver.nativeListener = null; }
                    Collections.sort(capture.cues);
                    String state = level.getBlockState(pos).toString();
                    var row = new JsonObject(); row.addProperty("tool", sample.tool); row.addProperty("target", sample.block);
                    row.addProperty("variant", attempt == 0 ? "native-main" : (attempt < 3 ? "simulated-" : "shared-") + hand.name());
                    row.add("native_feedback", new Gson().toJsonTree(capture.cues)); row.addProperty("excluded_actor_broadcasts", capture.actorExcluded);
                    row.addProperty("state", state); row.addProperty("held_item_preserved", player.getItemInHand(hand) == held); rows.add(row);
                    Files.writeString(Path.of("feedback-verification.json"), new GsonBuilder().setPrettyPrinting().create().toJson(rows));
                    if (attempt == 0) { reference = capture; nativeState = state; }
                    else {
                        check(reference.cues.equals(capture.cues), "feedback mismatch for " + sample.tool + " / " + hand + ": native=" + reference.cues + ", simulated=" + capture.cues);
                        check(nativeState.equals(state), "action mismatch for " + sample.tool + " / " + hand + ": " + nativeState + " != " + state);
                        if (attempt < 3) check(capture.actorExcluded == 0, "simulated feedback still excludes actor for " + sample.tool);
                    }
                    check(player.getItemInHand(hand) == held && !WrenchContext.serverOnly() && WrenchContext.hand() == null, "feedback scope or held item leaked");
                    Files.writeString(Path.of("feedback-verification.json"), new GsonBuilder().setPrettyPrinting().create().toJson(rows));
                }
            }
        } finally {
            SoundObserver.listener = oldPackets; SoundObserver.nativeListener = oldNative;
            player.getInventory().clearContent(); level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()); level.setBlockAndUpdate(pos.east(), Blocks.AIR.defaultBlockState());
        }
        return "; feedback: " + rows.size() + " native/simulated main/offhand comparisons across 15 tools, original sound/particle event parity, actor inclusion and scope cleanup";
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
