package jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.bytes;

import jokrey.utilities.encoder.as_union.li.LIPosition;
import jokrey.utilities.encoder.as_union.li.LISearchResult;
import jokrey.utilities.encoder.as_union.li.bytes.LIbae;
import jokrey.utilities.encoder.tag_based.TagBasedEncoderBytes;
import jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.thread_safe.SynchronizingTagBasedEncoderBytes;
import jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.LITagEncoder;
import jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.type.transformer.LITypeToBytesTransformer;
import jokrey.utilities.encoder.type_transformer.TypeToFromRawTransformer;
import jokrey.utilities.simple.data_structure.pairs.Pair;
import jokrey.utilities.transparent_storage.StorageSystemException;
import jokrey.utilities.transparent_storage.bytes.TransparentBytesStorage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Encodes multiple chunks of bytes into one and tags them.
 *   they then become searchable.
 *   The internal byte storage can be in RAM or on file or even remote(but you'd have to implement that yourself).
 *   Each tag is only allowed to exist once.
 *
 * This is done by adding entries in pairs to the underlying lie system.(tag, then content).
 *   Everything else are just convenience helpers to make exactly that easier.
 * At decoding time entries can then be identified by tags.
 *   This is done by simply iterating over all lie elements(in pairs always) until the tag is found.
 *
 * NOT THREAD SAFE - by design(for performance reasons)
 *     It can simply be wrapped into a {@link SynchronizingTagBasedEncoderBytes} to make it thread safe.
 *
 * @author jokrey
 */
public class LITagBytesEncoder extends LITagEncoder<byte[]> implements TagBasedEncoderBytes {
    /**
     * Empty Constructor.
     * Initialises all needed internal variables.
     */
    public LITagBytesEncoder() {
        super(new LIbae());
    }

    /**
     * Constructor.
     * Initialises the internal storage model with the provided parameter "workingArr".
     * Useful if one want's to decode previously stored encoded bytes.
     *
     * @param workingArr encoded array to start with
     */
    public LITagBytesEncoder(byte[] workingArr) {
        super(new LIbae(workingArr));
    }

    /**
     * Constructor.
     * Initialises the internal storage model by reading everything from the input stream into an array and calling LITagBytesEncoder(byte[] workingArr)
     *
     * @param is encoded array to start with
     * @throws IOException is something happens while reading from YOUR InputStream......
     */
    public LITagBytesEncoder(InputStream is) throws IOException {
        super(new LIbae(is));
    }

    /**
     * Constructor.
     * Initialises the internal storage model with the provided parameter "contentBuilder".
     * Useful if one want's to decode previously stored encoded bytes.
     *
     * @param contentBuilder contentBuilder to start with or from
     */
    public LITagBytesEncoder(TransparentBytesStorage contentBuilder) {
        super(new LIbae(contentBuilder));
    }

    /**
     * Constructor.
     * Uses the given LIbae as the internal M:1 encoder
     *
     * @param aLIbae a LIbae implementation to work with
     */
    public LITagBytesEncoder(LIbae aLIbae) {
        super(aLIbae);
    }



    @Override public TransparentBytesStorage getRawStorageSystem() {
        return (TransparentBytesStorage) super.getRawStorageSystem();
    }

    private TypeToFromRawTransformer<byte[]> transformer = null;
    @Override public TypeToFromRawTransformer<byte[]> getTypeTransformer() {
        if(transformer==null)
            transformer=createTypeTransformer();
        return transformer;
    }
    @Override public TypeToFromRawTransformer<byte[]> createTypeTransformer() {
        return new LITypeToBytesTransformer();
    }


    //implementing the Encodable


    /** {inheritDoc} */
    @Override public byte[] getEncodedBytes() throws StorageSystemException {
        return getEncoded();
    }
    /** {inheritDoc} */
    @Override public void readFromEncodedBytes(byte[] encoded_bytes) {
        readFromEncoded(encoded_bytes);
    }
    /** {inheritDoc} */
    @Override public String getEncodedString() {
        return Base64.getEncoder().encodeToString(getEncoded());
    }
    /** {inheritDoc} */
    @Override public void readFromEncodedString(String encoded_string) {
        readFromEncoded(Base64.getDecoder().decode(encoded_string));
    }





    //additional stream functionality and support


    @Override public Pair<Long, InputStream> getEntry_asLIStream(String tag) throws StorageSystemException {
        LISearchResult sr = search(tag);
        if(sr == null) return null;
        return new Pair<>(sr.entryLength(), getRawStorageSystem().substream(sr.entry_start_index, sr.entry_end_index));
    }


    @Override public LITagBytesEncoder addEntry_nocheck(String tag, InputStream content, long content_length) throws StorageSystemException {
//        deleteEntryT(tag); //just to show whats missing

        lie.encode(getTypeTransformer().transform(tag));
        ((LIbae)lie).encode(content, content_length);
        return this;
    }

    /** {@inheritDoc}
     * Should be faster and more memory concious for some StorageSystem than using the default implementation
     *
     * Supports remove, though remove
     */
    @Override public Iterable<TaggedStream> getEntryIterator_stream() {
        return new Iterable<TaggedStream>() {
            LIPosition iterator = lie.reset(); //to make it nestable and thread safe
            @Override public Iterator<TaggedStream> iterator() {
                return new Iterator<TaggedStream>() {
                    @Override public boolean hasNext () {
                        try {
                            return iterator.hasNext(getRawStorageSystem());
                        } catch (StorageSystemException e) {
                            return false;
                        }
                    }
                    @Override public TaggedStream next () {
                        try {
                            return new TaggedStream(new String(
                                    lie.decode(iterator), StandardCharsets.UTF_8),
                                    ((LIbae)lie).li_decode_asStream(iterator));
                        } catch (StorageSystemException e) {
                            throw new NoSuchElementException("Internal Storage System Exception of sorts("+e.getMessage()+").");
                        }
                    }

                    @Override public void remove() {
                        lie.deleteMany(iterator, 2);

//                        long old_read_pointer = ((LIbae)lie).unsafe_manually_get_read_pointer();
//                        ((LIbae)lie).unsafe_manually_set_read_pointer(iterator_read_pointer);
//                        long number_of_deleted_bytes = lie.delete(2);
//                        if(old_read_pointer==iterator_read_pointer) {
//                            //We have a problem. The value the non-iterator lie has is now invalid(deleted)...
//                            //this is a huge issue, but we'll simply adress it by pointing the pointer to a sensible default value
//                            lie.reset();
//                        } else if(old_read_pointer>iterator_read_pointer) {
//                            //We have a problem. The old read pointer will point to an invalid index or at least an unexpected index
//                            //therefore we'll have to change the pointer after
//                            old_read_pointer -= number_of_deleted_bytes;
//                            //one might think that old_read_pointer can now be negative.
//                            // But that is not possible, unless the underlying storage system was altered.
//                            // The read pointer always points to the first of a tuple, we are only deleting a tuple here
//                        }
//                        ((LIbae)lie).unsafe_manually_set_read_pointer(old_read_pointer);
                    }
                };
            }
        };
    }
}