package jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.thread_safe;

import jokrey.utilities.encoder.tag_based.TagBasedEncoder;
import jokrey.utilities.encoder.tag_based.TagBasedEncoderBytes;
import jokrey.utilities.simple.data_structure.pairs.Pair;
import jokrey.utilities.transparent_storage.bytes.TransparentBytesStorage;

import java.io.InputStream;

/**
 * Thread safe wrapper for TagBasedEncoderBytes.
 *
 * @see SynchronizingTagBasedEncoder
 * @author jokrey
 */
public class SynchronizingTagBasedEncoderBytes extends SynchronizingTagBasedEncoder<byte[]> implements TagBasedEncoderBytes {
    protected final TagBasedEncoderBytes delegation;

    /** @see SynchronizingTagBasedEncoder#SynchronizingTagBasedEncoder(TagBasedEncoder) */
    public SynchronizingTagBasedEncoderBytes(TagBasedEncoderBytes delegation) {
        super(delegation);
        this.delegation = (TagBasedEncoderBytes) super.delegation;
    }


    /** NOT THREAD SAFE
     *   (Acquiring the entry as a stream can never be thread safe.
     *    The read lock would have to be held until the stream is closed.
     *    Handing out this kind of control to just any caller is not a good idea, sorry )
     * {@see LITagBytesEncoder#getEntry_asLIStream(String)} */
    @Override public Pair<Long, InputStream> getEntry_asLIStream(String tag) {
        return delegation.getEntry_asLIStream(tag);
    }
    /** {@see LITagBytesEncoder#addEntry(String, InputStream, long)} */
    @Override public boolean addEntry(String tag, InputStream content, long content_length) {
        w.lock();
        try {
            return delegation.addEntry(tag, content, content_length);
        } finally { w.unlock(); }
    }
    /** {@see LITagBytesEncoder#addEntry_nocheck(String, InputStream, long)} */
    @Override public TagBasedEncoderBytes addEntry_nocheck(String tag, InputStream content, long content_length) {
        w.lock();
        try {
            delegation.addEntry_nocheck(tag, content, content_length);
            return this;
        } finally { w.unlock(); }
    }


    /**
     * USAGE IS NOT THREAD SAFE
     * {@inheritDoc}
     */
    @Override public TransparentBytesStorage getRawStorageSystem() {
        return delegation.getRawStorageSystem();
    }

    /**
     * NOT THREAD SAFE
     * {@inheritDoc}
     */
    @Override public Iterable<TaggedStream> getEntryIterator_stream() {
        return delegation.getEntryIterator_stream();
    }
    //most other methods already properly overridden in SynchronizingTagBasedEncoder
}
