package jokrey.utilities.encoder.tag_based.serialization.field.annotated;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Annotation used as a marker by {@link ObjectEncoderAnnotation}.
 * Only fields with this marker will be serialized.
 *
 * @author jokrey
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Encode {
}