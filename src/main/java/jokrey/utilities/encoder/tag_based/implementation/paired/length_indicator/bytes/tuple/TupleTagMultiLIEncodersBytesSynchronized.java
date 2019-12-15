package jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.bytes.tuple;

import jokrey.utilities.encoder.tag_based.SynchronizedTagBasedEncoder;
import jokrey.utilities.encoder.tag_based.TagBasedEncoder;
import jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.thread_safe.SynchronizingTagBasedEncoderBytes;
import jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.bytes.LITagBytesEncoder;
import jokrey.utilities.encoder.tag_based.tuple_tag.SynchronizedTupleTagBasedEncoder;
import jokrey.utilities.encoder.tag_based.tuple_tag.SynchronizedTupleTagBasedEncoderBytes;
import jokrey.utilities.simple.data_structure.pairs.Pair;
import jokrey.utilities.transparent_storage.StorageSystemException;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Like it's superclass {@link TupleTagMultiLIEncodersBytes}, but additionally guarantees Thread Safety for all of its standard operations.
 *
 * @author jokrey
 */
public class TupleTagMultiLIEncodersBytesSynchronized extends TupleTagMultiLIEncodersBytes implements SynchronizedTupleTagBasedEncoderBytes<TupleTagMultiLIEncodersBytesSynchronized> {
    public SynchronizingTagBasedEncoderBytes getNewEncoderFor(String super_tag) {
        return new SynchronizingTagBasedEncoderBytes(new LITagBytesEncoder());
    }

    @Override public Map<String, Pair<TagBasedEncoder<byte[]>, Long>> createMap() {
        return new ConcurrentHashMap<>();
    }

    //highly important::
    @Override public boolean addEntry(String super_tag, String tag, byte[] entry) {
        return getSubEncoder(super_tag).addEntry(tag, entry); //nothing more is required, but this has to be atomic... (and since the outer tuple encoder isn't thread safe, just the underlying single encoders are the delegation has to be atomic)
    }
    @Override public boolean addEntry(String super_tag, String tag, InputStream content, long content_length) throws StorageSystemException {
        return getSubEncoder(super_tag).addEntry(tag, content, content_length); //nothing more is required, but this has to be atomic... (and since the outer tuple encoder isn't thread safe, just the underlying single encoders are the delegation has to be atomic)
    }


    @SuppressWarnings("unchecked")
    @Override public <R> R doReadLocked(String super_tag, SynchronizedTupleTagBasedEncoder.LockedAction<TupleTagMultiLIEncodersBytesSynchronized, R> action) {
        return ((SynchronizedTagBasedEncoder<byte[]>)getSubEncoder(super_tag)).doReadLocked(
                encoder -> action.doLocked(super_tag, TupleTagMultiLIEncodersBytesSynchronized.this)
        );
    }
    @SuppressWarnings("unchecked")
    @Override public <R> R doWriteLocked(String super_tag, SynchronizedTupleTagBasedEncoder.LockedAction<TupleTagMultiLIEncodersBytesSynchronized, R> action) {
        return ((SynchronizedTagBasedEncoder<byte[]>)getSubEncoder(super_tag)).doWriteLocked(
                encoder -> action.doLocked(super_tag, TupleTagMultiLIEncodersBytesSynchronized.this)
        );
    }
}
