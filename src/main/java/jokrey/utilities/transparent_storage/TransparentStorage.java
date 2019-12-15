package jokrey.utilities.transparent_storage;

/**
 * Generic Storage System for any LIe.
 *
 * All methods should be reentrant and thread safe.
 * Usage that requires atomic, consecutive access of multiple methods are naturally still not thread safe.
 *
 * It allows closing the storage system, but before using please consult doc on {@link #close()}.
 *
 * @author jokrey
 */
public interface TransparentStorage<SF> extends AutoCloseable {
    /**
     * Clears the entire content to an initial state.
     * getContent should return something empty(ish) after.
     */
    void clear();
    
    /**
     * Sets the entire content. To the buffer array given.
     * May NOT actually copy the given array. So if you think you might use that array after, please consider cloning it.
     *
     * @param content array from which to set the raw content to later read from
     */
    void setContent(SF content);

    /**
     * The content as a byte array. The acquired array can be altered in whatever way the caller chooses, as it is a copy of the internal state.
     * For some implementations this may not be possible, because a SF can only be size 2^31-1 (and even that is not a good idea)..
     *
     * @return the content as a copied SF
     */
    SF getContent();

    /**
     * deletes the bytes between the specified indices
     *
     * @param      start  The beginning index, inclusive.
     * @param      end    The ending index, exclusive.
     * @return this object
     */
    TransparentStorage<SF> delete(long start, long end);

    /**
     * Returns the bytes between the specified indices
     *     Result can be altered as it is guaranteed to be a (sub)copy of the internal buffer.
     *
     * if the end index is out of boundaries, then return sub(start, contentSize())
     *
     * @param      start  The beginning index, inclusive.
     * @param      end    The ending index, exclusive.
     * @return the sub
     */
    SF sub(long start, long end);

    /**
     * Sets the content from start to start+part.length with part,
     * appending if required.
     * @param start start index, has to be within bounds
     * @param part not null
     * @param off
     * @param off_end
     */
    TransparentStorage<SF> set(long start, SF part, int off, int off_end);

    /**
     * Sets the content from start to start+part.length with part,
     * appending if required.
     * @param start start index, has to be within bounds
     * @param part not null
     * @param off
     */
    TransparentStorage<SF> set(long start, SF part, int off);

    /**
     * Sets the content from start to start+part.length with part,
     * appending if required.
     * @param start start index, has to be within bounds
     * @param part not null
     */
    default TransparentStorage<SF> set(long start, SF part) {
        return set(start, part, 0);
    }

    /**
     * Appends the bytes to the end of the content
     *
     * @param val to be appended
     * @return this object
     */
    default TransparentStorage<SF> append(SF val) {
        return set(contentSize(), val);
    }

    /**
     * Returns the size of the content.
     * Should be more performant and accurate than getContent().length
     *
     * @return the size in bytes
     */
    long contentSize();


    //standard is required

    int hashCode();

    boolean equals(Object o);

    /**
     * Closes the StorageSystem.
     * If the storage system does IO operations this may mean closing underlying resources.
     * For other systems this may mean setting internal fields to null to allow the gc to get rid of them.
     *
     * This method will NOT do anything to the stored data.
     * However with non-persistent storage systems it may nonetheless become inaccessible forever.
     *
     * After this method was called any other methods of the storage system are allowed to fail with any exception.
     * Typically this will be a null pointer exception because internal fields have been set to null.
     * (i.e. just make absolutely sure to not call any method on the system after close has been called.
     */
    @Override void close() throws Exception;
}
