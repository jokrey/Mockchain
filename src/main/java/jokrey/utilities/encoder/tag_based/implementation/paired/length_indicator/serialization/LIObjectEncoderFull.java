package jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.serialization;

import jokrey.utilities.encoder.tag_based.TagBasedEncoder;
import jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.bytes.LITagBytesEncoder;
import jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.string.LITagStringEncoder;
import jokrey.utilities.encoder.tag_based.serialization.field.full.ObjectEncoderFull;

/**
 * Shortcut functionality for {@link ObjectEncoderFull} usage with the LI***Encoders..
 *
 * @see LITagBytesEncoder
 * @see LITagStringEncoder
 * @author jokrey
 */
public class LIObjectEncoderFull {
    /**
     * Calls {@link ObjectEncoderFull#serialize(TagBasedEncoder, Object)} with a newly created LITBE.
     * @see ObjectEncoderFull#serialize(TagBasedEncoder, Object)
     * @return encoded/serialized array
     */
    public static byte[] serialize(Object o) {
        return ObjectEncoderFull.serialize(new LITagBytesEncoder(), o).getEncodedBytes();
    }
    /**
     * Calls {@link ObjectEncoderFull#serialize(TagBasedEncoder, Object)} with a newly created LITSE.
     * @see ObjectEncoderFull#serialize(TagBasedEncoder, Object)
     * @return encoded/serialized string
     */
    public static String serialize_as_str(Object o) {
        return ObjectEncoderFull.serialize(new LITagStringEncoder(), o).getEncodedString();
    }

    /**
     * Method will recreate an Object of type T from bytes using LITBE.
     * @see ObjectEncoderFull#deserialize(TagBasedEncoder, Class)
     * @param bytes preferably previously encoded using {@link #serialize(Object)}
     * @param c java bean class
     * @return recreated object
     * throws IllegalArgumentException if any of the constraints above are not followed
     */
    public static <T> T deserialize(byte[] bytes, Class<T> c) {
        return ObjectEncoderFull.deserialize(new LITagBytesEncoder(bytes), c);
    }
    /**
     * Method will recreate an Object of type T from string using LITSE.
     * @see ObjectEncoderFull#deserialize(TagBasedEncoder, Class)
     * @param string preferably previously encoded using {@link #serialize(Object)}
     * @param c java bean class
     * @return recreated object
     * throws IllegalArgumentException if any of the constraints above are not followed
     */
    public static <T> T deserialize(String string, Class<T> c) {
        return ObjectEncoderFull.deserialize(new LITagStringEncoder(string), c);
    }
}
