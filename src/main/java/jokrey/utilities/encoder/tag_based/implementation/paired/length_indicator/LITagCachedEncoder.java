package jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator;

import jokrey.utilities.encoder.as_union.li.LIPosition;
import jokrey.utilities.encoder.as_union.li.LISearchResult;
import jokrey.utilities.encoder.type_transformer.TypeToFromRawTransformer;

import java.util.ArrayList;

/**
 * Cached wrapper for a {@link LITagEncoder}.
 * Over time decreases search, delete and checked-add complexity to O(log2(n))  [HashMap complexity]
 *    There is very little added space complexity.
 *
 * It works by caching completed li searches. (Their get_next_li_bounds results, not the actually stored values).
 *    It will NOT pre-cache searches in the constructor. Or anywhere else.
 *    It will only search over elements when that was necessary anyways.
 *    If you do require pre-caching then call getTags. It will have to run through all elements and therefore cache them.
 *
 * Not thread safe out of the box
 *
 * TODO: With a half cache it is already guaranteed that the biggest value in that cache is the smallest possible start value for an unknown element. (implemented, in super class, but has thread safety issues
 *       (Due to the fact that normal search is from start to end)
 *
 *
 * @author jokrey
 */
public class LITagCachedEncoder<SF> extends LITagEncoder<SF> {
    private final LITagEncoder<SF> delegation;
    private final LITagPositionCache cache = new LITagPositionCache();

    public LITagCachedEncoder(LITagEncoder<SF> delegation) {
        super(delegation.lie); //no double storage, and any operations will be executed there.
        this.delegation=delegation;
//        fill_search_cache();  // don't do that. cache on demand - much faster
    }


    //updated search algorithm
    @Override protected LISearchResult search(String tag) {
        LISearchResult cache_sr = cache.search(tag);
        if(cache_sr != null) return cache_sr; //MOST IMPORTANT PART

        if(cache.is_fully_cached()) {
            return null; //a full cache cannot produce a cache miss. If it still misses, then we can be sure that it does not exist at all.
//                throw new IllegalStateException("should not happen and this sanity check can be removed as soon as tests show that it doesn't");
        }

        //if we happen upon this line, then either cache isn't fully filled yet
        //   therefore everything that follows is just a result of the fact that we do not precache

        LIPosition search_pos = lie.reset();
        long last_read_pointer = 0;
        String dec_tag;
        long entry_length;
        while((dec_tag = getTag(lie.decode(search_pos))) != null &&
                ((entry_length = lie.skipEntry(search_pos)) != -1)) {

            long entry_end_index = search_pos.pointer;
            LISearchResult sr = new LISearchResult(entry_end_index-entry_length, entry_end_index, last_read_pointer);

            //always cache also not only our cache miss(maybe we can precache some without much cost and avoid a future search).
            //   May produce some redundant calls, but at some point the cache is filled and no more calls will be made
            cache.cache_search(dec_tag, sr);

            last_read_pointer = search_pos.pointer;

            if(tag.equals(dec_tag)) {
                if(sr.entry_end_index == lie.getRawSize()) //was last entry
                    cache.set_fully_cached();
                return sr;
            }
        }

        //yes to be sure the cache is fully cached we are require to have run through it once.
        cache.set_fully_cached();

        return null;
    }


//changed wrapping:::

    //also add entry to cache
    @Override public LITagCachedEncoder<SF> addEntry_nocheck(String tag, SF arr) {
        long old_length = lie.getRawSize();
        super.addEntry_nocheck(tag, arr);
        cache_add(tag, old_length);
        return this;
    }
    void cache_add(String tag, long old_length_before_add) {
        //minimal, optimal performance, fake search  (no checked search, but guaranteed fine)
        LIPosition fake_search_pos = new LIPosition(old_length_before_add);
        lie.skip(fake_search_pos); //skip tag
        long entry_length = lie.skipEntry(fake_search_pos); //skip entry
        long entry_end_index = fake_search_pos.pointer;
        LISearchResult added_sr = new LISearchResult(entry_end_index-entry_length, entry_end_index, old_length_before_add);

        cache.cache_search(tag, added_sr);

        lie.reset();//reset, because current position is end position and thereby useless
    }



    //also delete entry from cache table
    @Override public SF deleteEntry(String tag) {
        LISearchResult sr = search(tag); //already cached
        if(sr==null) return null;
        SF entry = lie.getRawStorage().sub(sr.entry_start_index, sr.entry_end_index);
        lie.getRawStorage().delete(sr.raw_storage_start_index, sr.entry_end_index);
        cache.remove_cache_entry(tag);
        return entry;
    }
    @Override public boolean deleteEntry_noReturn(String tag) {
        LISearchResult sr = search(tag); //already cached
        if(sr==null) return false;
        lie.getRawStorage().delete(sr.raw_storage_start_index, sr.entry_end_index);
        cache.remove_cache_entry(tag);
        return true;
    }



    //use this opportunity to fully fill the cache
    @Override public String[] getTags() {
        if(cache.is_fully_cached()) {
            return cache.getCachedTags();
        } else { //damn now we have to iterate all of them anyways. Well -> we'll use that chance to cache everything (close to no cost anyways)
            ArrayList<String> toReturn = new ArrayList<>();
            LIPosition search_pos = lie.reset();
            long last_raw_read_pointer = 0;
            String dec_tag;
            long entry_length;
            while ((dec_tag = getTag(lie.decode(search_pos))) != null &&
                    ((entry_length = lie.skipEntry(search_pos)) != -1)) {

                long entry_end_index = search_pos.pointer;
                LISearchResult sr = new LISearchResult(entry_end_index - entry_length, entry_end_index, last_raw_read_pointer);

                cache.cache_search(dec_tag, sr);

                last_raw_read_pointer = search_pos.pointer;

                toReturn.add(dec_tag);
            }
            cache.set_fully_cached();
            return toReturn.toArray(new String[0]);
        }
    }

    //clear the cache here...
    @Override public LITagEncoder<SF> readFromEncoded(SF encoded_raw) {
        cache.reset();
        return super.readFromEncoded(encoded_raw);
    }
    @Override public LITagCachedEncoder<SF> clear() {
        cache.reset();
        super.clear();
        return this;
    }










    //required delegation overrides...

    @Override public TypeToFromRawTransformer<SF> createTypeTransformer() {
        return delegation.getTypeTransformer();
    }
    @Override public byte[] getEncodedBytes() {
        return delegation.getEncodedBytes();
    }
    @Override public void readFromEncodedBytes(byte[] encoded_bytes) {
        delegation.readFromEncodedBytes(encoded_bytes);
    }
    @Override public String getEncodedString() {
        return delegation.getEncodedString();
    }
    @Override public void readFromEncodedString(String encoded_string) {
        delegation.readFromEncodedString(encoded_string);
    }
}