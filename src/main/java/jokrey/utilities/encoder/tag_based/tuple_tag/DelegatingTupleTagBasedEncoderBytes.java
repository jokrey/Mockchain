package jokrey.utilities.encoder.tag_based.tuple_tag;

import jokrey.utilities.encoder.tag_based.TagBasedEncoder;
import jokrey.utilities.encoder.tag_based.TagBasedEncoderBytes;
import jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.thread_safe.SynchronizingTagBasedEncoder;
import jokrey.utilities.simple.data_structure.pairs.Pair;

import java.io.InputStream;

/**
 * Most simple delegation wrapper like {@link DelegatingTupleTagBasedEncoder}, but additionally relays operations to {@link TupleTagBasedEncoderBytes}.
 *
 * @author jokrey
 */
public class DelegatingTupleTagBasedEncoderBytes<TTBE extends TupleTagBasedEncoderBytes> extends DelegatingTupleTagBasedEncoder<TTBE, byte[]> implements TupleTagBasedEncoderBytes {
    /** @see SynchronizingTagBasedEncoder#SynchronizingTagBasedEncoder(TagBasedEncoder) */
    public DelegatingTupleTagBasedEncoderBytes(TTBE delegation) {
        super(delegation);
    }


    /** {@see LITagBytesEncoder#addEntry(String, InputStream, long} */
    @Override public boolean addEntry(String super_tag, String tag, InputStream content, long content_length) {
        return delegation.addEntry(super_tag, tag, content, content_length);
    }
    /** {@see LITagBytesEncoder#getEntry_asLIStream(String} */
    @Override public Pair<Long, InputStream> getEntry_asLIStream(String super_tag, String tag) {
        return delegation.getEntry_asLIStream(super_tag, tag);
    }
    /** {@see LITagBytesEncoder#addEntry_nocheck(String, InputStream, long} */
    @Override public DelegatingTupleTagBasedEncoderBytes addEntry_nocheck(String super_tag, String tag, InputStream content, long content_length) {
        delegation.addEntry_nocheck(super_tag, tag, content, content_length);
        return this;
    }

    @Override public TagBasedEncoderBytes getSubEncoder(String super_tag) {
        return delegation.getSubEncoder(super_tag);
    }

    @Override public Iterable<TaggedStream> getEntryIterator_stream(String super_tag) {
        return delegation.getEntryIterator_stream(super_tag);
    }
    //most other methods already properly overridden in SynchronizingTagBasedEncoder
}
