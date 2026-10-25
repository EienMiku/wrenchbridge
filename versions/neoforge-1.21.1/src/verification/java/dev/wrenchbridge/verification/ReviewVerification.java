package dev.wrenchbridge.verification;

import dev.wrenchbridge.*;
import com.google.gson.GsonBuilder;
import dev.wrenchbridge.core.ToolRules;
import dev.wrenchbridge.verification.BorrowReturnProbe;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Regression checks for the server review. This source set is excluded from release JARs. */
public final class ReviewVerification {
    private static volatile int sink;
    private ReviewVerification() {}
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError("review: " + message); }
    static Field field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name); field.setAccessible(true); return field;
    }
    static ItemStack item(String id) { return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id))); }

    public static String verify(ServerLevel level) throws Exception {
        String nativeProxy = NativeProxyVerification.verify(level);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("neoforge", net.neoforged.fml.ModList.get().getModContainerById("neoforge").orElseThrow().getModInfo().getVersion().toString());
        Map<String, String> mods = new LinkedHashMap<>();
        for (String id : List.of("create", "grieflogger", "zxcopt", "ae2", "mekanism", "curios", "supplementaries"))
            net.neoforged.fml.ModList.get().getModContainerById(id).ifPresent(mod -> mods.put(id, mod.getModInfo().getVersion().toString()));
        report.put("mods", mods);
        report.put("tag_semantics_and_membership_microbenchmark", tags());
        var returns = BorrowReturnProbe.run(level);
        check(returns.stream().allMatch(row -> Boolean.TRUE.equals(row.get("native_action_ran")) && Boolean.TRUE.equals(row.get("conserved"))
                && Boolean.TRUE.equals(row.get("post_action_state_preserved"))), "borrowed tool conservation/state");
        report.put("borrowed_returns", returns);
        report.put("request_coalescing", requests(level));
        report.put("native_grieflogger", AuditVerification.verify(level));
        Files.writeString(Path.of("review-verification.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        Object audit = report.get("native_grieflogger");
        return "; review: registry-safe tags, live modes, 10 return fault cases, bounded/coalesced request scans; audit="
                + (audit instanceof List<?> cases ? cases.size() + " native cases" : audit) + nativeProxy;
    }

    private static Map<String, Object> tags() {
        ItemStack held = new ItemStack(WrenchBridge.WRENCH.get());
        var tag = TagKey.create(Registries.ITEM, ResourceLocation.parse("c:tools/wrench"));
        var blockTag = TagKey.create(Registries.BLOCK, tag.location());
        check(!held.is(tag) && !held.getItem().builtInRegistryHolder().is(tag), "universal tag leaked outside use");
        check(!WrenchTagKeys.contains(blockTag), "block registry accepted as an item wrench tag");
        var intrusive = net.minecraft.core.Holder.Reference.createIntrusive(new net.minecraft.core.HolderOwner<net.minecraft.world.item.Item>() {}, held.getItem());
        check(!intrusive.is(tag) && intrusive.tags().count() == 0, "unbound intrusive holder failed during registration");
        WrenchContext.run(InteractionHand.MAIN_HAND, held, held, () -> {
            check(held.is(tag) && held.getItem().builtInRegistryHolder().is(tag), "stack/holder temporary wrench identity");
            check(held.getTags().anyMatch(tag::equals) && held.getItem().builtInRegistryHolder().tags().anyMatch(tag::equals), "tag enumeration");
            check(!BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.STONE).is(blockTag), "forced tool altered a block holder");
            return true;
        });
        check(!held.is(tag), "temporary tag context leaked");
        List<TagKey<?>> tags = new ArrayList<>();
        for (int i = 0; i < 64; i++) tags.add(i % 4 == 0 ? blockTag : TagKey.create(Registries.ITEM, ResourceLocation.parse("review:ordinary_tag_" + i)));
        tags.add(tag);
        for (int i = 0; i < 100_000; i++) { old(tags.get(i % tags.size())); WrenchTagKeys.contains(tags.get(i % tags.size())); }
        var bean = java.lang.management.ManagementFactory.getThreadMXBean();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("semantics", "PASS"); result.put("scope", "membership microbenchmark only; not server MSPT");
        if (bean instanceof com.sun.management.ThreadMXBean allocations && allocations.isThreadAllocatedMemorySupported()) {
            allocations.setThreadAllocatedMemoryEnabled(true);
            int count = 300_000, hits = 0;
            long thread = Thread.currentThread().threadId(), before = allocations.getThreadAllocatedBytes(thread);
            long time = System.nanoTime();
            for (int i = 0; i < count; i++) if (old(tags.get(i % tags.size()))) hits++;
            result.put("old_ns_per_check", (System.nanoTime() - time) / (double) count);
            result.put("old_bytes_per_check", (allocations.getThreadAllocatedBytes(thread) - before) / (double) count);
            sink = hits;
            before = allocations.getThreadAllocatedBytes(thread); time = System.nanoTime(); hits = 0;
            for (int i = 0; i < count; i++) if (WrenchTagKeys.contains(tags.get(i % tags.size()))) hits++;
            result.put("new_ns_per_check", (System.nanoTime() - time) / (double) count);
            result.put("new_bytes_per_check", (allocations.getThreadAllocatedBytes(thread) - before) / (double) count);
            sink = hits;
        }
        return result;
    }
    private static boolean old(TagKey<?> tag) { return ToolRules.WRENCH_TAGS.contains(tag.location().toString()); }

    private static Map<String, Object> requests(ServerLevel level) throws Exception {
        ServerPlayer player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.fromString("8aac9b44-f16b-4dd3-b290-60b3d764a025"), "WBRequestProbe"));
        player.getInventory().clearContent(); player.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        Class<?> fixture = Class.forName("dev.wrenchbridge.verification.VerificationMod");
        Field handlerField = field(fixture, "menuHandler"); Object saved = handlerField.get(null);
        var constructor = handlerField.getType().getDeclaredConstructor(); constructor.setAccessible(true);
        ItemStackHandler handler = (ItemStackHandler) constructor.newInstance(); handlerField.set(null, handler);
        Field reads = field(handlerField.getType(), "reads");
        String id = "wrenchbridge:mode_wrench";
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(WrenchBridge.WRENCH.get()));
            player.getInventory().setItem(8, item("wrenchbridge_verification:menu_bag"));
            handler.setStackInSlot(0, item(id));
            forgetRequests(player); SourceViewPacket.forget(player.getUUID());
            var mode = new ModePacket(id, "CONFIG");
            check(mode(mode, player), "initial mode selection");
            int start = reads.getInt(handler);
            for (int i = 0; i < 1000; i++) check(!mode(mode, player), "duplicate mode action accepted");
            check(reads.getInt(handler) == start, "duplicate modes rescanned container");
            for (int i = 1; i < 8; i++) check(mode(new ModePacket(id, i % 2 == 1 ? "WRENCH" : "CONFIG"), player), "legitimate selection burst rejected");
            start = reads.getInt(handler);
            for (int i = 0; i < 1000; i++) check(!mode(new ModePacket(id, "CONFIG"), player), "mode flood accepted");
            check(reads.getInt(handler) == start, "over-budget modes rescanned container");
            check(catalog(player) != null, "initial catalog");
            start = reads.getInt(handler);
            for (int i = 0; i < 1000; i++) check(catalog(player) == null, "catalog flood accepted");
            check(reads.getInt(handler) == start, "catalog requests rescanned within cadence");
            check(SourceViewPacket.reply(player, new SourceViewPacket.Request(1, List.of(id))) != null, "initial snapshot");
            handler.getStackInSlot(0).setDamageValue(32); start = reads.getInt(handler);
            for (int i = 0; i < 1000; i++) {
                check(SourceViewPacket.reply(player, new SourceViewPacket.Request(100 + i, List.of(id))) == null, "snapshot flood accepted");
                SourceViewPacket.changed(player);
            }
            check(flush(player) == null && reads.getInt(handler) == start, "changed bypassed snapshot cadence");
            Map<?, ?> sessions = (Map<?, ?>) field(SourceViewPacket.class, "SESSIONS").get(null);
            Object session = sessions.get(player.getUUID()); Field captured = field(session.getClass(), "captured");
            captured.setInt(session, captured.getInt(session) - 10);
            var fresh = flush(player);
            check(fresh != null && fresh.token == 1099 && SourceViewPacket.decodeStack(player, fresh.entries.get(0).data).getDamageValue() == 32,
                    "coalesced snapshot did not use latest token/live tool state");
            start = reads.getInt(handler);
            check(flush(player) == null && reads.getInt(handler) == start, "snapshot dirty flag was not cleared");
            handler.setStackInSlot(0, ItemStack.EMPTY);
            @SuppressWarnings("unchecked") Map<UUID, Integer> last = (Map<UUID, Integer>) field(ServerRequests.class, "CATALOG_LAST").get(null);
            last.put(player.getUUID(), last.get(player.getUUID()) - 10);
            var catalog = catalog(player);
            check(catalog != null && catalog.rows().isEmpty(), "coalesced catalog cached removed inventory");
            SourceViewPacket.changed(player);
            Field requested = field(session.getClass(), "last"); requested.setInt(session, requested.getInt(session) - 41);
            check(flush(player) == null && !sessions.containsKey(player.getUUID()), "expired source session retained pending work");
            return Map.of("status", "PASS", "rapid_distinct_mode_selections", 8, "duplicate_mode_packets", 1000,
                    "over_budget_mode_packets", 1000, "coalesced_catalog_requests", 1000, "coalesced_source_requests_and_changes", 1000,
                    "cadence_ticks", 10, "live_latest_state", true);
        } finally {
            forgetRequests(player); SourceViewPacket.forget(player.getUUID());
            player.getInventory().clearContent(); handlerField.set(null, saved);
        }
    }
    private static Object call(Class<?> type, String name, Class<?>[] types, Object... args) throws Exception {
        var method = type.getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(null, args);
    }
    private static boolean mode(ModePacket packet, ServerPlayer player) throws Exception {
        return (boolean) call(ServerRequests.class, "mode", new Class<?>[]{ModePacket.class, ServerPlayer.class}, packet, player);
    }
    private static CatalogPacket.Reply catalog(ServerPlayer player) throws Exception {
        return (CatalogPacket.Reply) call(ServerRequests.class, "catalog", new Class<?>[]{ServerPlayer.class}, player);
    }
    private static SourceViewPacket.Reply flush(ServerPlayer player) throws Exception {
        return (SourceViewPacket.Reply) call(SourceViewPacket.class, "flush", new Class<?>[]{ServerPlayer.class}, player);
    }
    private static void forgetRequests(ServerPlayer player) throws Exception {
        call(ServerRequests.class, "forget", new Class<?>[]{UUID.class}, player.getUUID());
    }
}
