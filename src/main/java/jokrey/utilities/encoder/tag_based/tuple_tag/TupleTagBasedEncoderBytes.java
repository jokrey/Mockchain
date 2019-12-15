package jokrey.utilities.encoder.tag_based.tuple_tag;

import jokrey.utilities.encoder.tag_based.TagBasedEncoder;
import jokrey.utilities.encoder.tag_based.TagBasedEncoderBytes;
import jokrey.utilities.encoder.type_transformer.TypeToFromRawTransformer;
import jokrey.utilities.simple.data_structure.pairs.Pair;
import jokrey.utilities.transparent_storage.StorageSystemException;
import jokrey.utilities.transparent_storage.bytes.TransparentBytesStorage;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;

/**
 * Superclass for most tag based encoders using bytes as their StorageFormat
 *
 * @author jokrey
 */
public interface TupleTagBasedEncoderBytes extends TupleTagBasedEncoder<byte[]> {
    /**
     * Retrieves the entry with the specified tag as an InputStream.
     * This stream is not cached and if the contents it points to are changed from elsewhere before using this stream the results may be unexpected.
     *
     * @param tag the tag identifying the entry
     * @return entry at tag as stream with the length in a pair OR null if it does not exist
     * @throws StorageSystemException if something with the underlying storage system fails
     * @throws RuntimeException any kind of RuntimeException if the encoded array has been altered externally
     */
    Pair<Long, InputStream> getEntry_asLIStream(String super_tag, String tag) throws StorageSystemException;
    /**same as {@link #getEntry_asLIStream(String, String)}, but without the length. */
    default InputStream getEntry_asStream(String super_tag, String tag) throws StorageSystemException {
        return getEntry_asLIStream(super_tag, tag).r;
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
    TupleTagBasedEncoderBytes addEntry_nocheck(String super_tag, String tag, InputStream content, long content_length) throws StorageSystemException;

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
    default boolean addEntry(String super_tag, String tag, InputStream content, long content_length) throws StorageSystemException {
        boolean was_replace = deleteEntry_noReturn(super_tag, tag); //just in case it already exists_in_cache
        addEntry_nocheck(super_tag, tag, content, content_length);
        return was_replace;
    }



    /**
     * Provides a nice little Iterator, that does not require caching all the tags to iterate over them.
     * The streams are not cached and if the contents it points to are changed from elsewhere before using the streams the results may be unexpected.
     *
     * Supports remove
     *
     * @return an iterator over all tags and their entries
     */
    default Iterable<TaggedStream> getEntryIterator_stream(String super_tag) {
        Iterator<String> tag_iterator = tag_iterator(super_tag).iterator();
        return () -> new Iterator<TaggedStream>() {
            @Override public boolean hasNext() {
                return tag_iterator.hasNext();
            }
            @Override public TaggedStream next() {
                String tag = tag_iterator.next();
                return new TaggedStream(super_tag, tag, getEntry_asLIStream(super_tag, tag).r);
            }
            @Override public void remove() {
                tag_iterator.remove();
            }
        };
        //the following is another possible implementation.
        //it is likely to be quicker because no additional search has to be done
        //However with longer than array streams it may fail
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

    default TagBasedEncoderBytes getSubEncoder(String super_tag) {
        return new TagBasedEncoderBytes() {
            @Override public Pair<Long, InputStream> getEntry_asLIStream(String tag) throws StorageSystemException {
                return TupleTagBasedEncoderBytes.this.getEntry_asLIStream(super_tag, tag);
            }
            @Override public TagBasedEncoderBytes addEntry_nocheck(String tag, InputStream content, long content_length) throws StorageSystemException {
                TupleTagBasedEncoderBytes.this.addEntry_nocheck(super_tag, tag, content, content_length);
                return this;
            }
            @Override public long length(String tag) {
                return TupleTagBasedEncoderBytes.this.length(super_tag, tag);
            }
            @Override public boolean exists(String tag) {
                return TupleTagBasedEncoderBytes.this.exists(super_tag, tag);
            }
            @Override public String[] getTags() {
                return TupleTagBasedEncoderBytes.this.getTags(super_tag);
            }
            @Override public TagBasedEncoder<byte[]> clear() {
                TupleTagBasedEncoderBytes.this.clear(super_tag);
                return this;
            }
            @Override public boolean deleteEntry_noReturn(String tag) {
                return TupleTagBasedEncoderBytes.this.deleteEntry_noReturn(super_tag, tag);
            }
            @Override public TagBasedEncoder<byte[]> addEntry_nocheck(String tag, byte[] entry) {
                TupleTagBasedEncoderBytes.this.addEntry_nocheck(super_tag, tag, entry);
                return this;
            }
            @Override public byte[] getEntry(String tag) {
                return TupleTagBasedEncoderBytes.this.getEntry(super_tag, tag);
            }
            @Override public byte[] deleteEntry(String tag) {
                return TupleTagBasedEncoderBytes.this.deleteEntry(super_tag, tag);
            }
            @Override public TypeToFromRawTransformer<byte[]> createTypeTransformer() {
                return TupleTagBasedEncoderBytes.this.getTypeTransformer(); //no need to create a new one
            }
            @Override public int hashCode() {
                return Arrays.hashCode(getTags());
            }

            //unsupported:
            @Override public TransparentBytesStorage getRawStorageSystem() {
                throw new UnsupportedOperationException("readFromEncoded not supported for tuple encoder");
            }
            @Override public TagBasedEncoder<byte[]> readFromEncoded(byte[] encoded_raw) {
                throw new UnsupportedOperationException("readFromEncoded not supported for tuple encoder");
            }
            @Override public byte[] getEncoded() {
                throw new UnsupportedOperationException("getEncoded not supported for tuple encoder");
            }
            @Override public boolean equals(Object o) {
                throw new UnsupportedOperationException("equals not supported for tuple encoder");
            }
            @Override public Iterator<TaggedEntry<byte[]>> iterator() {
                throw new UnsupportedOperationException("iterator not supported for tuple encoder");
            }
            @Override public byte[] getEncodedBytes() {
                throw new UnsupportedOperationException("getEncodedBytes not supported for tuple encoder");
            }
            @Override public void readFromEncodedBytes(byte[] encoded_bytes) {
                throw new UnsupportedOperationException("readFromEncodedBytes not supported for tuple encoder");
            }
            @Override public String getEncodedString() {
                throw new UnsupportedOperationException("getEncodedString not supported for tuple encoder");
            }
            @Override public void readFromEncodedString(String encoded_string) {
                throw new UnsupportedOperationException("readFromEncodedString not supported for tuple encoder");
            }
        };
    }

    /**
     * Tuple of String and InputStream. Yes. Java kinda sucks sometimes.
     */
    class TaggedStream {
        public final String super_tag;
        public final String tag;
        public final InputStream stream;
        public TaggedStream(String st, String t, InputStream s) {super_tag=st;tag=t;stream=s;}
    }


    @Override byte[] getEntry(String super_tag, String tag);
//    @Override TupleTagBasedEncoderBytes addEntry_nocheck(String super_tag, String tag, byte[] entry);
    @Override TupleTagBasedEncoder<byte[]> clear();
    @Override byte[] deleteEntry(String super_tag, String tag);
    @Override TypeToFromRawTransformer<byte[]> createTypeTransformer();
}
