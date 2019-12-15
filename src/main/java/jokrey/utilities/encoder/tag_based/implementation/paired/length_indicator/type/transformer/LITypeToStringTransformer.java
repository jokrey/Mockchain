package jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.type.transformer;

import jokrey.utilities.encoder.as_union.li.string.LIse;
import jokrey.utilities.encoder.type_transformer.NotTransformableException;
import jokrey.utilities.encoder.type_transformer.string.TypeToStringTransformer;

import java.lang.reflect.Array;

/**
 * Implementation of {@link TypeToStringTransformer} additionally using lise for array transformation.
 *
 * @author jokrey
 */
public class LITypeToStringTransformer extends TypeToStringTransformer {
    @Override public String transform(Object[] entry) throws NotTransformableException {
        LIse internal = new LIse();
        for (Object o : entry)
            internal.encode(transform(o));
        return internal.getEncodedString();
    }

    @SuppressWarnings("unchecked")
    @Override public <T> T detransform_array(String entry, Class<T> c) throws NotTransformableException {
        String[] raw = new LIse(entry).decodeAll();
        T arr = (T) Array.newInstance(c.getComponentType(), raw.length);
        for (int i = 0; i < raw.length; i++)
            Array.set(arr, i, detransform(raw[i], c.getComponentType()));
        return arr;
    }
}