package jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.serialization;

import jokrey.utilities.encoder.tag_based.TagBasedEncoder;
import jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.bytes.LITagBytesEncoder;
import jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.string.LITagStringEncoder;
import jokrey.utilities.encoder.tag_based.serialization.field.annotated.ObjectEncoderAnnotation;

/**
 * Shortcut functionality for {@link ObjectEncoderAnnotation} usage with the LI***Encoders..
 *
 * @see LITagBytesEncoder
 * @see LITagStringEncoder
 * @author jokrey
 */
public class LIObjectEncoderAnnotation {
    /**
     * Calls {@link ObjectEncoderAnnotation#serialize(TagBasedEncoder, Object)} with a newly created LITBE.
     * @see ObjectEncoderAnnotation#serialize(TagBasedEncoder, Object)
     * @return encoded/serialized array
     */
    public static byte[] serialize(Object o) {
        return ObjectEncoderAnnotation.serialize(new LITagBytesEncoder(), o).getEncodedBytes();
    }
    /**
     * Calls {@link ObjectEncoderAnnotation#serialize(TagBasedEncoder, Object)} with a newly created LITSE.
     * @see ObjectEncoderAnnotation#serialize(TagBasedEncoder, Object)
     * @return encoded/serialized string
     */
    public static String serialize_as_str(Object o) {
        return ObjectEncoderAnnotation.serialize(new LITagStringEncoder(), o).getEncodedString();
    }

    /**
     * Method will recreate an Object of type T from bytes using LITBE.
     * @see ObjectEncoderAnnotation#deserialize(TagBasedEncoder, Class)
     * @param bytes preferably previously encoded using {@link #serialize(Object)}
     * @param c java bean class
     * @return recreated object
     * throws IllegalArgumentException if any of the constraints above are not followed
     */
    public static <T> T deserialize(byte[] bytes, Class<T> c) {
        return ObjectEncoderAnnotation.deserialize(new LITagBytesEncoder(bytes), c);
    }
    /**
     * Method will recreate an Object of type T from string using LITSE.
     * @see ObjectEncoderAnnotation#deserialize(TagBasedEncoder, Class)
     * @param string preferably previously encoded using {@link #serialize(Object)}
     * @param c java bean class
     * @return recreated object
     * throws IllegalArgumentException if any of the constraints above are not followed
     */
    public static <T> T deserialize(String string, Class<T> c) {
        return ObjectEncoderAnnotation.deserialize(new LITagStringEncoder(string), c);
    }
}
