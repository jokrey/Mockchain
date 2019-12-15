package jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.compression.deflate;

import jokrey.utilities.bitsandbytes.BitHelper;
import jokrey.utilities.encoder.tag_based.TagBasedEncoder;
import jokrey.utilities.encoder.tag_based.TagBasedEncoderBytes;
import jokrey.utilities.encoder.type_transformer.TypeToFromRawTransformer;
import jokrey.utilities.simple.data_structure.pairs.Pair;
import jokrey.utilities.transparent_storage.StorageSystemException;
import jokrey.utilities.transparent_storage.bytes.TransparentBytesStorage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.zip.Deflater;

/**
 * Uses the deflate algorithm to compress the entry data, before sending it to the delegation.
 *
 * Compression will be completely transparent to the user. As long as streams are not used(the work different here, due to limitations.
 *
 * Tags will not be compressed.
 *
 * @author jokrey
 */
public class DeflateContentCompressingBytesEncoder implements TagBasedEncoderBytes {
    private final TagBasedEncoderBytes delegation;
    private final int compression_level;

    /**
     * Constructor
     * Requires call to setKey later.
     * @param delegation the actual tbe
     */
    public DeflateContentCompressingBytesEncoder(TagBasedEncoderBytes delegation) {
        this(delegation, Deflater.BEST_COMPRESSION);
    }
    /**
     * Constructor
     * Requires call to setKey later.
     * @param delegation the actual tbe
     * @param compression_level between 0-9 {@link Deflater#Deflater(int)}
     */
    public DeflateContentCompressingBytesEncoder(TagBasedEncoderBytes delegation, int compression_level) {
        this.delegation = delegation;
        this.compression_level = compression_level;
    }




    @Override public DeflateContentCompressingBytesEncoder addEntry_nocheck(String tag, byte[] entry) {
        delegation.addEntry_nocheck(tag, DeflateAlgorithmHelper.compress(entry, compression_level));
        return this;
    }
    @Override public byte[] getEntry(String tag) {
        return DeflateAlgorithmHelper.decompress(delegation.getEntry(tag));
    }
    @Override public byte[] deleteEntry(String tag) {
        return DeflateAlgorithmHelper.decompress(delegation.deleteEntry(tag));
    }

    /**
     * USES SLOW NAIVE VERSION
     * {@inheritDoc}
     */
    @Override public boolean addEntry(String tag, InputStream content, long content_length) throws StorageSystemException {
        if(content_length > Integer.MAX_VALUE)
            throw new UnsupportedOperationException();
        try {
            return addEntry(tag, BitHelper.toByteArray(content, (int) content_length));
        } catch (IOException e) {
            throw new StorageSystemException("IOException thrown by provided input stream: "+e.getMessage());
        }
    }
    /**
     * USES SLOW NAIVE VERSION
     * {@inheritDoc}
     */
    @Override public Pair<Long, InputStream> getEntry_asLIStream(String tag) throws StorageSystemException {
        byte[] entry = getEntry(tag);
        return new Pair<>((long) entry.length, new ByteArrayInputStream(entry));
    }
    /**
     * USES SLOW NAIVE VERSION
     * {@inheritDoc}
     */
    @Override public TagBasedEncoderBytes addEntry_nocheck(String tag, InputStream content, long content_length) throws StorageSystemException {
        if(content_length > Integer.MAX_VALUE)
            throw new UnsupportedOperationException();
        try {
            return addEntry_nocheck(tag, BitHelper.toByteArray(content, (int) content_length));
        } catch (IOException e) {
            throw new StorageSystemException("IOException thrown by provided input stream: "+e.getMessage());
        }
    }

    /**
     * USES SLOW NAIVE VERSION
     * {@inheritDoc}
     */
    @Override public long length(String tag) {
        return getEntry(tag).length;
//        throw new UnsupportedOperationException("length cannot be known before decoding - use getEntry(tag).length");
    }


    /**
     * The TaggedEntry provided by each next() call will be decrypted.
     * @see TagBasedEncoder#iterator()
     */
    @Override public Iterator<TaggedEntry<byte[]>> iterator() {
        Iterator<TaggedEntry<byte[]>> iterator = delegation.iterator();
        return new Iterator<TaggedEntry<byte[]>>() {
            @Override public boolean hasNext() {
                return iterator.hasNext();
            }
            @Override public TaggedEntry<byte[]> next() {
                TaggedEntry<byte[]> encrypted = iterator.next();
                return new TaggedEntry<>(encrypted.tag, DeflateAlgorithmHelper.decompress(encrypted.val));
            }
            @Override public void remove() {
                iterator.remove();
            }
        };
    }

    /**
     * Will be compressed.
     * {@inheritDoc}
     */
    @Override public byte[] getEncodedBytes() {
        return DeflateAlgorithmHelper.compress(delegation.getEncodedBytes(), compression_level);
    }
    /**
     * {@inheritDoc}
     * @param encoded_bytes previously compressed data.
     */
    @Override public void readFromEncodedBytes(byte[] encoded_bytes) {
        delegation.readFromEncodedBytes(DeflateAlgorithmHelper.decompress(encoded_bytes));
    }



    //simple delegation:

    @Override public boolean exists(String tag) {
        return delegation.exists(tag);
    }
    @Override public String[] getTags() {
        return delegation.getTags();
    }
    @Override public DeflateContentCompressingBytesEncoder clear() {
        delegation.clear();
        return this;
    }
    @Override public boolean deleteEntry_noReturn(String tag) {
        return delegation.deleteEntry_noReturn(tag);
    }
    @Override public TransparentBytesStorage getRawStorageSystem() {
        return delegation.getRawStorageSystem();
    }
    @Override public TypeToFromRawTransformer<byte[]> createTypeTransformer() {
        return delegation.getTypeTransformer();
    }
}
