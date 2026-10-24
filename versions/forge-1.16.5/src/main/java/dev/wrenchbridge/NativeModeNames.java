package dev.wrenchbridge;

import java.lang.reflect.Method;
import java.util.Locale;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;

/** Keeps native translatable components intact, including their arguments and styles. */
public final class NativeModeNames {
  private NativeModeNames() {}

  public static ITextComponent choice(ItemStack tool, String mode) {
    return mode.isEmpty()
        ? tool.getHoverName().copy()
        : new net.minecraft.util.text.TranslationTextComponent(
            "screen.wrenchbridge.entry", tool.getHoverName(), mode(tool, mode));
  }

  public static ITextComponent mode(ItemStack tool, String name) {
    Enum<?> option = option(tool, name);
    if (option != null) {
      for (String accessor :
          new String[] {"getTextComponent", "getDisplayName", "getTranslatedName", "sliceName"}) {
        try {
          Object value = option.getDeclaringClass().getMethod(accessor).invoke(option);
          if (value instanceof ITextComponent text) return text.copy();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
      }
      for (String accessor :
          new String[] {"getTranslationKey", "getLangKey", "getTranslationName"}) {
        try {
          Object value = option.getDeclaringClass().getMethod(accessor).invoke(option);
          if (value instanceof String key && key.contains("."))
            return new net.minecraft.util.text.TranslationTextComponent(key);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
      }
    }
    return new net.minecraft.util.text.TranslationTextComponent(
        "mode.wrenchbridge." + name.toLowerCase(Locale.ROOT));
  }

  private static Enum<?> option(ItemStack stack, String name) {
    Object item = stack.getItem();
    for (String accessor :
        new String[] {"getMode", "getWrenchMode", "getCurrentMode", "getState"}) {
      for (Method method : item.getClass().getMethods()) {
        if (!method.getName().equals(accessor)) continue;
        Class<?>[] parameters = method.getParameterTypes();
        try {
          Object value;
          if (parameters.length == 1 && parameters[0].isInstance(stack))
            value = method.invoke(item, stack);
          else if (parameters.length == 0 && method.getReturnType().isEnum())
            value = method.invoke(item);
          else continue;
          if (!(value instanceof Enum<?> current)) continue;
          for (Enum<?> candidate : current.getDeclaringClass().getEnumConstants())
            if (candidate.name().equals(name)) return candidate;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
      }
    }
    return null;
  }
}
