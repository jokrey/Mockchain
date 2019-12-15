package jokrey.utilities.encoder.type_transformer;

import jokrey.utilities.encoder.Encodable;
import jokrey.utilities.encoder.EncodableAsBytes;
import jokrey.utilities.encoder.EncodableAsString;

import java.util.Arrays;
import java.util.List;

/**
 * Allows type transformations from a raw to a specific type. (here called detransformation, because from_transform sounds worse.).
 *
 * Some of this isn't very pretty, but java primitives are not very pretty.
 *
 * @author jokrey
 */
public interface TypeFromRawTransformer<SF> {
    List<Class> SHOULD_SUPPORT_TYPES = Arrays.asList(Boolean.class, boolean.class, boolean[].class, Byte.class, byte.class, byte[].class, Integer.class, int.class, int[].class, Long.class, long.class, long[].class,
                                                Float.class, float.class, float[].class, Double.class, double.class, double[].class, String.class);
    Class[] SHOULD_SUPPORT_ASSIGNABLE = {EncodableAsString.class, EncodableAsBytes.class, Encodable.class};

    /**
     * Back transforms the given type SF into an Object of class c.
     *    uses unchecked casting where it can, so should be fairly fast for most classes.
     *
     * if entry is null, null will be returned.
     * @param entry An Object of supported type T.
     *              Supported types typically include String, Boolean, Byte, Integer, Long and their n dimensional arrays(including primitive 1d arrays).
     *              Additionally typically supports classes implementing {@link EncodableAsString} and {@link EncodableAsBytes}
     *              (though only their implemented versions, and only if they have a no-arg constructor see {@link EncodableAsBytes#createFromEncodedBytes(byte[], Class)} for details ).
     * @return newly created object of type T.
     * @throws NotTransformableException if entry==null || entry type not supported
     */
    @SuppressWarnings("unchecked")
    default <T> T detransform(SF entry, Class<T> c) throws NotTransformableException {
        if(entry==null)
//            return null;
            throw new NotTransformableException(null);
        if(c == Object.class)
            return (T) entry;
        if(c == Boolean.class || c == boolean.class)
            return (T) detransform_boolean(entry);
        if(c == boolean[].class)
            return (T) detransform_booleans(entry);

        if(c == Byte.class || c == byte.class)
            return (T) detransform_byte(entry);
        if(c == byte[].class)
            return (T) detransform_bytes(entry);
        if(c == Short.class || c == short.class)
            return (T) detransform_short(entry);
        if(c == short[].class)
            return (T) detransform_shorts(entry);
        if(c == Integer.class || c == int.class)
            return (T) detransform_int(entry);
        if(c == int[].class)
            return (T) detransform_ints(entry);
        if(c == Long.class || c == long.class)
            return (T) detransform_long(entry);
        if(c == long[].class)
            return (T) detransform_longs(entry);

        if(c == Float.class || c == float.class)
            return (T) detransform_float(entry);
        if(c == float[].class)
            return (T) detransform_floats(entry);
        if(c == Double.class || c == double.class)
            return (T) detransform_double(entry);
        if(c == double[].class)
            return (T) detransform_doubles(entry);

        if(c == Character.class || c == char.class)
            return (T) detransform_char(entry);
        if(c == char[].class)
            return (T) detransform_chars(entry);

        if(Object[].class.isAssignableFrom(c))
            return detransform_array(entry, c);

        //special support:
        if(c == String.class)
            return (T) detransform_string(entry);
        if(EncodableAsString.class.isAssignableFrom(c))
            return (T) EncodableAsString.createFromEncodedString(detransform_string(entry), (Class<? extends EncodableAsString>) c);
        if(EncodableAsBytes.class.isAssignableFrom(c))
            return (T) EncodableAsBytes.createFromEncodedBytes(detransform_bytes(entry), (Class<? extends EncodableAsBytes>) c);
        if(Encodable.class.isAssignableFrom(c))
            return (T) EncodableAsBytes.createFromEncodedBytes(detransform_bytes(entry), (Class)c);

        return detransform_else(entry, c);
    }

    boolean canDetransform(Class<?> o);

    /**@see #detransform(Object, Class)*/
    default<T> T detransform_else(SF entry, Class<T> c) throws NotTransformableException {
        throw new NotTransformableException(entry, c);
    }

    /**@see #detransform(Object, Class)*/
    default<T> T detransform_array(SF entry, Class<T> c) throws NotTransformableException {
        throw new NotTransformableException(entry, c);
    }


    /**@see #detransform(Object, Class)*/
    default Boolean detransform_boolean(SF entry) throws NotTransformableException {
        throw new NotTransformableException(entry, boolean.class);
    }
    /**@see #detransform(Object, Class)*/
    default boolean[] detransform_booleans(SF entry) throws NotTransformableException {
        throw new NotTransformableException(entry, boolean[].class);
    }

    /**@see #detransform(Object, Class)*/
    default Byte detransform_byte(SF entry) throws NotTransformableException {
        throw new NotTransformableException(entry, byte.class);
    }
    /**@see #detransform(Object, Class)*/
    default byte[] detransform_bytes(SF entry) throws NotTransformableException {
        throw new NotTransformableException(entry, byte[].class);
    }

    /**@see #detransform(Object, Class)*/
    default Short detransform_short(SF entry) throws NotTransformableException {
        throw new NotTransformableException(entry, short.class);
    }
    /**@see #detransform(Object, Class)*/
    default short[] detransform_shorts(SF entry) throws NotTransformableException {
        throw new NotTransformableException(entry, short[].class);
    }

    /**@see #detransform(Object, Class)*/
    default Integer detransform_int(SF entry) throws NotTransformableException {
        throw new NotTransformableException(entry, int.class);
    }
    /**@see #detransform(Object, Class)*/
    default int[] detransform_ints(SF entry) throws NotTransformableException {
        throw new NotTransformableException(entry, int[].class);
    }

    /**@see #detransform(Object, Class)*/
    default Long detransform_long(SF entry) throws NotTransformableException {
        throw new NotTransformableException(entry, long.class);
    }
    /**@see #detransform(Object, Class)*/
    default long[] detransform_longs(SF entry) throws NotTransformableException {
        throw new NotTransformableException(entry, long[].class);
    }

    /**@see #detransform(Object, Class)*/
    default Float detransform_float(SF entry) throws NotTransformableException {
        throw new NotTransformableException(entry, float.class);
    }
    /**@see #detransform(Object, Class)*/
    default float[] detransform_floats(SF entry) throws NotTransformableException {
        throw new NotTransformableException(entry, float[].class);
    }

    /**@see #detransform(Object, Class)*/
    default Double detransform_double(SF entry) throws NotTransformableException {
        throw new NotTransformableException(entry, double.class);
    }
    /**@see #detransform(Object, Class)*/
    default double[] detransform_doubles(SF entry) throws NotTransformableException {
        throw new NotTransformableException(entry, double[].class);
    }

    /**@see #detransform(Object, Class)*/
    default Character detransform_char(SF entry) throws NotTransformableException {
        throw new NotTransformableException(entry, char.class);
    }
    /**@see #detransform(Object, Class)*/
    default char[] detransform_chars(SF entry) throws NotTransformableException {
        throw new NotTransformableException(entry, char[].class);
    }

    /**@see #detransform(Object, Class)*/
    default String detransform_string(SF entry) throws NotTransformableException {
        throw new NotTransformableException(entry, String.class);
    }
}
