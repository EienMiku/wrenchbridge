package dev.wrenchbridge.core;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Only reads mode getters and calls matching, explicitly named enum setters. Never probes use actions. */
public final class NativeModes {
    private NativeModes() {}

    private static Object read(Object item, Object stack) {
        for (String name : new String[]{"getMode", "getWrenchMode", "getCurrentMode", "getState"}) {
            for (Method method : item.getClass().getMethods()) {
                if (!method.getName().equals(name)) continue;
                Class<?>[] params = method.getParameterTypes();
                try {
                    if (params.length == 1 && params[0].isInstance(stack)) return method.invoke(item, stack);
                    if (params.length == 0 && method.getReturnType().isEnum()) return method.invoke(item);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Failure to inspect a mode is not permission to invoke native gameplay code.
                }
            }
        }
        return null;
    }

    public static String current(Object item, Object stack) {
        Object value = read(item, stack);
        return value instanceof Enum ? ((Enum<?>) value).name() : "";
    }

    public static List<String> choices(Object item, Object stack, Object player) {
        Object current = read(item, stack);
        if (!(current instanceof Enum)) return Collections.emptyList();
        if (setter(item, stack, player, current) == null) return Collections.emptyList();
        List<String> values = new ArrayList<String>();
        for (Object value : ((Enum<?>) current).getDeclaringClass().getEnumConstants()) values.add(((Enum<?>) value).name());
        return values;
    }

    public static boolean select(Object item, Object stack, Object player, String name) throws ReflectiveOperationException {
        Object current = read(item, stack);
        if (!(current instanceof Enum)) return false;
        for (Object choice : ((Enum<?>) current).getDeclaringClass().getEnumConstants()) {
            if (!((Enum<?>) choice).name().equals(name)) continue;
            Method setter = setter(item, stack, player, choice);
            if (setter == null) return false;
            Object[] arguments = arguments(setter, stack, player, choice);
            setter.invoke(Modifier.isStatic(setter.getModifiers()) ? null : item, arguments);
            return true;
        }
        return false;
    }

    private static Method setter(Object item, Object stack, Object player, Object mode) {
        for (Method method : item.getClass().getMethods()) {
            if (!method.getName().equals("setMode") && !method.getName().equals("setWrenchMode")
                    && !method.getName().equals("setState")) continue;
            if (arguments(method, stack, player, mode) != null) return method;
        }
        return null;
    }

    private static Object[] arguments(Method method, Object stack, Object player, Object mode) {
        Class<?>[] params = method.getParameterTypes();
        if (params.length < 2 || params.length > 3) return null;
        Object[] values = new Object[params.length];
        boolean hasStack = false, hasMode = false;
        int erasedMode = -1;
        for (int i = 0; i < params.length; i++) {
            Class<?> type = params[i];
            // Generic mode APIs (e.g. Mekanism IRadialModeItem) erase the mode parameter to Object.
            // Resolve concrete stack/player parameters first; an ambiguous Object signature is rejected.
            if (type == Object.class) {
                if (erasedMode >= 0) return null;
                erasedMode = i;
            } else if (type.isInstance(stack) && !hasStack) { values[i] = stack; hasStack = true; }
            else if (type.isInstance(mode) && !hasMode) { values[i] = mode; hasMode = true; }
            else if (player != null && type.isInstance(player)) values[i] = player;
            else return null;
        }
        if (erasedMode >= 0) {
            if (hasMode) return null;
            values[erasedMode] = mode; hasMode = true;
        }
        return hasStack && hasMode ? values : null;
    }
}
