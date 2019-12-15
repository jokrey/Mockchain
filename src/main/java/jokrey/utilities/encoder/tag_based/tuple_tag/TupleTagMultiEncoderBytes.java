package jokrey.utilities.encoder.tag_based.tuple_tag;

import jokrey.utilities.encoder.tag_based.TagBasedEncoderBytes;
import jokrey.utilities.simple.data_structure.pairs.Pair;
import jokrey.utilities.transparent_storage.StorageSystemException;

import java.io.InputStream;

/**
 * Like {@link TupleTagMultiEncoder}, but additionally support the {@link TupleTagBasedEncoderBytes} interface methods.
 *
 * @author jokrey
 */
public abstract class TupleTagMultiEncoderBytes extends TupleTagMultiEncoder<byte[]> implements TupleTagBasedEncoderBytes {
    @Override public TagBasedEncoderBytes getSubEncoder(String super_tag) {
        return (TagBasedEncoderBytes) super.getSubEncoder(super_tag);
    }
    @Override abstract public TagBasedEncoderBytes getNewEncoderFor(String super_tag);

    @Override public Pair<Long, InputStream> getEntry_asLIStream(String super_tag, String tag) throws StorageSystemException {
        return getSubEncoder(super_tag).getEntry_asLIStream(tag);
    }
    @Override public TupleTagMultiEncoderBytes addEntry_nocheck(String super_tag, String tag, InputStream content, long content_length) throws StorageSystemException {
        TagBasedEncoderBytes sub = getSubEncoder(super_tag);
        sub.addEntry_nocheck(tag, content, content_length);
        return this;
    }
}
