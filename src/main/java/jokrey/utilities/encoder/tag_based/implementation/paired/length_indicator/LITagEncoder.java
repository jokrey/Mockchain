package jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator;

import jokrey.utilities.encoder.as_union.li.LIPosition;
import jokrey.utilities.encoder.as_union.li.LISearchResult;
import jokrey.utilities.encoder.as_union.li.LIe;
import jokrey.utilities.encoder.tag_based.TagBasedEncoder;
import jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.thread_safe.SynchronizingTagBasedEncoder;
import jokrey.utilities.simple.data_structure.ExtendedIterator;
import jokrey.utilities.transparent_storage.TransparentStorage;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Objects;

/**
 * Non altering methods are reentrant and thread safe.
 *    Also long as non altering methods in the used {@link LIe} are thread safe.
 *
 * Implementation of a TagBasedEncoder using SF as the StorageFormat
 *    This implementation simply encodes tuples of tag and content into a LIse instance.
 *    Add: O(1)
 *    Delete: O(n)
 *    Search: O(n)
 *
 * Fasted way to decode everything is to use the iterator.
 *    Then decoding is still in O(n), but that is the best case to decode n elements with any data structure.
 *
 * NOT THREAD SAFE - by design for performance reasons
 *    For a thread safe version wrap an implementation of this class into a {@link SynchronizingTagBasedEncoder} or build a custom version.
 *    A custom versoin should preferably use read write locks, which is possible, because the non altering calls are reentrant.
 *
 * Due to performance optimizations heavily dependent on LIe (not it's subclasses though).
 *    This is perfectly fine, but has to be kept in mind
 *
 * @author jokrey
 * @see TagBasedEncoder
 */
public abstract class LITagEncoder<SF> implements TagBasedEncoder<SF> {
    //the underlying storage logic - untagged, sequence storage
    protected final LIe<SF> lie;
    
    /**
     * Initialises the internal storage model with the provided custom storage logic.
     *
     *
     * @param used_storage_logic previously encoded SF
     */
    protected LITagEncoder(LIe<SF> used_storage_logic) {
        lie = used_storage_logic;
//        position = new AtomicReference<>(lie.reset());
    }

    /**
     * Initialises the internal storage model with the provided parameter "workingSF".
     * Useful if one want's to decode a previously stored encoded SF.
     *
     * @param encoded previously encoded SF
     */
    public LITagEncoder(LIe<SF> used_storage_logic, SF encoded) {
        this(used_storage_logic);
        readFromEncoded(encoded);
    }



    //helper
    protected String getTag(SF raw) {
        return raw==null? null: getTypeTransformer().detransform_string(raw);
    }

//    private final AtomicReference<LIPosition> position;
    /**
     * ret can be null or has three entries: [0]==entry_start_index, [1]==entry_end_index, [2]==unsafe_raw_storage_start_index (end index is equal to entry_end_index
     * despite using a member variable and altering that member variable this search algorithm remains reentrant and thread safe.
     */
    protected LISearchResult search(String tag) {
        if(tag == null) throw new NullPointerException();
//        TODO: search from old position, implemented but it has thread safety issues for some reason.

        LIPosition local_position = lie.reset();
//        LIPosition local_position = position.get().copy(); //copy so that the member variable isn't changed before completing the search
                                                       //   would cause an issue if another thread was altering it, then we'd be jumping all over.
//        LIPosition start_search_position = local_position.copy(); // copy so that it isn't changed

        long last_raw_position = local_position.pointer;
        String dec_tag;
        do {
            dec_tag = getTag(lie.decode(local_position));
            long entry_length = lie.skipEntry(local_position); //skip also required to obtain a new valid pre-tag position.. ((%2==0))
            if (Objects.equals(tag, dec_tag)) {
                long entry_end_index = local_position.pointer;
//                    position.set(local_position);
                return new LISearchResult(entry_end_index - entry_length, entry_end_index, last_raw_position);
            }
            last_raw_position = local_position.pointer;
        } while (dec_tag != null);
//        } while (!start_search_position.equals(local_position));
//        position.set(lie.reset());
        return null;
    }


    @Override public boolean addEntry(String tag, SF entry) {
        return TagBasedEncoder.super.addEntry(tag, entry);
    }

    @Override public LITagEncoder<SF> addEntry_nocheck(String tag, SF entry) {
        lie.encode(getTypeTransformer().transform(tag)).encode(entry);
        return this;
    }

    @Override public SF getEntry(String tag) {
        LISearchResult sr = search(tag);
        if(sr == null) return null;
        return lie.getRawStorage().sub(sr.entry_start_index, sr.entry_end_index);
    }

    @Override public SF deleteEntry(String tag) {
        LISearchResult sr = search(tag);
        if(sr == null) return null;
        SF val = lie.getRawStorage().sub(sr.entry_start_index, sr.entry_end_index);
        lie.getRawStorage().delete(sr.raw_storage_start_index, sr.entry_end_index);

//        position.set(lie.reset()); //todo:
//        long raw_pos = lie.get(position);
//        if(raw_pos > sr.raw_storage_start_index)
//            lie.set(position, raw_pos - (sr.entry_end_index - sr.raw_storage_start_index));
        return val;
    }


    @Override public boolean deleteEntry_noReturn(String tag) {
        LISearchResult sr = search(tag);
        if(sr == null) return false;
        lie.getRawStorage().delete(sr.raw_storage_start_index, sr.entry_end_index);

//        position.set(lie.reset());//todo:
//        long raw_pos = lie.get(position);
//        if(raw_pos > sr.raw_storage_start_index)
//            lie.set(position, raw_pos - (sr.entry_end_index - sr.raw_storage_start_index));
        return true;
    }


    @Override public boolean exists(String tag) {
        return search(tag)!= null;
    }


    @Override public long length(String tag) {
        LISearchResult sr = search(tag);
        if(sr == null) return -1;
        return sr.entryLength();
    }


    @Override public String[] getTags() {
        ArrayList<String> toReturn = new ArrayList<>();
        LIPosition pos = lie.reset();
        String dec_tag;
        while((dec_tag = getTag(lie.decode(pos))) != null && lie.skipEntry(pos) != -1)
            toReturn.add(dec_tag);
        return toReturn.toArray(new String[0]);
    }


    @Override public LITagEncoder<SF> clear() {
        lie.clear();
//        position.set(lie.reset());
        return this;
    }

    @Override public TransparentStorage<SF> getRawStorageSystem() {
        return lie.getRawStorage();
    }

    @Override public LITagEncoder<SF> readFromEncoded(SF encoded_raw) {
        lie.readFromEncoded(encoded_raw);
//        position.set(lie.reset());
        return this;
    }

    @Override public SF getEncoded() {
        return lie.getEncoded();
    }


    @Override public Iterator<TaggedEntry<SF>> iterator() {
        ExtendedIterator<SF> lie_iterator = lie.iterator();
        return new Iterator<TaggedEntry<SF>>() {
            @Override public boolean hasNext() {
                return lie_iterator.hasNext(); //yes, this does work. Think about it! && Nooo, now you think it actually doesn't work! && Think again! && There you go!!! && Hooray
            }
            @Override public TaggedEntry<SF> next() {
                return new TaggedEntry<>(getTag(lie_iterator.next()), lie_iterator.next());
            }
            @Override public void remove() {
                lie_iterator.remove();
                lie_iterator.skip();
                lie_iterator.remove();
//                position.set(lie.reset());
            }
        };
    }

    @Override public int hashCode() {
        return lie.hashCode();
    }

    @Override public boolean equals(Object o) {
        return o instanceof LITagEncoder && lie.equals(((LITagEncoder)o).lie);
    }
}