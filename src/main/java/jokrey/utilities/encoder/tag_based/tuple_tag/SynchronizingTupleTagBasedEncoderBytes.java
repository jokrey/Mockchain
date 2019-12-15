package jokrey.utilities.encoder.tag_based.tuple_tag;

import jokrey.utilities.encoder.tag_based.TagBasedEncoder;
import jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.thread_safe.SynchronizingTagBasedEncoder;
import jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.thread_safe.SynchronizingTagBasedEncoderBytes;
import jokrey.utilities.simple.data_structure.pairs.Pair;

import java.io.InputStream;

/**
 * Thread safe wrapper for TupleTagBasedEncoderBytes
 *
 * @author jokrey
 */
public class SynchronizingTupleTagBasedEncoderBytes<TTBE extends TupleTagBasedEncoderBytes> extends SynchronizingTupleTagBasedEncoder<TTBE, byte[]> implements SynchronizedTupleTagBasedEncoderBytes<TTBE> {
    /** @see SynchronizingTagBasedEncoder#SynchronizingTagBasedEncoder(TagBasedEncoder) */
    public SynchronizingTupleTagBasedEncoderBytes(TTBE delegation) {
        super(delegation);
    }

    @Override public SynchronizingTagBasedEncoderBytes getSubEncoder(String super_tag) {
        return new SynchronizingTagBasedEncoderBytes(delegation.getSubEncoder(super_tag));
    }


    /** {@see LITagBytesEncoder#addEntry(String, InputStream, long)} */
    @Override public boolean addEntry(String super_tag, String tag, InputStream content, long content_length) {
        w.lock();
        try {
            return delegation.addEntry(super_tag, tag, content, content_length);
        } finally {
            w.unlock();
        }
    }
    /** {@see LITagBytesEncoder#addEntry_nocheck(String, InputStream, long)} */
    @Override public SynchronizingTupleTagBasedEncoderBytes<TTBE> addEntry_nocheck(String super_tag, String tag, InputStream content, long content_length) {
        w.lock();
        try {
            delegation.addEntry_nocheck(super_tag, tag, content, content_length);
            return this;
        } finally {
            w.unlock();
        }
    }

    /** NOT THREAD SAFE
     * {@see LITagBytesEncoder#getEntry_asLIStream(String)} */
    @Override public Pair<Long, InputStream> getEntry_asLIStream(String super_tag, String tag) {
        return delegation.getEntry_asLIStream(super_tag, tag);
    }


    @Override public Iterable<TaggedStream> getEntryIterator_stream(String super_tag) {
        return delegation.getEntryIterator_stream(super_tag);
    }
    //most other methods already properly overridden in SynchronizingTagBasedEncoder
}
