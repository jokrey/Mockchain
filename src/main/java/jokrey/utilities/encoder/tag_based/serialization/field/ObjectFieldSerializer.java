package jokrey.utilities.encoder.tag_based.serialization.field;

import jokrey.utilities.encoder.tag_based.TagBasedEncoder;
import jokrey.utilities.encoder.type_transformer.TypeToFromRawTransformer;

import java.lang.reflect.Field;

import static jokrey.utilities.encoder.helper.ReflectionHelper.getWrap;

/**
 * Helper class for all serializers using fields.
 * Not supposed to be used from outside (but can, who cares).
 *
 * Upside to traditional serialization:
 *    Two different versions of the same class can be deserialized.
 *    When a class looses a field it will never be queried from the encoded byte array  - and not re-serialized when stored again.
 *    When a class gains a field it can be initialized to a default value(as every field is) the first time and works normally from there on out.
 *
 * @author jokrey
 */
public class ObjectFieldSerializer {
    /**
     * Method will encode an Object into the provided encoder.
     * Every field supplied by field_supplier are written into the encoder. If a field cannot be serialized directly it will be serialized using a recursive call of this method.
     *
     * The fields have to have Types that are recursively encodable using the chose encoder. If not an Exception will be thrown.
     *
     * @param encoder preferably empty (though it should also work if it isn't)
     * @param o java object
     * @param level0_field_supplier main field supplier
     * @param recursive_field_supplier recursive field supplier for any recursive call
     * @return param encoder  (why not, and might create nicer call chains)
     * throws IllegalArgumentException if any of the constraints above are not followed
     */
    @SuppressWarnings("unchecked")
    public static TagBasedEncoder serialize(TagBasedEncoder encoder, Object o, FieldListSupplier level0_field_supplier, FieldListSupplier recursive_field_supplier) {
        TypeToFromRawTransformer raw_transformer = encoder.getTypeTransformer();
        if(raw_transformer.canTransform(o.getClass())) {
            encoder.addEntry("", raw_transformer.transform(o));
        } else {
            Class c = o.getClass();
            for (Field f : level0_field_supplier.fields(c)) {
                String name = f.getName();
                try {
                    f.setAccessible(true);
                    Object field_value = f.get(o);
                    if(raw_transformer.canTransform(f.getType())) {
                        System.out.println("canTransform f.getType() = " + f.getType());
                        encoder.addEntryT(name, field_value);
                    } else {//try serializing as an object itself
                        encoder.addEntry(name, serialize(encoder.getClass().newInstance(), field_value, recursive_field_supplier, recursive_field_supplier).getEncoded());
                    }
                } catch (IllegalAccessException | InstantiationException e) {
                    throw new IllegalArgumentException("Object could not be serialized, because a field could not be queried or serialized - " + e.getMessage());
                }
            }
        }

        return encoder;
    }

    /**
     * Method will recreate an Object of type T from the provided encoder.
     *
     * Every field supplied by field_supplier are read from the encoder. If a field cannot be deserialized directly it will be deserialized using a recursive call of this method.
     *      (this is guaranteed when encoded using {@link #serialize(TagBasedEncoder, Object, FieldListSupplier, FieldListSupplier)}, and the same field_supplier).
     *  Should a field not be available within the encoder the method simply ignores it
     *      This is done a new version of the same class that gained a field can still be otherwise normally deserialized.
     *      That field should have a sensible default value set in the no-arg constructor.
     *
     * @param encoder preferably previously encoded using {@link #serialize(TagBasedEncoder, Object, FieldListSupplier, FieldListSupplier)}
     * @param c java class
     * @param level0_field_supplier main field supplier
     * @param recursive_field_supplier recursive field supplier for any recursive call
     * @return recreated object
     * throws IllegalArgumentException if any of the constraints above are not followed
     */
    @SuppressWarnings("unchecked")
    public static <T, SF> T deserialize(TagBasedEncoder<SF> encoder, Class<T> c, FieldListSupplier level0_field_supplier, FieldListSupplier recursive_field_supplier) {
        TypeToFromRawTransformer<SF> raw_transformer = encoder.getTypeTransformer();
        if(raw_transformer.canDetransform(c)) {
            return raw_transformer.detransform(encoder.getEntry(""), c);
        } else {
            T inst;
            try {
                inst = c.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new IllegalArgumentException("Object could not be deserialized, because new object of Class c(" + c + ") " +
                        "could not be instantiated - missing no-args constructor? - " + e.getMessage());
            }

            for (Field f : level0_field_supplier.fields(c)) {
                String name = f.getName();
                String type_class_name = getWrap(f.getType()).getName();
                try {
                    Class<?> type_class = Class.forName(type_class_name);
                    f.setAccessible(true);
                    if(raw_transformer.canDetransform(f.getType())) {
                        Object o = encoder.getEntryT(name, type_class);
                        if (o != null)  // newer version of the same class may not have that field
                            f.set(inst, o);
                    } else {
                        TagBasedEncoder sub_decoder = encoder.getClass().newInstance();
                        sub_decoder.readFromEncoded(encoder.getEntry(name));
                        f.set(inst, deserialize(sub_decoder, type_class, recursive_field_supplier, recursive_field_supplier));
                    }
                } catch (ClassNotFoundException | InstantiationException e) {
                    throw new IllegalArgumentException("Object could not be deserialized, because the type for (" + name + ") was not recognised within this system - " + e.getMessage());
                } catch (IllegalAccessException e) {
                    throw new IllegalArgumentException("Object could not be deserialized, because a field could not be set - " + e.getMessage());
                }
            }
            return inst;
        }
    }
}
