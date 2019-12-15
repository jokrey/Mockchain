package jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.cached;

import jokrey.utilities.encoder.tag_based.TagBasedEncoderBytes;
import jokrey.utilities.encoder.tag_based.TagEntryObservable;
import jokrey.utilities.encoder.type_transformer.TypeToFromRawTransformer;
import jokrey.utilities.simple.data_structure.pairs.Pair;
import jokrey.utilities.transparent_storage.bytes.TransparentBytesStorage;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Iterator;

/**
 * Cached wrapper for a TagBasedEncoder.
 *   Should not be used with ByteArrayStorage (cause that would just be redundant storage in RAM)
 *
 * Caches a certain amount of bytes in RAM, for the most recently requested tags.
 *
 * @author jokrey
 */
public class CachedTagBasedEncoderBytes implements TagBasedEncoderBytes {
    private final TagBasedEncoderBytes delegation;
    private final TaggedBytesCache cache;

    /**
     * Constructor
     * @param delegation the actual tbe
     * @param external_change_callback_supplier external change callback supplier, can be null (default is null)
     * @param max_cache_size_in_bytes maximum cached bytes - should really be as big as possible
     * @param max_single_cache_entry_size_in_bytes maximum cached bytes per entry, used to prefer small cached entries
     */
    public CachedTagBasedEncoderBytes(TagBasedEncoderBytes delegation, TagEntryObservable external_change_callback_supplier,
                                      int max_cache_size_in_bytes, int max_single_cache_entry_size_in_bytes) {
        this.delegation = delegation;
        cache = new TaggedBytesCache(max_cache_size_in_bytes, max_single_cache_entry_size_in_bytes);
        cache.setRemoteUpdateListener(external_change_callback_supplier);
    }
    /** @see #CachedTagBasedEncoderBytes(TagBasedEncoderBytes, TagEntryObservable, int, int) */
    public CachedTagBasedEncoderBytes(TagBasedEncoderBytes delegation, int max_cache_size_in_bytes, int max_single_cache_entry_size_in_bytes) {
        this.delegation = delegation;
        cache = new TaggedBytesCache(max_cache_size_in_bytes, max_single_cache_entry_size_in_bytes);
    }
    /** @see #CachedTagBasedEncoderBytes(TagBasedEncoderBytes, TagEntryObservable, int, int) */
    public CachedTagBasedEncoderBytes(TagBasedEncoderBytes delegation, int max_cache_size_in_bytes) {
        this.delegation = delegation;
        cache = new TaggedBytesCache(max_cache_size_in_bytes);
    }
    /** @see #CachedTagBasedEncoderBytes(TagBasedEncoderBytes, TagEntryObservable, int, int) */
    public CachedTagBasedEncoderBytes(TagBasedEncoderBytes delegation) {
        this.delegation = delegation;
        cache = new TaggedBytesCache();
    }
    /** @see #CachedTagBasedEncoderBytes(TagBasedEncoderBytes, TagEntryObservable, int, int) */
    public CachedTagBasedEncoderBytes(TagBasedEncoderBytes delegation, TagEntryObservable external_change_callback_supplier) {
        this.delegation = delegation;
        cache = new TaggedBytesCache();
        cache.setRemoteUpdateListener(external_change_callback_supplier);
    }







    //changed wrapping:::


    @Override public byte[] getEntry(String tag) {
        byte[] entry = cache.getCacheEntry(tag);
        if(entry != null) {
            return entry;
        } else {
            entry = delegation.getEntry(tag);
            cache.local_fill_entry_cache(tag, entry);
            return entry;
        }
    }

    @Override public Pair<Long, InputStream> getEntry_asLIStream(String tag) {
        byte[] entry = cache.getCacheEntry(tag);
        if(entry != null) {
            return new Pair<>((long) entry.length, new ByteArrayInputStream(entry));
        } else {
            //maybe too large for cache and reading it into a byte array first, putting that into the cache and then reading is even less optimal compared to just not caching here...
            return delegation.getEntry_asLIStream(tag);
        }
    }

    @Override public CachedTagBasedEncoderBytes addEntry_nocheck(String tag, byte[] arr) {
        cache.local_update_tag_add(tag, arr); //has to be before!! remote update maybe too fast
        delegation.addEntry_nocheck(tag, arr);
        return this;
    }

    @Override public TagBasedEncoderBytes addEntry_nocheck(String tag, InputStream content, long content_length) {
        if(cache.local_update_tag_add(tag, content, content_length)) {
            delegation.addEntry_nocheck(tag, cache.getCacheEntry(tag));
        } else {
            delegation.addEntry_nocheck(tag, content, content_length);
        }
        return this;
    }

    @Override public byte[] deleteEntry(String tag) {
        byte[] entry = cache.getCacheEntry(tag);
        if(entry != null) {
            deleteEntry_noReturn(tag);
            return entry;
        } else {
            cache.local_update_tag_delete(tag); //has to be before!! remote update maybe too fast
            entry = delegation.deleteEntry(tag);
            return entry;
        }
    }

    @Override public boolean deleteEntry_noReturn(String tag) {
        cache.local_update_tag_delete(tag); //has to be before!! remote update maybe too fast
        delegation.deleteEntry_noReturn(tag);
        return false;
    }


    @Override public CachedTagBasedEncoderBytes clear() {
        cache.reset();
        delegation.clear();
        return this;
    }

    @Override public CachedTagBasedEncoderBytes readFromEncoded(byte[] encoded_raw) {
        cache.reset();
        delegation.readFromEncoded(encoded_raw);
        return this;
    }

    @Override public boolean exists(String tag) {
        return cache.exists_in_cache(tag) || getEntry(tag) != null;
    }

    @Override public long length(String tag) {
        byte[] entry = cache.getCacheEntry(tag);
        if(entry!=null) {
            return entry.length;
        } else {
            return getEntry(tag).length;
        }
    }
    @Override public void readFromEncodedBytes(byte[] encoded_bytes) {
        cache.reset();
        delegation.readFromEncodedBytes(encoded_bytes);
    }

    @Override public String toString() {
        return "["+getClass().getSimpleName()+": delegation_encoder: \""+delegation+"\", cache: \""+cache+"\"]";
    }


    //required, boring delegation override

    //tag cache not possible, because never guaranteed that all tags are cached. (not even the goal)
    @Override public String[] getTags() {
        return delegation.getTags();
    }

    @Override public Iterator<TaggedEntry<byte[]>> iterator() {
        return delegation.iterator();
    }
    @Override public Iterable<String> tag_iterator() {
        return delegation.tag_iterator();
    }
    @Override public Iterable<TaggedStream> getEntryIterator_stream() {
        return delegation.getEntryIterator_stream();
    }
    @Override public byte[] getEncoded() {
        return delegation.getEncoded();
    }
    @Override public byte[] getEncodedBytes() {
        return delegation.getEncodedBytes();
    }
    @Override public TypeToFromRawTransformer<byte[]> createTypeTransformer() {
        return delegation.getTypeTransformer();
    }
    @Override public TransparentBytesStorage getRawStorageSystem() {
        return delegation.getRawStorageSystem();
    }
    @Override public int hashCode() {
        return delegation.hashCode();
    }
    @Override public boolean equals(Object o) {
        return o instanceof CachedTagBasedEncoderBytes && delegation.equals(((CachedTagBasedEncoderBytes)o).delegation);
    }
}