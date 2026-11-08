package id.omniforge;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Helper reflection kecil supaya OmniForge tidak butuh dependency compile ke plugin lain. */
final class Refl {
    private Refl() {}

    static Class<?> cls(String plugin, String name) {
        try {
            Plugin p = Bukkit.getPluginManager().getPlugin(plugin);
            ClassLoader cl = p != null ? p.getClass().getClassLoader() : Refl.class.getClassLoader();
            return Class.forName(name, true, cl);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Panggil method (static bila target berupa Class). Melempar exception bila tidak ketemu. */
    static Object call(Object target, String name, Object... args) {
        if (target == null) throw new IllegalStateException("target null untuk " + name);
        boolean stat = target instanceof Class<?>;
        Class<?> c = stat ? (Class<?>) target : target.getClass();
        for (Method m : c.getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
            Object[] conv = convert(m.getParameterTypes(), args);
            if (conv == null) continue;
            try {
                m.setAccessible(true);
                return m.invoke(stat ? null : target, conv);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e.getCause());
            } catch (Exception e) {
                // coba overload lain
            }
        }
        throw new IllegalStateException("method tidak ditemukan: " + c.getSimpleName() + "#" + name);
    }

    static Object tryCall(Object target, String name, Object... args) {
        try {
            return call(target, name, args);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object[] convert(Class<?>[] types, Object[] args) {
        Object[] out = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Class<?> t = types[i];
            Object a = args[i];
            if (a == null) {
                if (t.isPrimitive()) return null;
                out[i] = null;
            } else if (t.isPrimitive()) {
                if (t == boolean.class) {
                    if (!(a instanceof Boolean)) return null;
                    out[i] = a;
                } else if (a instanceof Number n) {
                    if (t == int.class) out[i] = n.intValue();
                    else if (t == long.class) out[i] = n.longValue();
                    else if (t == double.class) out[i] = n.doubleValue();
                    else if (t == float.class) out[i] = n.floatValue();
                    else return null;
                } else return null;
            } else {
                if (!t.isInstance(a)) return null;
                out[i] = a;
            }
        }
        return out;
    }

    /** Ambil field (static bila target = Class), cari sampai superclass. */
    static Object get(Object target, String name) {
        try {
            boolean stat = target instanceof Class<?>;
            Class<?> c = stat ? (Class<?>) target : target.getClass();
            while (c != null) {
                try {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    return f.get(stat ? null : target);
                } catch (NoSuchFieldException e) {
                    c = c.getSuperclass();
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    static Object firstField(Object target, String... names) {
        for (String n : names) {
            Object v = get(target, n);
            if (v != null) return v;
        }
        return null;
    }

    static Object newInstance(Class<?> c, Object... args) {
        for (Constructor<?> k : c.getConstructors()) {
            if (k.getParameterCount() != args.length) continue;
            Object[] conv = convert(k.getParameterTypes(), args);
            if (conv == null) continue;
            try {
                return k.newInstance(conv);
            } catch (Exception ignored) {
            }
        }
        throw new IllegalStateException("constructor tidak cocok: " + c.getSimpleName());
    }

    static Object enumVal(Class<?> c, String name) {
        if (c == null || !c.isEnum()) return null;
        for (Object o : c.getEnumConstants()) {
            if (((Enum<?>) o).name().equalsIgnoreCase(name)) return o;
        }
        return null;
    }
}
