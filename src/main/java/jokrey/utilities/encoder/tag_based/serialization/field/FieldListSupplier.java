package jokrey.utilities.encoder.tag_based.serialization.field;

import java.lang.reflect.Field;
import java.util.List;

/**
 *INTERNAL USAGE
 * @author jokrey
 */
@FunctionalInterface
public interface FieldListSupplier {
    List<Field> fields(Class c);
}
