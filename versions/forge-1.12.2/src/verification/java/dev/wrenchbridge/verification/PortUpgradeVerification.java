package dev.wrenchbridge.verification;

import dev.wrenchbridge.*;
import dev.wrenchbridge.core.NativeModes;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * Runtime checks for the cross-version callback and server-work ports; excluded from release JARs.
 */
public final class PortUpgradeVerification {
  private static void check(boolean ok, String message) {
    if (!ok) throw new AssertionError("alpha14 port: " + message);
  }

  private static Object call(Class<?> type, String name, Class<?>[] types, Object... args)
      throws Exception {
    Method m = type.getDeclaredMethod(name, types);
    m.setAccessible(true);
    return m.invoke(null, args);
  }

  private static ItemStack tool() {
    return new ItemStack(
        net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.getValue(
            new ResourceLocation("wrenchbridge:mode_wrench")));
  }

  private static void forget(EntityPlayerMP player) throws Exception {
    call(ServerRequests.class, "forget", new Class<?>[] {UUID.class}, player.getUniqueID());
    SourceViewPacket.forget(player.getUniqueID());
  }

  public static String verify(WorldServer level) throws Exception {
    EntityPlayerMP player =
        net.minecraftforge.common.util.FakePlayerFactory.get(
            level,
            new com.mojang.authlib.GameProfile(
                UUID.fromString("c90980bb-b79d-46a5-be51-2aa08b3e573f"), "WBPortUpgrade"));
    int messageCases = 0;
    try {
      for (EnumHand hand : EnumHand.values()) {
        player.inventory.clear();
        forget(player);
        ItemStack wrench = new ItemStack(WrenchBridge.WRENCH), source = tool();
        player.setHeldItem(hand, wrench);
        player.inventory.setInventorySlotContents(9, source);
        String original = NativeModes.current(source.getItem(), source);
        check(
            VirtualModes.select(player, hand, Tools.id(source), "CONFIG"),
            "native profile selection");
        ItemStack view = NativeToolViews.create(player, wrench, source);
        check(
            !view.isEmpty()
                && view != source
                && NativeModes.current(view.getItem(), view).equals("CONFIG")
                && NativeModes.current(source.getItem(), source).equals(original),
            "read-only native projection");
        NativeToolExecution.use(
            player,
            hand,
            Tools.id(source),
            borrowed -> {
              check(
                  player.getHeldItem(hand) == wrench
                      && NativeModes.current(borrowed.getItem(), borrowed).equals("CONFIG"),
                  "real callback profile/hand");
              borrowed.setItemDamage(2);
              return true;
            },
            false);
        check(
            source.getItemDamage() == 2
                && NativeModes.current(source.getItem(), source).equals(original),
            "real costs or original mode restoration");
        check(
            VirtualModes.select(player, hand, "wrenchbridge:universal_wrench", ""),
            "return to universal");
        check(
            !VirtualModes.isNative(wrench) && VirtualModes.item(wrench).isEmpty(),
            "universal profile retained native identity");
        messageCases++;
        ModePacket request = new ModePacket(Tools.id(source), "CONFIG", hand == EnumHand.OFF_HAND);
        check(
            (Boolean)
                call(
                    ServerRequests.class,
                    "mode",
                    new Class<?>[] {ModePacket.class, EntityPlayerMP.class},
                    request,
                    player),
            "first guarded selection");
        for (int i = 0; i < 1000; i++)
          check(
              !(Boolean)
                  call(
                      ServerRequests.class,
                      "mode",
                      new Class<?>[] {ModePacket.class, EntityPlayerMP.class},
                      request,
                      player),
              "duplicate selection bypassed guard");
        for (int i = 1; i < 8; i++)
          check(
              (Boolean)
                  call(
                      ServerRequests.class,
                      "mode",
                      new Class<?>[] {ModePacket.class, EntityPlayerMP.class},
                      new ModePacket(
                          Tools.id(source),
                          i % 2 == 1 ? "WRENCH" : "CONFIG",
                          hand == EnumHand.OFF_HAND),
                      player),
              "distinct burst selection rejected");
        check(
            !(Boolean)
                call(
                    ServerRequests.class,
                    "mode",
                    new Class<?>[] {ModePacket.class, EntityPlayerMP.class},
                    request,
                    player),
            "over-budget selection accepted");
        check(
            call(ServerRequests.class, "catalog", new Class<?>[] {EntityPlayerMP.class}, player)
                != null,
            "first catalog");
        for (int i = 0; i < 1000; i++)
          check(
              call(ServerRequests.class, "catalog", new Class<?>[] {EntityPlayerMP.class}, player)
                  == null,
              "catalog flood not coalesced");
        java.util.List<String> ids = java.util.Collections.singletonList(Tools.id(source));
        check(
            SourceViewPacket.reply(player, new SourceViewPacket.Request(1, ids)) != null,
            "initial snapshot");
        for (int i = 0; i < 1000; i++) {
          check(
              SourceViewPacket.reply(player, new SourceViewPacket.Request(10 + i, ids)) == null,
              "snapshot flood not coalesced");
          SourceViewPacket.changed(player);
        }
        source.setItemDamage(21);
        Field sessions = SourceViewPacket.class.getDeclaredField("SESSIONS");
        sessions.setAccessible(true);
        Object session = ((Map<?, ?>) sessions.get(null)).get(player.getUniqueID());
        Field captured = session.getClass().getDeclaredField("captured");
        captured.setAccessible(true);
        captured.setInt(session, captured.getInt(session) - 10);
        SourceViewPacket.Reply fresh =
            (SourceViewPacket.Reply)
                call(
                    SourceViewPacket.class, "flush", new Class<?>[] {EntityPlayerMP.class}, player);
        check(
            fresh != null
                && fresh.token == 1009
                && SourceViewPacket.decodeStack(player, fresh.entries.get(0).data).getItemDamage()
                    == 21,
            "latest nonce/live tool lost while coalescing");
        check(
            call(SourceViewPacket.class, "flush", new Class<?>[] {EntityPlayerMP.class}, player)
                == null,
            "snapshot remained dirty");
        forget(player);
      }
      ItemStack wrench = new ItemStack(WrenchBridge.WRENCH);
      player.inventory.clear();
      player.setHeldItem(EnumHand.MAIN_HAND, wrench);
      BlockPos pos = new BlockPos(0, 110, 0);
      RayTraceResult hit =
          new RayTraceResult(
              new net.minecraft.util.math.Vec3d(
                  pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
              net.minecraft.util.EnumFacing.UP,
              pos);
      int[] writes = {0}, permissions = {0};
      for (boolean remove : new boolean[] {false, true}) {
        level.setBlockState(pos, Blocks.STONE.getDefaultState());
        WrenchUseTransaction.use(
            player,
            EnumHand.MAIN_HAND,
            wrench,
            hit,
            () -> {
              try {
                for (int i = 0; i < 2; i++)
                  call(
                      WrenchUseTransaction.class,
                      "permission",
                      new Class<?>[] {
                        EntityPlayerMP.class, World.class, BlockPos.class, Supplier.class
                      },
                      player,
                      level,
                      pos,
                      (Supplier<Boolean>)
                          () -> {
                            permissions[0]++;
                            return true;
                          });
              } catch (Exception error) {
                throw new RuntimeException(error);
              }
              for (int i = 0; i < 2; i++)
                check(
                    WrenchUseTransaction.deferAudit(level, pos, "probe", player, () -> writes[0]++),
                    "audit scope missing");
              if (remove) level.setBlockState(pos, Blocks.AIR.getDefaultState());
              return true;
            });
        check(writes[0] == (remove ? 1 : 0), "audit duplicated or recorded a non-removal");
      }
      check(permissions[0] == 2, "permission preflight repeated inside one action");
      Map<String, Object> report = new LinkedHashMap<>();
      report.put("status", "PASS");
      report.put("hands", 2);
      report.put("duplicate_mode_requests_per_hand", 1000);
      report.put("catalog_requests_per_hand", 1000);
      report.put("snapshot_requests_and_changes_per_hand", 1000);
      report.put("live_latest_snapshot", true);
      report.put("projection_mode_costs_and_physical_hand", true);
      report.put("scoped_permissions_and_audit", true);
      java.nio.file.Files.write(
          java.nio.file.Paths.get("alpha14-port-upgrade.json"),
          new com.google.gson.GsonBuilder()
              .setPrettyPrinting()
              .create()
              .toJson(report)
              .getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return "; alpha14: both-hand native projections/real costs, mode restoration,"
                 + " bounded/coalesced requests, latest snapshot, permission and audit scope";
    } finally {
      forget(player);
      player.inventory.clear();
    }
  }
}
