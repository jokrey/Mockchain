package jokrey.utilities.encoder.helper;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ReflectionHelper {
    @SuppressWarnings("unchecked")
    public static <T> Class<T> getWrap(Class<T> c) {
        return c.isPrimitive() ? (Class<T>) prim_map.get(c) : c;
    }
    private static final Map<Class<?>, Class<?>> prim_map;
    static {
        Map<Class<?>, Class<?>> m = new HashMap<>();
        m.put(boolean.class, Boolean.class);
        m.put(byte.class, Byte.class);
        m.put(char.class, Character.class);
        m.put(double.class, Double.class);
        m.put(float.class, Float.class);
        m.put(int.class, Integer.class);
        m.put(long.class, Long.class);
        m.put(short.class, Short.class);
        m.put(void.class, Void.class);
        prim_map = Collections.unmodifiableMap(m);
    }
}
