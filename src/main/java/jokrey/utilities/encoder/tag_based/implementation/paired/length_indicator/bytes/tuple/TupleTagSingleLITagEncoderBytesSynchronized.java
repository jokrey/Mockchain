package jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.bytes.tuple;

import jokrey.utilities.encoder.tag_based.SynchronizedTagBasedEncoder;
import jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.thread_safe.SynchronizingTagBasedEncoderBytes;
import jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.bytes.LITagBytesEncoder;
import jokrey.utilities.encoder.tag_based.tuple_tag.SynchronizedTupleTagBasedEncoder;
import jokrey.utilities.encoder.tag_based.tuple_tag.SynchronizedTupleTagBasedEncoderBytes;
import jokrey.utilities.transparent_storage.StorageSystemException;
import jokrey.utilities.transparent_storage.bytes.TransparentBytesStorage;

import java.io.InputStream;

/**
 * Like it's superclass {@link TupleTagSingleLITagEncoderBytes}, but additionally guarantees Thread Safety for all of its standard operations.
 *
 * @author jokrey
 */
public class TupleTagSingleLITagEncoderBytesSynchronized extends TupleTagSingleLITagEncoderBytes implements SynchronizedTupleTagBasedEncoderBytes<TupleTagSingleLITagEncoderBytesSynchronized> {
    public TupleTagSingleLITagEncoderBytesSynchronized() {
        super(new SynchronizingTagBasedEncoderBytes(new LITagBytesEncoder()));
    }
    public TupleTagSingleLITagEncoderBytesSynchronized(TransparentBytesStorage storage) {
        super(new SynchronizingTagBasedEncoderBytes(new LITagBytesEncoder(storage)));
    }

    @Override public boolean addEntry(String super_tag, String tag, byte[] entry) {
        return delegation.addEntry(getSingleTag(super_tag, tag), entry); //nothing more is required, but this has to be atomic... (and since the outer tuple encoder isn't thread safe, just the underlying single encoders are the delegation has to be atomic)
    }
    @Override public boolean addEntry(String super_tag, String tag, InputStream content, long content_length) throws StorageSystemException {
        return delegation.addEntry(getSingleTag(super_tag, tag), content, content_length); //nothing more is required, but this has to be atomic... (and since the outer tuple encoder isn't thread safe, just the underlying single encoders are the delegation has to be atomic)
    }

    @SuppressWarnings("unchecked")
    @Override public <R> R doReadLocked(String super_tag, SynchronizedTupleTagBasedEncoder.LockedAction<TupleTagSingleLITagEncoderBytesSynchronized, R> action) {
        return ((SynchronizedTagBasedEncoder<byte[]>)delegation).doReadLocked(
                encoder -> action.doLocked(super_tag, TupleTagSingleLITagEncoderBytesSynchronized.this)
        );
    }
    @SuppressWarnings("unchecked")
    @Override public <R> R doWriteLocked(String super_tag, SynchronizedTupleTagBasedEncoder.LockedAction<TupleTagSingleLITagEncoderBytesSynchronized, R> action) {
        return ((SynchronizedTagBasedEncoder<byte[]>)delegation).doWriteLocked(
                encoder -> action.doLocked(super_tag, TupleTagSingleLITagEncoderBytesSynchronized.this)
        );
    }
}
