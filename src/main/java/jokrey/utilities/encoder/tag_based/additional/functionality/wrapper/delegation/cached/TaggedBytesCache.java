package jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.cached;

import jokrey.utilities.bitsandbytes.BitHelper;
import jokrey.utilities.encoder.tag_based.TagEntryObservable;
import jokrey.utilities.encoder.tag_based.TagEntryObserver;
import jokrey.utilities.transparent_storage.StorageSystemException;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tag bytes entry cache for internal use by {@link CachedTagBasedEncoderBytes}.
 */
class TaggedBytesCache {
    private final int max_cache_size_in_bytes;
    private final int max_single_cache_entry_size_in_bytes;
    TaggedBytesCache() {
        this((int) Math.pow(2, 24)); //roughly(less) 4096_0000
    }
    TaggedBytesCache(int max_cache_size_in_bytes) {
        this(max_cache_size_in_bytes, max_cache_size_in_bytes / 6);
    }
    TaggedBytesCache(int max_cache_size_in_bytes, int max_single_cache_entry_size_in_bytes) {
        this.max_cache_size_in_bytes=max_cache_size_in_bytes;
        this.max_single_cache_entry_size_in_bytes =max_single_cache_entry_size_in_bytes;
    }

    private final LinkedHashMap<String, byte[]> cache = new LinkedHashMap<>();
    private int cache_size_bytes = 0;

    synchronized void local_update_tag_add(String tag, byte[] entry) {
        add_remote_block_for_tag(tag);
        local_fill_entry_cache(tag, entry);
    }
    synchronized boolean local_update_tag_add(String tag, InputStream content, long content_length) {
        if(! entry_fits_cache_size(content_length)) return false; //just straight up don't cache when too large
        try {
            local_update_tag_add(tag, BitHelper.toByteArray(content));
        } catch (IOException e) {
            throw new StorageSystemException("TaggedBytesCache failed to read from InputStream. excmsg:"+e.getMessage());
        }
        return true;
    }
    synchronized void local_update_tag_delete(String tag) {
        add_remote_block_for_tag(tag);  //required.. remote update may come so late that we already locally added something else, we'd want to ignore that.
        byte[] previous = cache.remove(tag);
        if(previous!=null)
            cache_size_bytes -= previous.length;
    }

    synchronized boolean local_fill_entry_cache(String tag, byte[] entry) {
        if(entry == null || ! entry_fits_cache_size(entry.length)) return false; //just straight up don't cache when too large

        byte[] previous = cache.put(tag, entry);
        if(previous != null) {
            cache_size_bytes-=previous.length;
            cache_size_bytes+=entry.length;
        } else {
            cache_size_bytes += entry.length;
            remove_oldest_elements_to_fit_into_max_cache_size();
        }
        return true;
    }

    private synchronized void remote_update_tag_add(String tag) {
        boolean tag_blocked = remove_one_remote_block(tag);
        if(! tag_blocked) {
            //i.e. invalidate cache for specified tag:
            byte[] deleted_cache_entry = cache.remove(tag); // (do not precache tag. cache only on demand.)
            if(deleted_cache_entry != null)
                cache_size_bytes-=deleted_cache_entry.length;
        }
    }
    private synchronized void remote_update_tag_delete(String tag) {
        boolean tag_blocked = remove_one_remote_block(tag);
        if(! tag_blocked) {
            byte[] deleted_cache_entry = cache.remove(tag);
            if(deleted_cache_entry != null)
                cache_size_bytes-=deleted_cache_entry.length;
        }
    }



    private boolean entry_fits_cache_size(long entry_size) {
        return entry_size <= max_single_cache_entry_size_in_bytes;
    }


    private void remove_oldest_elements_to_fit_into_max_cache_size() {
        remove_oldest_elements_to_fit_into_max_cache_size(0);
    }
    private void remove_oldest_elements_to_fit_into_max_cache_size(int new_element_length) {
        if(cache_size_bytes > (max_cache_size_in_bytes + new_element_length)) {
            //remove oldest values, to fit max cache size
            Iterator<Map.Entry<String, byte[]>> cache_entries = cache.entrySet().iterator();
            while (cache_size_bytes > (max_cache_size_in_bytes + new_element_length) && cache_entries.hasNext()) {
                Map.Entry<String, byte[]> cache_entry = cache_entries.next();//works because LinkedHashMaps iterator retains the order in which elements are inserted
                cache_entries.remove();
                cache_size_bytes -= cache_entry.getValue().length;
            }
        }
    }


    private final HashMap<String, Integer> tag_blocked_for_remote_update = new HashMap<>();
    private synchronized void add_remote_block_for_tag(String tag) {
        Integer block_count = tag_blocked_for_remote_update.get(tag);
        tag_blocked_for_remote_update.put(tag, block_count == null? 1 : block_count + 1);
    }
    private synchronized boolean remove_one_remote_block(String tag) {
        Integer block_count = tag_blocked_for_remote_update.remove(tag);
        if(block_count==null) { // || block_count == 0 cannot happen
            return false;
        } else if(block_count > 1)
           tag_blocked_for_remote_update.put(tag, block_count - 1);
        return true;
    }

    void setRemoteUpdateListener(TagEntryObservable external_change_callback_supplier) {
        if(external_change_callback_supplier!=null)
            external_change_callback_supplier.addTagEntryObserver(new TagEntryObserver() {
                @Override public void update_add(String tag) {
                    remote_update_tag_add(tag);
                }
                @Override public void update_delete(String tag) {
                    remote_update_tag_delete(tag);
                }
                @Override public void update_set_content() {
                    reset();
                }
            });
    }


    byte[] getCacheEntry(String tag) {
        return cache.get(tag);
    }
    boolean exists_in_cache(String tag) {
        return cache.containsKey(tag);
    }

    public synchronized void reset() {
        cache.clear();
        tag_blocked_for_remote_update.clear();
//        for(TagBasedEncoder.PairTaggedEntry<SF> e:data_query)  // do not precache
//            cache.put(e.tag, e.val);
    }


    @Override synchronized public String toString() {
        return "TaggedBytesCache{" + "cache=" + cache.keySet() + ", cache_size_bytes=" + cache_size_bytes +
                ", tag_blocked_for_remote_update=" + tag_blocked_for_remote_update + '}';
    }
}