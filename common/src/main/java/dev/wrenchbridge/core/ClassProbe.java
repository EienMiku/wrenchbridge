package dev.wrenchbridge.core;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/** Uses structural type checks without linking to optional mod classes. */
public final class ClassProbe {
    private ClassProbe() {}
    private static final ClassValue<Set<String>> TYPES = new ClassValue<Set<String>>() {
        @Override protected Set<String> computeValue(Class<?> type) {
            Set<String> names = new HashSet<String>();
            collect(type, names);
            return names;
        }
    };
    private static void collect(Class<?> type, Set<String> names) {
        if (type == null || !names.add(type.getName())) return;
        collect(type.getSuperclass(), names);
        for (Class<?> parent : type.getInterfaces()) collect(parent, names);
    }
    public static boolean is(Object object, String type) {
        return object != null && TYPES.get(object.getClass()).contains(type);
    }
    public static Method method(Class<?> type, String name, Class<?>... params) throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name, params);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }
    public static Method compatibleMethod(Class<?> type, String name, Object... arguments) throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterTypes().length != arguments.length) continue;
                boolean matches = true;
                for (int i = 0; i < arguments.length; i++)
                    if (!method.getParameterTypes()[i].isInstance(arguments[i])) matches = false;
                if (matches) { method.setAccessible(true); return method; }
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }
}
