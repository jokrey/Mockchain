package jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.type.transformer;

import jokrey.utilities.encoder.as_union.li.bytes.LIbae;
import jokrey.utilities.encoder.type_transformer.NotTransformableException;
import jokrey.utilities.encoder.type_transformer.bytes.TypeToBytesTransformer;

import java.lang.reflect.Array;

/**
 * Implementation of {@link TypeToBytesTransformer} additionally using libae for array transformation.
 *
 * @author jokrey
 */
public class LITypeToBytesTransformer extends TypeToBytesTransformer {

    @Override public byte[] transform(Object[] entry) throws NotTransformableException {
        LIbae internal = new LIbae();
        for (Object o : entry)
            internal.encode(transform(o));
        return internal.getEncodedBytes();
    }

    @SuppressWarnings("unchecked")
    @Override public <T> T detransform_array(byte[] entry, Class<T> c) throws NotTransformableException {
        byte[][] raw = new LIbae(entry).decodeAll();
        T arr = (T) Array.newInstance(c.getComponentType(), raw.length);
        for (int i = 0; i < raw.length; i++)
            Array.set(arr, i, detransform(raw[i], c.getComponentType()));
        return arr;
    }
}