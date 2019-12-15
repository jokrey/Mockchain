package jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator;

import jokrey.utilities.encoder.as_union.li.LISearchResult;

import java.util.HashMap;

/**
 * Internal usage only
 *
 * Caches search results for tags and handles some of the complexity of updating them on changes.
 *
 * @author jokrey
 */
class LITagPositionCache {
    private final HashMap<String, LISearchResult> tag_positions = new HashMap<>();


    private boolean fully_cached = false;
    boolean is_fully_cached() {
        return fully_cached;
    }
    void set_fully_cached() {
        fully_cached = true;
    }


    LISearchResult search(String tag) {
        return tag_positions.get(tag);
    }
    void cache_search(String tag, LISearchResult sr) {
        tag_positions.put(tag, sr);//only works if every entry in map is smaller than sr - this is guaranteed by application logic.
    }
    void remove_cache_entry(String tag) {
        LISearchResult tag_sr = tag_positions.remove(tag);
        if(tag_sr != null) {  //if a value was previously associated with the tag, should REALLY always be the case, but a null check doesn't cost nothing
            //we need to update every cache tag that is stored "after" the removed tag [that way we can keep their positions without researching (as in searching again)]::
            long tag_sr_length = tag_sr.total_length();
            tag_positions.replaceAll((e_tag, e_sr) -> {
                if(e_sr.largerThan(tag_sr))
                    return e_sr.minus(tag_sr_length);
                return e_sr;
            });
        }
    }
    void reset() {
        tag_positions.clear();
        fully_cached=false;
    }

    String[] getCachedTags() {
        return tag_positions.keySet().toArray(new String[0]);
    }

    @Override public String toString() {
        return "l=\""+tag_positions.size()+"\", fully_cached=\""+ fully_cached +"\", raw_cache="+ tag_positions;
    }
}