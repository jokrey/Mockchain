package jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.string;

import jokrey.utilities.encoder.as_union.li.string.LIse;
import jokrey.utilities.encoder.tag_based.TagBasedEncoder;
import jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.thread_safe.SynchronizingTagBasedEncoder;
import jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.LITagEncoder;
import jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.type.transformer.LITypeToStringTransformer;
import jokrey.utilities.encoder.type_transformer.TypeToFromRawTransformer;
import jokrey.utilities.transparent_storage.StorageSystemException;

import java.nio.charset.StandardCharsets;

/**
 * Implementation of a LITagEncoder using String as the StorageFormat
 *
 * NOT THREAD SAFE - and will never become thread safe for performance reasons
 *    use the {@link SynchronizingTagBasedEncoder} for this purpose
 *
 * @author jokrey
 * @see TagBasedEncoder
 */
public class LITagStringEncoder extends LITagEncoder<String> {
    /**
     * Empty Constructor.
     * Initialises all needed internal variables.
     */
    public LITagStringEncoder() {
        this(new LIse());
    }

    /**
     * Initialises the internal storage model with the provided parameter "workingString".
     * Useful if one want's to decode a previously stored encoded string.
     *
     * @param workingString previously encoded string
     */
    public LITagStringEncoder(String workingString) {
        this();
        readFromEncodedString(workingString);
    }

    /**
     * Initialises the internal storage model with the provided custom storage logic.
     *
     *
     * @param used_storage_logic previously encoded string
     */
    public LITagStringEncoder(LIse used_storage_logic) {
        super(used_storage_logic);
    }


    //TYPE CONVERSION


    private TypeToFromRawTransformer<String> transformer = null;
    @Override public TypeToFromRawTransformer<String> getTypeTransformer() {
        if(transformer==null)
            transformer=createTypeTransformer();
        return transformer;
    }
    @Override public TypeToFromRawTransformer<String> createTypeTransformer() {
        return new LITypeToStringTransformer();
    }


    //implementing the EncodableAsString and EncodableAsBytes interface methods



    @Override public byte[] getEncodedBytes() throws StorageSystemException {
        return getEncodedString().getBytes(StandardCharsets.UTF_8);
    }

    @Override public void readFromEncodedBytes(byte[] encoded_bytes) {
        readFromEncodedString(new String(encoded_bytes, StandardCharsets.UTF_8));
    }

    @Override public String getEncodedString() {
        return getEncoded();
    }

    @Override public void readFromEncodedString(String encoded_string) {
        readFromEncoded(encoded_string);
    }
}