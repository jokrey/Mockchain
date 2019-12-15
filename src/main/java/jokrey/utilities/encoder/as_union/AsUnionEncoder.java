package jokrey.utilities.encoder.as_union;

import jokrey.utilities.encoder.Encodable;
import jokrey.utilities.simple.data_structure.ExtendedIterator;
import jokrey.utilities.transparent_storage.StorageSystemException;
import jokrey.utilities.transparent_storage.TransparentStorage;

import java.util.ArrayList;
import java.util.NoSuchElementException;

import static jokrey.utilities.simple.data_structure.ExtendedIterator.createArrayFrom;
import static jokrey.utilities.simple.data_structure.ExtendedIterator.getTypeClassFor;

/**
 * Generic Interface for an iterator like encoder.
 *   Encodes multiple SF in order, into a single SF.
 *
 *
 *
 * @author jokrey
 */
public interface AsUnionEncoder<SF, Pos extends Position> extends Encodable<SF>, Iterable<SF> {
    //encode
    /**
     * Encodes and appends val to the end of this encoder
     * @param val to be encoded
     * @return this
     */
    AsUnionEncoder<SF, Pos> encode(SF val);

    /**
     * Encodes and appends all vals to the end of this encoder (in order)
     * @param vals to be encoded
     * @return this
     */
    default AsUnionEncoder<SF, Pos> encode(SF... vals) {
        for(SF val:vals)
            encode(val);
        return this;
    }

    //decode
    /**
     * Decodes a single element returns it.
     *
     * Parameter pos will be altered and subsequently decode the 'next' value after the last decoded one
     *     If there is no 'next' value, then pos will be set to the 'would-be' next value (and decode null next time, unless a new value was encoded in the meantime)
     *     If pos is already at an invalid value, then pos is not changed.
     *
     * @param pos position to be decoded
     * @return decoded element or null if there is no element to decode.
     */
    SF decode(Pos pos);

    /**
     * Skips a single element, should be faster than decoding to skip (because the value does not have to be read from memory).
     * Skipping means setting the internal pos to the new value(unless there is no new value, then pos is not changed).
     *
     * Parameter pos will be altered and subsequently decode the 'next' value after the last decoded one
     *     If there is no 'next' value, then pos will be set to the 'would-be' next value (and decode null next time, unless a new value was encoded in the meantime)
     *
     * @param pos position to be skipped.
     * @return number of skipped bytes (NOT TOTAL, but encoded user bytes)
     */
    long skipEntry(Pos pos);

    /**
     * Skips a single element, should be faster than decoding to skip (because the value does not have to be read from memory).
     * @param pos position to be skipped.
     * @return this
     */
    default AsUnionEncoder<SF, Pos> skip(Pos pos) {
        skipEntry(pos);
        return this;
    }

    //deletes

    /**
     * Clears the entire internal storage module.
     * DecodeFirst will return null after and decodeAll will return an empty array
     * @return this
     */
    default AsUnionEncoder<SF, Pos> clear() {
        getRawStorage().clear();
        return this;
    }

    /**
     * Deletes the entry at the given position and returns its length.
     *      If the given position was invalid it returns null and the data is not changed.
     *      Position itself is also not changed and will return null next time also, unless encode was called (unless more was changed then the position may never become valid)
     * @param pos position to be deleted.
     * @return deleted entry or null
     */
    long delete(Pos pos);

    /**
     * Deletes the entry at the given position and returns it.
     *      If the given position was invalid it returns null and the data is not changed.
     *      Position itself is also not changed and will return null next time also, unless encode was called (unless more was changed then the position may never become valid)
     * @param pos position to be deleted.
     * @return deleted entry or null
     */
    SF deleteEntry(Pos pos);


    //additional

    /**
     * @return a sensible "first" Position, should but is not guaranteed to return the same as forIndex(0).
     */
    Pos reset();

    /**
     * Will return a position for a given index
     * By default jumps through the data structure using skip, counting the skips and stopping at index.
     * @param index an index
     * @return internal position for index
     * @throws IndexOutOfBoundsException if the given index is out of bounds
     */
    default Pos forIndex(int index) {
        Pos pos = reset();
        for(int i=0;i<index;i++)
            skip(pos);
        return pos;
    }

    /**
     * An implementation may use this method to delete unused blocks, clean up the internal data structure and do similar things
     * This may be required because doing such operations at runtime may be too costly.
     * This entails that this operation may be slow.
     * This method may invalidate previous positions, but should not alter internal user data (indices, order, count, decode should all yield the same results).
     * If there is nothing to trim this method does precisely nothing(default)
     */
    default void trim() {}


    //convenience defaults

    /** @return the first element ever encoded */
    default SF decodeFirst() {
        return decode(reset());
    }

    /**
     * Decodes ALL entries previously encoded(in order)
     * @return all entries
     */
    default SF[] decodeAll() {
        return decodeMany(reset(), -1);
    }


    /**
     * Will decode entries until either there are no more to decode or it has decoded max entries
     * Parameter pos will be altered and subsequently decode the 'next' value after the last decoded one
     *     If there is no 'next' value, then pos will be set to the 'would-be' next value (and decode null next time, unless a new value was encoded in the meantime)
     * @param pos position to start decoding from
     * @param max number of entries to be decoded (a negative number here will cause a deletion of ALL elements after pos)
     * @return The decoded entries.
     */
    default SF[] decodeMany(Pos pos, int max) {
        ArrayList<SF> list = new ArrayList<>();
        SF dec_single = null;
        while((max<0 || list.size()<max) && (dec_single = decode(pos)) != null)
            list.add(dec_single);

        if(dec_single==null)
            return createArrayFrom(list, getTypeClassFor(getClass()));
        else
            return createArrayFrom(list, dec_single);
    }

    /**
     * Will delete entries until either there are no more to delete or it has deleted max entries
     * Parameter pos maybe altered, but will end up at a valid position (decoding with it, should yield the 'next' value after the deleted one)
     * @param pos position to start deleting from
     * @param max number of entries to be deleted (a negative number here will cause a deletion of ALL elements after pos)
     * @return number of deleted bytes (NOT DELETED BYTES IN UNDERLYING STORAGE, but user data bytes)
     */
    default long deleteMany(Pos pos, int max) {
        long totalBytesDeleted = 0;
        int counter = 0;
        while((max<0 || counter<max)) {
            counter++;
            long bytesDeleted = skipEntry(pos);
            if(bytesDeleted==-1)break;
            else totalBytesDeleted += bytesDeleted;
        }
        return totalBytesDeleted;
    }

    /**
     * Will delete entries until either there are no more to delete or it has deleted max entries
     * Parameter pos maybe altered, but will end up at a valid position (decoding with it, should yield the 'next' value after the last deleted one)
     * @param pos position to start deleting from
     * @param max number of entries to be deleted (a negative number here will cause a deletion of ALL elements after pos)
     * @return The deleted entries.
     */
    default SF[] deleteEntries(Pos pos, int max) {
        ArrayList<SF> list = new ArrayList<>();
        SF dec_single = null;
        while((max<0 || list.size()<max) && (dec_single = deleteEntry(pos)) != null)
            list.add(dec_single);

        if(dec_single==null)
            return createArrayFrom(list, getTypeClassFor(getClass()));
        else
            return createArrayFrom(list, dec_single);
    }



    //Interface default implementation

    /**
     * @return the raw underlying storage
     */
    TransparentStorage<SF> getRawStorage();

    /** Shortcut
     * @return getRawStorage().contentSize()
     */
    default long getRawSize() {
        return getRawStorage().contentSize();
    }

    /** Shortcut
     * @return getRawStorage().getEncoded()
     */
    @Override default SF getEncoded() {
        return getRawStorage().getContent();
    }

    /** Shortcut
     * @return getRawStorage().readFromEncoded(encoded)
     */
    @Override default AsUnionEncoder<SF, Pos> readFromEncoded(SF encoded) {
        getRawStorage().setContent(encoded);
        return this;
    }

    @Override default ExtendedIterator<SF> iterator() {
        return iteratorFrom(reset());
    }
    /** Iterates from provided position
     * @see #iterator() */
    default ExtendedIterator<SF> iteratorFrom(Pos iterator) {
        return new ExtendedIterator<SF>() {
            @Override public boolean hasNext () {
                try {
                    return iterator.hasNext(getRawStorage());
                } catch (StorageSystemException e) {
                    return false;
                }
            }

            @Override public SF next() {
                SF decoded = next_or_null();
                if(decoded!=null)
                    return decoded;
                else
                    throw new NoSuchElementException("No more elements available. (Concurrent delete access?)");
            }

            @Override public SF next_or_null() {
                try {
                    return decode(iterator);
                } catch (StorageSystemException e) {
                    throw new NoSuchElementException("Internal Storage System Exception of sorts("+e.getMessage()+").");
                }
            }

            @Override public void skip() {
                try {
                    long decoded = AsUnionEncoder.this.skipEntry(iterator);
                    if(decoded<0)
                        throw new NoSuchElementException("No more elements available. (Concurrent delete access?)");
                } catch (StorageSystemException e) {
                    throw new NoSuchElementException("Internal Storage System Exception of sorts("+e.getMessage()+").");
                }
            }

            @Override public void remove() {
                delete(iterator);
                //setting the pointer is not necessary. It now automatically points to the next value.

//                long number_of_deleted_bytes = li_delete(iterator);
//                //special feature to at least keep the internal iterator pointer
//                if(this_iterator.pointer== iterator.pointer) {
//                    //We have a problem. The value the non-iterator libae has is now invalid(deleted)...
//                    //this is a huge issue, but we'll simply address it by pointing the pointer to a sensible default value
//                    this_iterator.pointer = 0;
//                } else if(this_iterator.pointer> iterator.pointer) {
//                    //We have a problem. The old read pointer will point to an invalid index or at least an unexpected index
//                    //therefore we'll have to change the pointer after
//                    this_iterator.pointer -= number_of_deleted_bytes;
//                    //one might think that old_read_pointer can now be negative.
//                    // But that is not possible, unless the underlying storage system was altered.
//                    // The read pointer always points to the first of a tuple, we are only deleting a tuple here
//                }
            }
        };
    }
}
