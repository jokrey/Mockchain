package jokrey.utilities.encoder.tag_based.serialization.field.annotated;

import jokrey.utilities.encoder.tag_based.TagBasedEncoder;
import jokrey.utilities.encoder.tag_based.serialization.field.ObjectFieldSerializer;
import jokrey.utilities.encoder.tag_based.serialization.field.full.ObjectEncoderFull;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Allows java objects to be serialized using TagBasedEncoder's.
 *    It does this by storing every annotated field (with {@link Encode}).
 *
 * @see TagBasedEncoder
 * @see Encode
 * @author jokrey
 */
public class ObjectEncoderAnnotation {
    /**
     * Method will encode an Object into the provided encoder.
     * For that it will take every field(superclass, private included) annotated with {@link Encode}.
     * The fields that cannot be directly encoded are encoded using the {@link ObjectEncoderFull} logic.
     *
     * The fields have to have Types that are encodable using the chose encoder. If not an Exception will be thrown.
     *
     * @param encoder preferably empty (though it should also work if it isn't)
     * @param o java bean object
     * @return param encoder  (why not, and might create nicer call chains)
     * throws IllegalArgumentException if any of the constraints above are not followed
     */
    @SuppressWarnings("unchecked")
    public static TagBasedEncoder serialize(TagBasedEncoder encoder, Object o) {
        return ObjectFieldSerializer.serialize(encoder, o, ObjectEncoderAnnotation::getAllAnnotatedFields, ObjectEncoderFull::getAllFields);
    }

    /**
     * Method will recreate an Object of type T from the provided encoder.
     *
     * The fields that cannot be directly decoded are decoded using the {@link ObjectEncoderFull} logic.
     * Every field annotated with {@link Encode} of that Object(superclass fields and private fields included) has to exist as a tag within the provided encoder.
     *      (this is guarunteed when encoded using {@link #serialize(TagBasedEncoder, Object)}.
     *
     * @param encoder preferably previously encoded using {@link #serialize(TagBasedEncoder, Object)}
     * @param c java bean class
     * @return recreated object
     * throws IllegalArgumentException if any of the constraints above are not followed
     */
    @SuppressWarnings("unchecked")
    public static <T> T deserialize(TagBasedEncoder<?> encoder, Class<T> c) {
        return ObjectFieldSerializer.deserialize(encoder, c, ObjectEncoderAnnotation::getAllAnnotatedFields, ObjectEncoderFull::getAllFields);
    }


    //With a little help from:: https://stackoverflow.com/a/2405757
    /**
     * Returns all fields of the class. Superclass fields included.
     * @param type class to search
     * @return fields
     */
    public static List<Field> getAllAnnotatedFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for(Field f:c.getDeclaredFields())
                if(f.isAnnotationPresent(Encode.class))
                    fields.add(f);
        }
        return fields;
    }
}
