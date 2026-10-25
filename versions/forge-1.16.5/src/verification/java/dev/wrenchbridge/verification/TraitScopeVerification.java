package dev.wrenchbridge.verification;

import dev.wrenchbridge.*;
import java.util.concurrent.FutureTask;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;

/** Per-use gates and thread isolation; this fixture is excluded from published JARs. */
final class TraitScopeVerification {
  private static void check(boolean value, String message) {
    if (!value) throw new AssertionError("alpha15 traits: " + message);
  }

  private static WrenchTraitScope scope(ItemStack stack) {
    return (WrenchTraitScope) (Object) stack.getItem();
  }

  private static boolean wrench(ItemStack stack) {
    return stack.getItem().getTags().contains(new ResourceLocation("c:tools/wrench"));
  }

  static void verify() throws Exception {
    ItemStack universal = new ItemStack(WrenchBridge.WRENCH.get());
    ItemStack source = new ItemStack(Items.STICK);
    ItemStack other = new ItemStack(Items.APPLE);
    WrenchTraitScope u = scope(universal), s = scope(source), o = scope(other);
    check(
        u.wrenchbridge$traitState() == 1
            && s.wrenchbridge$traitState() == 0
            && o.wrenchbridge$traitState() == 0,
        "idle states");
    check(!wrench(universal) && !wrench(source), "idle tag leakage");
    for (ResourceLocation tag : WrenchTagKeys.ITEMS)
      check(
          WrenchTagKeys.contains(
              new ResourceLocation(new String(tag.getNamespace()), new String(tag.getPath()))),
          "equal name");
    check(
        !WrenchTagKeys.contains(new ResourceLocation("other:tools/wrench")), "namespace collision");
    WrenchContext.run(
        Hand.MAIN_HAND,
        universal,
        universal,
        () -> {
          check(u.wrenchbridge$traitState() == 3 && wrench(universal), "same item counted twice");
          return true;
        });
    IllegalStateException expected = new IllegalStateException("consumed source");
    try {
      WrenchContext.run(
          Hand.MAIN_HAND,
          universal,
          source,
          () -> {
            check(
                u.wrenchbridge$traitState() == 3
                    && s.wrenchbridge$traitState() == 2
                    && o.wrenchbridge$traitState() == 0,
                "participating item states");
            check(wrench(universal) && wrench(source) && !wrench(other), "active tags");
            FutureTask<Boolean> reader =
                new FutureTask<>(() -> !wrench(source) && !wrench(universal));
            new Thread(reader, "wb-trait-isolation").start();
            try {
              check(reader.get(5, java.util.concurrent.TimeUnit.SECONDS), "cross-thread leakage");
            } catch (Exception error) {
              throw new RuntimeException(error);
            }
            WrenchContext.run(
                Hand.OFF_HAND,
                universal,
                other,
                () -> {
                  check(
                      u.wrenchbridge$traitState() == 5
                          && s.wrenchbridge$traitState() == 2
                          && o.wrenchbridge$traitState() == 2,
                      "nested counts");
                  check(wrench(other) && !wrench(source), "nested thread view");
                  return true;
                });
            check(
                u.wrenchbridge$traitState() == 3
                    && o.wrenchbridge$traitState() == 0
                    && wrench(source)
                    && WrenchContext.hand() == Hand.MAIN_HAND,
                "outer restoration");
            source.setCount(0);
            check(!wrench(source), "empty stack became a wrench");
            throw expected;
          });
      throw new AssertionError("exception swallowed");
    } catch (IllegalStateException error) {
      check(error == expected, "wrong failure");
    }
    check(
        u.wrenchbridge$traitState() == 1
            && s.wrenchbridge$traitState() == 0
            && o.wrenchbridge$traitState() == 0
            && WrenchContext.hand() == null,
        "cleanup after consumption/failure");
    check(!wrench(universal) && !wrench(new ItemStack(Items.STICK)), "post-action leakage");
  }
}
