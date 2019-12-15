package jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.bytes.tuple;

import jokrey.utilities.encoder.tag_based.TagBasedEncoder;
import jokrey.utilities.encoder.tag_based.TagBasedEncoderBytes;
import jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.bytes.LITagBytesEncoder;
import jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.type.transformer.LITypeToBytesTransformer;
import jokrey.utilities.encoder.tag_based.tuple_tag.TupleTagBasedEncoder;
import jokrey.utilities.encoder.tag_based.tuple_tag.TupleTagMultiEncoderBytes;
import jokrey.utilities.encoder.type_transformer.TypeToFromRawTransformer;
import jokrey.utilities.simple.data_structure.pairs.Pair;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of {@link TupleTagMultiEncoderBytes} using specifically a multitude of {@link LITagBytesEncoder}s to store the tuple tags.
 *
 * @author jokrey
 */
public class TupleTagMultiLIEncodersBytes extends TupleTagMultiEncoderBytes {
    public TagBasedEncoderBytes getNewEncoderFor(String super_tag) {
        //cleaning up old encoders is not possible, because their data would be lost forever..
        return new LITagBytesEncoder();
    }
    @Override public TypeToFromRawTransformer<byte[]> createTypeTransformer() {
        return new LITypeToBytesTransformer();
    }
    @Override public Map<String, Pair<TagBasedEncoder<byte[]>, Long>> createMap() {
        return new HashMap<>();
    }

    @Override public TupleTagBasedEncoder<byte[]> clear() {
        encoders.clear();
        return this;
    }
}
