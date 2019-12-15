package jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator;

import jokrey.utilities.encoder.as_union.li.LISearchResult;
import jokrey.utilities.encoder.tag_based.TagBasedEncoderBytes;
import jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.bytes.LITagBytesEncoder;
import jokrey.utilities.simple.data_structure.pairs.Pair;
import jokrey.utilities.transparent_storage.StorageSystemException;
import jokrey.utilities.transparent_storage.bytes.TransparentBytesStorage;

import java.io.InputStream;

/**
 * Version of {@link LITagCachedEncoderBytes} that additionally implements {@link TagBasedEncoderBytes} and supports it's methods.
 *
 * @author jokrey
 */
public class LITagCachedEncoderBytes extends LITagCachedEncoder<byte[]> implements TagBasedEncoderBytes {
    private final LITagBytesEncoder delegation;

    public LITagCachedEncoderBytes(LITagBytesEncoder delegation) {
        super(delegation); //no double storage, but this w
        this.delegation=delegation;
//        fill_search_cache();  // don't do that. cache on demand - much faster
    }


    @Override public Pair<Long, InputStream> getEntry_asLIStream(String tag) throws StorageSystemException {
        LISearchResult sr = search(tag);
        if(sr == null) return null;
        return new Pair<>(sr.entry_end_index-sr.entry_start_index, getRawStorageSystem().substream(sr.entry_start_index, sr.entry_end_index));
    }
    @Override public TagBasedEncoderBytes addEntry_nocheck(String tag, InputStream content, long content_length) throws StorageSystemException {
        long old_length = lie.getRawSize();
        delegation.addEntry_nocheck(tag, content, content_length);
        cache_add(tag, old_length);
        return this;
    }


    //required simple delegation
    @Override public TransparentBytesStorage getRawStorageSystem() {
        return delegation.getRawStorageSystem();
    }
    @Override public Iterable<TaggedStream> getEntryIterator_stream() {
        return delegation.getEntryIterator_stream();
    }
}