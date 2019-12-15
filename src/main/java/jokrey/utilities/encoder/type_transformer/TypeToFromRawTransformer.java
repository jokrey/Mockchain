package jokrey.utilities.encoder.type_transformer;

/**
 * A subclass will be required to perform bi directional transformations.
 *
 * A standard unit test for them can be found in {@link TransformerTest}
 * @author jokrey
 */
public interface TypeToFromRawTransformer<SF> extends TypeToRawTransformer<SF>, TypeFromRawTransformer<SF> {

}
