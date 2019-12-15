package jokrey.utilities.encoder.type_transformer;

import jokrey.utilities.encoder.EncodableAsBytes;
import jokrey.utilities.encoder.EncodableAsString;

/**
 * Allows type transformations from a specific type to some raw type.
 *
 * @author jokrey
 */
public interface TypeToRawTransformer<SF> {
    /**
     * Transforms the given entry into type SF. In a way that allows back-transformation later.
     * This is what the call will default to if the subclass doesn't specify other ways to transform using inherited method overloading.
     *
     * @param entry An Object of supported type T.
     *              Supported types typically include String, Boolean, Byte, Integer, Long and their n dimensional arrays(including primitive 1d arrays).
     *              Additionally typically supports classes implementing EncodableAsString and EncodableAsBytes.
     *              Has to be not null
     * @return entry as type SF.
     * @throws NotTransformableException if entry==null || entry type not supported
     */
    default SF transform(Object entry) throws NotTransformableException {
        if(entry==null)
//            return null;
            throw new NotTransformableException(null);

        //primitive type support
        if(entry instanceof Boolean)
            return transform((Boolean) entry);
        if(entry instanceof boolean[])
            return transform((boolean[]) entry);

        if(entry instanceof Byte)
            return transform((Byte) entry);
        if(entry instanceof byte[])
            return transform((byte[]) entry);
        if(entry instanceof Short)
            return transform((Short) entry);
        if(entry instanceof short[])
            return transform((short[]) entry);
        if(entry instanceof Integer)
            return transform((Integer) entry);
        if(entry instanceof int[])
            return transform((int[]) entry);
        if(entry instanceof Long)
            return transform((Long) entry);
        if(entry instanceof long[])
            return transform((long[]) entry);
    
        if(entry instanceof Float)
            return transform((Float) entry);
        if(entry instanceof float[])
            return transform((float[]) entry);
        if(entry instanceof Double)
            return transform((Double) entry);
        if(entry instanceof double[])
            return transform((double[]) entry);
    
        if(entry instanceof Character)
            return transform((Character) entry);
        if(entry instanceof char[])
            return transform((char[]) entry);

        //support for any kind of nested arrays
        if(entry instanceof Object[])
            return transform((Object[])entry);

        //Special support for the following types:
        if(entry instanceof String)
            return transform((String) entry);
        if(entry instanceof EncodableAsString)
            return transform(((EncodableAsString) entry).getEncodedString());
        if(entry instanceof EncodableAsBytes)
            return transform(((EncodableAsBytes) entry).getEncodedBytes());

        throw new NotTransformableException(entry);
    }

    boolean canTransform(Class<?> o);


    default SF transform(Object[] entry) throws NotTransformableException {
        throw new NotTransformableException(entry, entry.getClass());
    }


    /**@see #transform(Object)*/
    default SF transform(boolean entry) throws NotTransformableException {
        throw new NotTransformableException(entry, boolean.class);
    }
    /**Will unbox and call {@link #transform(boolean)}*/
    default SF transform(Boolean entry) throws NotTransformableException { //required so that array transformation works.
        return transform(entry.booleanValue());
    }
    /**
     * By standard boxes each type and calls {@link #transform(Object)}.
     * That however requires one possibly unnecessary heap allocation, which is why this can and should be overridden.
     * @see #transform(Object)
     */
    default SF transform(boolean[] entry) throws NotTransformableException {
        Boolean[] boxed = new Boolean[entry.length];
        for(int i=0;i<entry.length;i++)
            boxed[i] = entry[i];
        return transform(boxed);
    }


    /**@see #transform(Object)*/
    default SF transform(byte entry) throws NotTransformableException {
        throw new NotTransformableException(entry, byte.class);
    }
    /**Will unbox and call {@link #transform(byte)}*/
    default SF transform(Byte entry) throws NotTransformableException {
        return transform(entry.byteValue());
    }
    /**@see #transform(Object) */
    default SF transform(byte[] entry) throws NotTransformableException {
        Byte[] boxed = new Byte[entry.length];
        for(int i=0;i<entry.length;i++)
            boxed[i] = entry[i];
        return transform(boxed);
    }

    /**@see #transform(Object)*/
    default SF transform(short entry) throws NotTransformableException {
        throw new NotTransformableException(entry, short.class);
    }
    /**Will unbox and call {@link #transform(short)}*/
    default SF transform(Short entry) throws NotTransformableException {
        return transform(entry.shortValue());
    }
    /**@see #transform(Object) */
    default SF transform(short[] entry) throws NotTransformableException {
        Short[] boxed = new Short[entry.length];
        for(int i=0;i<entry.length;i++)
            boxed[i] = entry[i];
        return transform(boxed);
    }

    /**@see #transform(Object)*/
    default SF transform(int entry) throws NotTransformableException {
        throw new NotTransformableException(entry, int.class);
    }
    /**Will unbox and call {@link #transform(int)}*/
    default SF transform(Integer entry) throws NotTransformableException {
        return transform(entry.intValue());
    }
    /**@see #transform(Object) */
    default SF transform(int[] entry) throws NotTransformableException {
        Integer[] boxed = new Integer[entry.length];
        for(int i=0;i<entry.length;i++)
            boxed[i] = entry[i];
        return transform(boxed);
    }

    /**@see #transform(Object)*/
    default SF transform(long entry) throws NotTransformableException {
        throw new NotTransformableException(entry, long.class);
    }
    /**Will unbox and call {@link #transform(long)}*/
    default SF transform(Long entry) throws NotTransformableException {
        return transform(entry.longValue());
    }
    /**@see #transform(Object) */
    default SF transform(long[] entry) throws NotTransformableException {
        Long[] boxed = new Long[entry.length];
        for(int i=0;i<entry.length;i++)
            boxed[i] = entry[i];
        return transform(boxed);
    }
    

    /**@see #transform(Object)*/
    default SF transform(float entry) throws NotTransformableException {
        throw new NotTransformableException(entry, float.class);
    }
    /**Will unbox and call {@link #transform(float)}*/
    default SF transform(Float entry) throws NotTransformableException {
        return transform(entry.floatValue());
    }
    /**@see #transform(Object) */
    default SF transform(float[] entry) throws NotTransformableException {
        Float[] boxed = new Float[entry.length];
        for(int i=0;i<entry.length;i++)
            boxed[i] = entry[i];
        return transform(boxed);
    }
    
    /**@see #transform(Object)*/
    default SF transform(double entry) throws NotTransformableException {
        throw new NotTransformableException(entry, double.class);
    }
    /**Will unbox and call {@link #transform(double)}*/
    default SF transform(Double entry) throws NotTransformableException {
        return transform(entry.doubleValue());
    }
    /**@see #transform(Object) */
    default SF transform(double[] entry) throws NotTransformableException {
        Double[] boxed = new Double[entry.length];
        for(int i=0;i<entry.length;i++)
            boxed[i] = entry[i];
        return transform(boxed);
    }

    /**@see #transform(Object)*/
    default SF transform(char entry) throws NotTransformableException {
        throw new NotTransformableException(entry, char.class);
    }
    /**Will unbox and call {@link #transform(char)}*/
    default SF transform(Character entry) throws NotTransformableException {
        return transform(entry.charValue());
    }
    /**@see #transform(Object) */
    default SF transform(char[] entry) throws NotTransformableException {
        Character[] boxed = new Character[entry.length];
        for(int i=0;i<entry.length;i++)
            boxed[i] = entry[i];
        return transform(boxed);
    }

    /**@see #transform(Object)*/
    default SF transform(String entry) throws NotTransformableException {
        throw new NotTransformableException(entry, entry.getClass());
    }
}
