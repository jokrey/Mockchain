package jokrey.utilities.encoder.tag_based;

import jokrey.utilities.encoder.Encodable;
import jokrey.utilities.simple.data_structure.pairs.Pair;
import jokrey.utilities.transparent_storage.StorageSystemException;
import jokrey.utilities.transparent_storage.bytes.TransparentBytesStorage;

import java.io.InputStream;
import java.util.Base64;
import java.util.Iterator;

/**
 * Super interface for most tag based encoders using bytes as their StorageFormat.
 *
 * @author jokrey
 */
public interface TagBasedEncoderBytes extends TagBasedEncoder<byte[]> {
    TransparentBytesStorage getRawStorageSystem();

    @Override default Encodable<byte[]> readFromEncoded(byte[] encoded_raw) {
        readFromEncodedBytes(encoded_raw);
        return this;
    }
    
    @Override default byte[] getEncoded() {
        return getEncodedBytes();
    }

    
    @Override default String getEncodedString() {
        return Base64.getEncoder().encodeToString(getEncoded());
    }
    
    @Override default void readFromEncodedString(String encoded_string) {
        readFromEncoded(Base64.getDecoder().decode(encoded_string));
    }

    /**
     * Retrieves the entry with the specified tag as an InputStream.
     * This stream is not cached and if the contents it points to are changed from elsewhere before using this stream the results may be unexpected.
     * This entails that concurrent usage is discouraged and only possible with additional synchronization.
     *
     * The long in the pair indicates the length. Some implementations may not be able to determine the length before the stream is read.
     * They will then return the closest possible number to the actual length.
     *
     * @param tag the tag identifying the entry
     * @return entry with tag as stream OR null if it does not exist
     * @throws StorageSystemException if something with the underlying storage system fails
     * @throws RuntimeException any kind of RuntimeException if the encoded array has been altered externally
     */
    Pair<Long, InputStream> getEntry_asLIStream(String tag) throws StorageSystemException;
    /**same as {@link #getEntry_asLIStream(String)}, but without the length. */
    default InputStream getEntry_asStream(String tag) throws StorageSystemException {
        return getEntry_asLIStream(tag).r;
    }

    /**
     * Same as addEntryT(String tag, InputStream content, long content_length),
     *    but does not check whether or not the tag already exists_in_cache within the system.
     *    It just assumes it isn't.
     *    If it turns out it already does exist within the system then the provided content may be hard to decode.
     *
     * @param tag the tag identifying the entry at decoding time.
     * @param content where to read the content from
     * @param content_length GUARANTEED eventual length of content
     * @throws RuntimeException any kind of RuntimeException if the stream does not deliver enough data or something else fail with it..
     */
    TagBasedEncoderBytes addEntry_nocheck(String tag, InputStream content, long content_length) throws StorageSystemException;

    /**
     * Adds the entry, with it's specified tag to the system.
     * If an entry with the specified tag is already in the system it is DELETED and replaced.
     *     To maintain the system condition that each tag is unique within the system.
     *
     * The content of the entry will be read from stream until content_length is reached.
     *   If the stream ends before that any number of exception will be thrown.
     *   So whatever you do, don't let that happen
     *
     * The stream will BE CLOSED after everything of value(and content_length) has been read.
     *
     * @param tag the tag identifying the entry at decoding time.
     * @param content where to read the content from
     * @param content_length GUARANTEED eventual length of content
     * @return whether an entry was replaced or not
     * @throws RuntimeException any kind of RuntimeException if the stream does not deliver enough data or something else fail with it..
     */
    default boolean addEntry(String tag, InputStream content, long content_length) throws StorageSystemException {
        boolean was_replace = deleteEntry_noReturn(tag); //just in case it already exists_in_cache
        addEntry_nocheck(tag, content, content_length);
        return was_replace;
    }



    /**
     * Provides a nice little Iterator, that does not require caching all the tags to iterate over them.
     * The streams are not cached and if the contents it points to are changed from elsewhere before using the streams the results may be unexpected.
     *
     * Default implementation does a search.
     *
     * Supports remove
     *
     * @return an iterator over all tags and their entries
     */
    default Iterable<TaggedStream> getEntryIterator_stream() {
        Iterator<String> tag_iterator = tag_iterator().iterator();
        return () -> new Iterator<TaggedStream>() {
            @Override public boolean hasNext() {
                return tag_iterator.hasNext();
            }
            @Override public TaggedStream next() {
                String tag = tag_iterator.next();
                return new TaggedStream(tag, getEntry_asStream(tag));
            }
            @Override public void remove() {
                tag_iterator.remove();
            }
        };

        //the following is another possible implementation.
        //it is likely to be quicker because no additional search has to be done
        //However with longer than array streams it may fail and produce issues.
//        Iterator<PairTaggedEntry<byte[]>> bytes_iterator = iterator();
//
//        return () -> new Iterator<TaggedStream>() {
//            @Override public boolean hasNext() {
//                return bytes_iterator.hasNext();
//            }
//            @Override public TaggedStream next() {
//                PairTaggedEntry<byte[]> entry = bytes_iterator.next();
//                InputStream entry_stream = new ByteArrayInputStream(entry.val);
//                return new TaggedStream(entry.tag, entry_stream);
//            }
//            @Override public void remove() {
//                bytes_iterator.remove();
//            }
//        };
    }

    /**
     * Tuple of String and InputStream. Yes. Java kinda sucks sometimes.
     */
    class TaggedStream {
        public final String tag;
        public final InputStream stream;
        public TaggedStream(String t, InputStream s) {tag=t;stream=s;}
    }
}
