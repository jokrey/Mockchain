package jokrey.utilities.transparent_storage.bytes;

import jokrey.utilities.transparent_storage.StorageSystemException;
import jokrey.utilities.transparent_storage.TransparentStorage;
import jokrey.utilities.transparent_storage.bytes.wrapper.SubBytesStorage;

import java.io.InputStream;

/**
 * Same as {@link TransparentStorage}, but adds some additional functionality for interaction with a transparent storage based on bytes.
 *
 * @author jokrey
 */
public interface TransparentBytesStorage extends TransparentStorage<byte[]> {
    /**
     * Will read bytes from stream until content_length is reached.
     *   If the stream ends before that many bytes were read any number of exceptions will be thrown.
     *   However to not make the entire libae content invalid, the remaining bytes will be padded with random(kind of, but should assumed to be) data
     *
     * The stream will BE CLOSED after everything of value(and content_length) has been read.
     *
     * Will overwrite bytes from start index or append them.
     *
     * @param start where to start overwriting content, if start > contentSize and Exception is thrown.
     * @param part where to read the content from
     * @param part_length GUARANTEED eventual length of content and maximum_bytes_to_read_from_content
     * @return this object
     * @throws StorageSystemException if something internally goes wrong
     */
    TransparentBytesStorage set(long start, InputStream part, long part_length) throws StorageSystemException;
    default TransparentBytesStorage append(InputStream content, long content_length) throws StorageSystemException {
        return set(contentSize(), content, content_length);
    }

    /**
     * Returns an input stream that will read the bytes between start and end.
     * If anything else from this object is called before the stream is read until the end results may be wrong.
     *
     * if start == end, then a stream will be returned.
     *
     * @param      start  The beginning index, inclusive.
     * @param      end    The ending index, exclusive.
     * @return the stream or null if end < start
     * @throws StorageSystemException if something internally goes wrong
     */
    InputStream substream(long start, long end) throws StorageSystemException;

    /**
     * Returns an input stream that will read all bytes in the represented storage.
     * If anything else from this object is called before the stream is read until the end results may be wrong.
     * @return a reading stream over the entire stored data
     */
    InputStream stream();


    //overridden so that it returns a TransparentBytesStorage and it's methods are available in the builder pattern.
    TransparentBytesStorage delete(long start, long end) throws StorageSystemException;
    TransparentBytesStorage set(long start, byte[] part, int off, int off_end) throws StorageSystemException;
    TransparentBytesStorage set(long start, byte[] part, int off) throws StorageSystemException;
    default TransparentBytesStorage set(long start, byte[] part) throws StorageSystemException {
        return set(start, part, 0);
    }
    default TransparentBytesStorage append(byte[] val) throws StorageSystemException {
        return set(contentSize(), val);
    }

    default SubBytesStorage subStorage(long start, long end) {
        return new SubBytesStorage(start, end, this);
    }
    default SubBytesStorage[] split(long at) {
        return split(at, Long.MAX_VALUE);
    }
    default SubBytesStorage[] split(long at, long max) {
        return new SubBytesStorage[] {
                new SubBytesStorage(0, at, this),
                new SubBytesStorage(0, max, this)
        };
    }
}
