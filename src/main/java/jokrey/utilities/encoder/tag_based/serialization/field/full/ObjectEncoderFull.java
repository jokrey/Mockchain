package jokrey.utilities.encoder.tag_based.serialization.field.full;

import jokrey.utilities.encoder.tag_based.TagBasedEncoder;
import jokrey.utilities.encoder.tag_based.serialization.field.FieldListSupplier;
import jokrey.utilities.encoder.tag_based.serialization.field.ObjectFieldSerializer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Allows java objects to be serialized using TagBasedEncoder's.
 *    It does this by storing every single field (private, public, final, superclass).
 *
 * TODO: Make work with null values. Though that requires an EXTENSIVE rework of TBE itself (which may not be desired, because many languages do not have a null type).
 *
 * @see TagBasedEncoder
 * @author jokrey
 */
public class ObjectEncoderFull {
    /**
     * Method will encode an Object into the provided encoder.
     * For that it will attempt to query every single field that object has(superclass fields and private fields included).
     *     Should a field be ignored, then take a look at annotated serializer.
     *
     * @see ObjectFieldSerializer#serialize(TagBasedEncoder, Object, FieldListSupplier, FieldListSupplier)
     * @param encoder preferably empty (though it should also work if it isn't)
     * @param o java bean object
     * @return param encoder  (why not, and might create nicer call chains)
     * throws IllegalArgumentException if any of the constraints above are not followed
     */
    @SuppressWarnings("unchecked")
    public static TagBasedEncoder serialize(TagBasedEncoder encoder, Object o) {
        return ObjectFieldSerializer.serialize(encoder, o, ObjectEncoderFull::getAllFields, ObjectEncoderFull::getAllFields);
    }

    /**
     * Method will recreate an Object of type T from the provided encoder.
     *
     * @see ObjectFieldSerializer#serialize(TagBasedEncoder, Object, FieldListSupplier, FieldListSupplier)
     * @param encoder preferably previously encoded using {@link #serialize(TagBasedEncoder, Object)}
     * @param c java bean class
     * @return recreated object
     * throws IllegalArgumentException if any of the constraints above are not followed
     */
    @SuppressWarnings("unchecked")
    public static <T> T deserialize(TagBasedEncoder<?> encoder, Class<T> c) {
        return ObjectFieldSerializer.deserialize(encoder, c, ObjectEncoderFull::getAllFields, ObjectEncoderFull::getAllFields);
    }


    //With a little help from:: https://stackoverflow.com/a/2405757
    /**
     * Returns all fields of the class. Superclass fields included.
     * @param type class to search
     * @return fields
     */
    public static List<Field> getAllFields(Class type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            fields.addAll(Arrays.asList(c.getDeclaredFields()));
        }
        return fields;
    }



    //additional shortcut functionality::

    /**
     * @see ObjectEncoderFull ::serialize
     * @throws IllegalAccessException if encoder_class cannot be instantiated
     * @throws InstantiationException if encoder_class cannot be instantiated
     * @return instantiated encoder_class
     */
    public static TagBasedEncoder serialize(Class<? extends TagBasedEncoder> encoder_class, Object o) throws IllegalAccessException, InstantiationException {
        return serialize(encoder_class.newInstance(), o);
    }

    /**
     * @see ObjectEncoderFull ::serialize
     * @throws IllegalAccessException if encoder_class cannot be instantiated
     * @throws InstantiationException if encoder_class cannot be instantiated
     * @return instantiated encoder_class
     */
    public static <T, SF> T deserialize(SF encoded, Class<? extends TagBasedEncoder<SF>> decoder_class, Class<T> c) throws IllegalAccessException, InstantiationException {
        TagBasedEncoder<SF> encoder = decoder_class.newInstance();
        encoder.readFromEncoded(encoded);
        return deserialize(encoder, c);
    }
}
