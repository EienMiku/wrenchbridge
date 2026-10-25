package dev.wrenchbridge.verification;

import com.google.gson.GsonBuilder;
import dev.wrenchbridge.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.items.IItemHandler;

/** Real native packet invocation, real source storage, and hostile/stale GUI payloads. */
public final class NativeProxyVerification {
  private static final UUID ID = UUID.fromString("3398ff69-3461-41f0-8c28-1a23ba4346c0");
  private static final BlockPos POS = new BlockPos(40, 110, 32);
  private static boolean editDenied, interactDenied, registered;

  private NativeProxyVerification() {}

  private static void check(boolean value, String text) {
    if (!value) throw new AssertionError("native proxy: " + text);
  }

  private static ItemStack item(String id) {
    return new ItemStack(
        net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new ResourceLocation(id)));
  }

  private static void permissions() {
    if (registered) return;
    registered = true;
    MinecraftForge.EVENT_BUS.addListener(
        EventPriority.HIGH,
        false,
        BlockEvent.BreakEvent.class,
        event -> {
          if (editDenied && event.getPlayer().getUUID().equals(ID)) event.setCanceled(true);
        });
    MinecraftForge.EVENT_BUS.addListener(
        EventPriority.HIGH,
        false,
        PlayerInteractEvent.RightClickBlock.class,
        event -> {
          if (interactDenied && event.getEntity().getUUID().equals(ID))
            event.setUseBlock(net.minecraftforge.eventbus.api.Event.Result.DENY);
        });
  }

  private record Source(
      ItemStack held, IItemHandler bag, ItemStack original, InteractionHand hand) {
    ItemStack current(ServerPlayer player) {
      return bag == null ? player.getInventory().getItem(9) : bag.getStackInSlot(0);
    }
  }

  private static Source setup(ServerPlayer player, InteractionHand hand, boolean stored)
      throws Exception {
    player.getInventory().clearContent();
    player.getInventory().selected = 0;
    player.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.SURVIVAL);
    player.moveTo(40.5, 110, 34.5, 180, 0);
    player.setShiftKeyDown(false);
    ItemStack donor = item("create:wrench");
    donor.setHoverName(Component.literal("Native proxy source"));
    IItemHandler handler = null;
    if (stored) {
      ItemStack bag = item("wrenchbridge:verification_bag");
      var field = PortVerification.class.getDeclaredField("BAG_HANDLER");
      field.setAccessible(true);
      handler = (IItemHandler) field.get(null);
      for (int slot = 0; slot < handler.getSlots(); slot++)
        handler.extractItem(slot, Integer.MAX_VALUE, false);
      check(handler.insertItem(0, donor, false).isEmpty(), "could not seed source container");
      player.getInventory().setItem(8, bag);
    } else player.getInventory().setItem(9, donor);
    if (hand == InteractionHand.OFF_HAND)
      player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
    ItemStack held = new ItemStack(WrenchBridge.WRENCH.get());
    player.setItemInHand(hand, held);
    check(VirtualModes.select(player, hand, "create:wrench", ""), "source selection");
    NativeBlockActions.forget(player.getUUID());
    return new Source(held, handler, donor.copy(), hand);
  }

  private static InteractionResult execute(ServerPlayer player, NativeBlockActionPacket packet) {
    try {
      var connection =
          new net.minecraft.network.Connection(
              net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
      connection.setListener(player.connection);
      var constructor =
          net.minecraftforge.network.NetworkEvent.Context.class.getDeclaredConstructor(
              net.minecraft.network.Connection.class,
              net.minecraftforge.network.NetworkDirection.class,
              int.class);
      constructor.setAccessible(true);
      var context =
          constructor.newInstance(
              connection, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER, 7);
      return NativeBlockActionPacket.execute(packet, context);
    } catch (ReflectiveOperationException error) {
      throw new RuntimeException(error);
    }
  }

  private static NativeBlockActionPacket request(
      InteractionHand hand, BlockState before, BlockState selected) {
    return NativeBlockActionPacket.of(
        NativeInputRule.CREATE_ROTATION.action(), hand, POS, before, selected);
  }

  private static void conserved(ServerPlayer player, Source source) {
    check(
        player.getItemInHand(source.hand) == source.held
            && source.held.is(WrenchBridge.WRENCH.get()),
        "physical hand converted");
    check(
        ItemStack.matches(source.original, source.current(player)),
        "source identity, components or count changed");
  }

  public static String verify(ServerLevel level) throws Exception {
    if (!net.minecraftforge.fml.ModList.get().isLoaded("create"))
      return "; native GUI proxy: Create absent";
    permissions();
    ServerPlayer player =
        net.minecraftforge.common.util.FakePlayerFactory.get(
            level, new com.mojang.authlib.GameProfile(ID, "WBNativeProxy"));
    List<String> results = new ArrayList<>();
    BlockState base = Blocks.OAK_LOG.defaultBlockState(),
        selected = base.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
    try {
      for (boolean stored : List.of(false, true))
        for (InteractionHand hand : InteractionHand.values()) {
          Source source = setup(player, hand, stored);
          ItemStack view = NativeToolViews.create(player, source.held, source.current(player));
          check(
              Tools.id(view).equals("create:wrench") && view != source.current(player),
              "generic view projection");
          level.setBlockAndUpdate(POS, base);
          var packet = request(hand, base, selected);
          check(
              execute(player, packet).consumesAction(),
              "native packet refused valid source: " + hand + " stored=" + stored);
          check(level.getBlockState(POS).equals(selected), "native rotation count/state");
          conserved(player, source);
          results.add("native log rotation " + hand + " stored=" + stored);
        }
      Source source = setup(player, InteractionHand.MAIN_HAND, false);
      for (String denial : List.of("edit", "interact")) {
        level.setBlockAndUpdate(POS, base);
        NativeBlockActions.forget(ID);
        editDenied = denial.equals("edit");
        interactDenied = denial.equals("interact");
        try {
          check(
              execute(player, request(source.hand, base, selected)) == InteractionResult.FAIL,
              "permission denial bypass");
        } finally {
          editDenied = interactDenied = false;
        }
        check(level.getBlockState(POS).equals(base), "denied callback ran");
        conserved(player, source);
        results.add("denied " + denial);
      }
      for (String invalid :
          List.of(
              "wrong-mode",
              "missing-source",
              "different-block",
              "non-rotation-property",
              "multiple-properties",
              "stale",
              "far",
              "invalid-id",
              "unknown-action")) {
        source = setup(player, InteractionHand.MAIN_HAND, false);
        level.setBlockAndUpdate(POS, base);
        var packet = request(source.hand, base, selected);
        switch (invalid) {
          case "wrong-mode" ->
              VirtualModes.select(player, source.hand, "wrenchbridge:universal_wrench", "");
          case "missing-source" -> player.getInventory().setItem(9, ItemStack.EMPTY);
          case "different-block" ->
              packet = request(source.hand, base, Blocks.STONE.defaultBlockState());
          case "non-rotation-property", "multiple-properties" -> {
            BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState();
            level.setBlockAndUpdate(POS, stairs);
            BlockState other = stairs.setValue(StairBlock.HALF, Half.TOP);
            if (invalid.equals("multiple-properties"))
              other = other.setValue(StairBlock.FACING, Direction.EAST);
            packet = request(source.hand, stairs, other);
          }
          case "stale" ->
              level.setBlockAndUpdate(
                  POS, base.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
          case "far" ->
              packet =
                  new NativeBlockActionPacket(
                      packet.action(),
                      false,
                      new BlockPos(5000, 110, 5000),
                      packet.baseState(),
                      packet.selectedState());
          case "invalid-id" ->
              packet =
                  new NativeBlockActionPacket(packet.action(), false, POS, -1, Integer.MAX_VALUE);
          case "unknown-action" ->
              packet =
                  new NativeBlockActionPacket(
                      new ResourceLocation("unknown:action"),
                      false,
                      POS,
                      packet.baseState(),
                      packet.selectedState());
        }
        BlockState before = level.getBlockState(POS);
        check(
            execute(player, packet) == InteractionResult.FAIL
                && level.getBlockState(POS).equals(before),
            invalid + " accepted");
        results.add("rejected " + invalid);
      }
      source = setup(player, InteractionHand.MAIN_HAND, false);
      BlockState hopper = Blocks.HOPPER.defaultBlockState();
      level.setBlockAndUpdate(POS, hopper);
      var container = (HopperBlockEntity) level.getBlockEntity(POS);
      container.setItem(0, new ItemStack(Items.GOLD_INGOT, 17));
      check(
          execute(
                  player,
                  request(
                      source.hand, hopper, hopper.setValue(HopperBlock.FACING, Direction.NORTH)))
              .consumesAction(),
          "container rotation refused");
      check(
          level.getBlockEntity(POS) == container && container.getItem(0).getCount() == 17,
          "native commit lost block entity contents");
      results.add("native hopper retains 17 items and block entity");
      source = setup(player, InteractionHand.MAIN_HAND, false);
      BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState();
      level.setBlockAndUpdate(POS, stairs.setValue(StairBlock.WATERLOGGED, true));
      check(
          execute(
                      player,
                      request(
                          source.hand, stairs, stairs.setValue(StairBlock.FACING, Direction.EAST)))
                  .consumesAction()
              && level.getBlockState(POS).getValue(StairBlock.WATERLOGGED),
          "stale unrelated property overwritten");
      results.add("unrelated waterlogged state preserved");
      source = setup(player, InteractionHand.MAIN_HAND, false);
      level.setBlockAndUpdate(POS, base);
      for (int i = 0; i < 8; i++) {
        BlockState now = level.getBlockState(POS);
        check(
            execute(player, request(source.hand, now, now.cycle(RotatedPillarBlock.AXIS)))
                .consumesAction(),
            "legitimate burst rejected");
      }
      for (int i = 0; i < 1000; i++) {
        BlockState now = level.getBlockState(POS);
        check(
            execute(player, request(source.hand, now, now.cycle(RotatedPillarBlock.AXIS)))
                == InteractionResult.FAIL,
            "flood accepted");
      }
      conserved(player, source);
      results.add("8 immediate commits; 1000 excess packets rejected");
      Files.writeString(
          Path.of("native-proxy-server.json"),
          new GsonBuilder()
              .setPrettyPrinting()
              .create()
              .toJson(Map.of("status", "PASS", "cases", results)));
      return "; native GUI proxy: "
          + results.size()
          + " cases, native packet parity, real sources, permissions, property/target bounds,"
          + " contents and flood limit";
    } finally {
      editDenied = interactDenied = false;
      NativeBlockActions.forget(ID);
      player.getInventory().clearContent();
      level.setBlockAndUpdate(POS, Blocks.AIR.defaultBlockState());
    }
  }
}
