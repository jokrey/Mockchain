package jokrey.utilities.encoder.as_union.li;

/**
 * INTERNAL USAGE
 *
 * @author jokrey
 */
public class LISearchResult { //to not use a non descriptive long[].. Should be of similar performance (bottleneck heap allocation for both..)
    public final long entry_start_index;
    public final long entry_end_index;
    public final long raw_storage_start_index;
    public LISearchResult(long entry_start_index, long entry_end_index, long raw_storage_start_index) {
        this.entry_start_index = entry_start_index;
        this.entry_end_index = entry_end_index;
        this.raw_storage_start_index = raw_storage_start_index;
        if(! (raw_storage_start_index <= entry_start_index && entry_start_index <= entry_end_index))
            throw new IllegalArgumentException(toString());
    }
    public boolean largerThan(LISearchResult o) {
        return raw_storage_start_index > o.raw_storage_start_index; //other don't have to be compared. Should they matter we have a larger issue at hand.....
    }
    public long total_length() {
        return entry_end_index - raw_storage_start_index;
    }
    @Override public String toString() {
        return "LISearchResult{" + "entry_start_index=" + entry_start_index + ", entry_end_index=" + entry_end_index + ", raw_storage_start_index=" + raw_storage_start_index + '}';
    }

    public LISearchResult minus(long minus) {
        return new LISearchResult(entry_start_index - minus, entry_end_index - minus, raw_storage_start_index - minus);
    }
    public long entryLength() {
        return entry_end_index - entry_start_index;
    }
}