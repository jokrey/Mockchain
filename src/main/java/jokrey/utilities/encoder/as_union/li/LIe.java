package jokrey.utilities.encoder.as_union.li;

import jokrey.utilities.encoder.as_union.AsUnionEncoder;
import jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.LITagEncoder;
import jokrey.utilities.transparent_storage.StorageSystemException;
import jokrey.utilities.transparent_storage.TransparentStorage;

/**
 * Non thread safe, but non altering methods are reentrant and thread safe(for readers only).
 *     As long as the underlying {@link TransparentStorage} system is reentrant and thread safe in it's non altering methods.
 *
 * This is the superclass for any length indicator based encoder.
 *     Technically this will work for any SF(StorageFormat) that can contain many of itself (any array) and has a length property (any array)
 *     One just has to find a function that returns a length indicator for the SF and a f^-1 function to turns that into boundaries again.
 * Idea: This is essentially just a weird type of iterator that can directly work on serialized data.
 *       It is used by {@link LITagEncoder} to store tag-value pairs.
 *
 * Implementations exist for Strings (utf8) and byte strings (byte[])
 * @author jokrey
 */
public abstract class LIe<SF> implements AsUnionEncoder<SF, LIPosition> {
    private final TransparentStorage<SF> storage;

    /**
     * Initialises internal storage with storage
     *
     * This constructor should be hidden from enc users
     * @param storage internal storage
     */
    protected LIe(TransparentStorage<SF> storage) {
        this.storage=storage;
    }

    /**
     * Initialises internal storage with storage and sets internal storage content to encoded
     * Shortcut for: new LIse().readFromEncoded(encoded);
     * @param encoded initial raw content
     */
    public LIe(TransparentStorage<SF> storage, SF encoded) {
        this(storage);
        readFromEncoded(encoded);
    }

    /**
     * Initialises internal storage with storage and encodes all initial values directly into the storage system.
     * Same as: new LIe(storage).encode(initial_values);
     * @param initial_values values to be encoded
     */
    public LIe(TransparentStorage<SF> storage, SF... initial_values) {
        this(storage);
        encode(initial_values);
    }


    //LI - OVERRIDE OVERRIDE OVERRIDE OVERRIDE OVERRIDE OVERRIDE OVERRIDE OVERRIDE

    /**
     * Only used internally
     */
    protected abstract SF getLengthIndicatorFor(SF val);

    /**
     * CANNOT not change anything within current, but this is so much quicker than calling getContent on it before
     * Only used internally (protected)
     *
     * @param start_pos start searching at
     * @param current curring working state
     * @return next start and end index of the li encoded SF. Calling current.sub(start, end) should yield the decoded SF.
     */
    protected abstract long[] get_next_li_bounds(LIPosition start_pos, TransparentStorage<SF> current);

    //LI - OVERRIDE OVERRIDE OVERRIDE OVERRIDE OVERRIDE OVERRIDE OVERRIDE OVERRIDE




    public long skipEntry(LIPosition pos) {
        long[] startIndex_endIndex_ofNextLI = get_next_li_bounds(pos, storage);
        if(startIndex_endIndex_ofNextLI != null) {
            pos.pointer = startIndex_endIndex_ofNextLI[1];
            return startIndex_endIndex_ofNextLI[1] - startIndex_endIndex_ofNextLI[0];
        }
        return -1;
    }

    public SF decode(LIPosition pos) {
        return decode_single(pos, false);
    }

    public LIPosition reset() {
        return new LIPosition(0);
    }


    //"SAFE" WRITE OPERATIONS::
    public LIe<SF> encode(SF val) {
        storage.append(getLengthIndicatorFor(val)).append(val);
        return this;
    }

    public SF deleteEntry(LIPosition pos) {
        return decode_single(pos, true);
    }

    public long delete(LIPosition pos) throws StorageSystemException {
        long[] startIndex_endIndex_ofNextLI = get_next_li_bounds(pos, storage);
        if(startIndex_endIndex_ofNextLI != null) {
            storage.delete(pos.pointer, startIndex_endIndex_ofNextLI[1]);
            //do not set read pointer, because it will now automatically point to the next item
            return startIndex_endIndex_ofNextLI[1] - startIndex_endIndex_ofNextLI[0];
        } else
            return -1;
    }


    @Override public TransparentStorage<SF> getRawStorage() {
        return storage;
    }



    //FUNCTIONALITY::

    private SF decode_single(LIPosition pos, boolean delete_decoded) {
        long[] startIndex_endIndex_ofNextLI = get_next_li_bounds(pos, storage);
        if(startIndex_endIndex_ofNextLI != null) {
            SF decoded = storage.sub(startIndex_endIndex_ofNextLI[0], startIndex_endIndex_ofNextLI[1]);
            if(delete_decoded) {
                storage.delete(pos.pointer, startIndex_endIndex_ofNextLI[1]);
            } else {
                pos.pointer = startIndex_endIndex_ofNextLI[1];
            }
            return decoded;
        } else {
            return null;
        }
    }



    @Override public boolean equals(Object o) {
        return o instanceof LIe && ((LIe)o).getRawStorage().equals(getRawStorage());
    }
    @Override public int hashCode() {
        return getRawStorage().hashCode();
    }
    @Override public String toString() {
        return "["+getClass().getSimpleName()+": with: "+getRawStorage()+"]";
    }
}
