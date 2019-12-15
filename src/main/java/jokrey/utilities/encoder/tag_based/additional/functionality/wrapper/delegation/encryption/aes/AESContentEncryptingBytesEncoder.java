package jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.encryption.aes;

import jokrey.utilities.encoder.helper.AESHelper;
import jokrey.utilities.encoder.tag_based.TagBasedEncoder;
import jokrey.utilities.encoder.tag_based.TagBasedEncoderBytes;
import jokrey.utilities.encoder.type_transformer.TypeToFromRawTransformer;
import jokrey.utilities.simple.data_structure.pairs.Pair;
import jokrey.utilities.transparent_storage.StorageSystemException;
import jokrey.utilities.transparent_storage.bytes.TransparentBytesStorage;

import java.io.InputStream;
import java.util.Iterator;

/**
 * Uses AES with CBC to encrypt content and tag {@link AESHelper}.
 * Might produce slightly longer output because for each entry the result byte string length has to be padded to be divisible by 16.
 * Additionally another 16 bits are required to store the iv aes requires.
 *
 * Encryption will be completely transparent to the user. As long as the key is correct.
 *
 * Tags will not be encrypted. Use {@link AESTagEncryptingBytesEncoder} for that.
 *
 * @author jokrey
 */
public class AESContentEncryptingBytesEncoder implements TagBasedEncoderBytes {
    private final TagBasedEncoderBytes delegation;
    private final AESHelper aes;

    /**
     * Constructor
     * Requires call to setKey later.
     * @param delegation the actual tbe
     */
    public AESContentEncryptingBytesEncoder(TagBasedEncoderBytes delegation) {
        this.delegation = delegation;
        aes=new AESHelper();
    }

    /**
     * Constructor
     * @param delegation the actual tbe
     * @param key aes key, should be length 16
     */
    public AESContentEncryptingBytesEncoder(TagBasedEncoderBytes delegation, byte[] key) {
        this.delegation = delegation;
        aes=new AESHelper(key);
    }

    /**
     * Re-initiates the internal aes params.
     * Might make previously encoded stuff using old params inaccessible
     * @param key aes key, should be length 16
     */
    public void setKey(byte[] key) {
        aes.setKey(key);
    }




    @Override public AESContentEncryptingBytesEncoder addEntry_nocheck(String tag, byte[] entry) {
        delegation.addEntry_nocheck(tag, aes.encrypt(entry));
        return this;
    }
    @Override public byte[] getEntry(String tag) {
        return aes.decrypt(delegation.getEntry(tag));
    }
    @Override public byte[] deleteEntry(String tag) {
        return aes.decrypt(delegation.deleteEntry(tag));
    }
    @Override public boolean addEntry(String tag, InputStream content, long content_length) throws StorageSystemException {
        return delegation.addEntry(tag, aes.encrypt_stream(content), aes.get_length_of_encrypted_with_nonce(content_length));
    }
    @Override public Pair<Long, InputStream> getEntry_asLIStream(String tag) throws StorageSystemException {
        Pair<Long, InputStream> stream = delegation.getEntry_asLIStream(tag);
        return new Pair<>(stream.l, aes.decrypt_stream(stream.r)); //todo: stream.l maybe too large..
    }
    @Override public TagBasedEncoderBytes addEntry_nocheck(String tag, InputStream content, long content_length) throws StorageSystemException {
        delegation.addEntry_nocheck(tag, aes.encrypt_stream(content), aes.get_length_of_encrypted_with_nonce(content_length));
        return this;
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
                return new TaggedEntry<>(encrypted.tag, aes.decrypt(encrypted.val));
            }
            @Override public void remove() {
                iterator.remove();
            }
        };
    }

    @Override public Iterable<TaggedStream> getEntryIterator_stream() {
        Iterator<TaggedStream> iterator = delegation.getEntryIterator_stream().iterator();
        return () -> new Iterator<TaggedStream>() {
            @Override public boolean hasNext() {
                return iterator.hasNext();
            }
            @Override public TaggedStream next() {
                TaggedStream encrypted = iterator.next();
                return new TaggedStream(encrypted.tag, aes.decrypt_stream(encrypted.stream));
            }
            @Override public void remove() {
                iterator.remove();
            }
        };
    }

    /**
     * Will be %16==0. This is due to the aes padding algorithm.
     * To get the actual length do: getEntry(tag).length()
     * @param tag the tag identifying the entry
     * @return the approximate length or -1 if tag does not exist
     * @see TagBasedEncoder#length(String)
     */
    @Override public long length(String tag) {
        return delegation.length(tag);
    }

    /**
     * Returns raw encrypted bytes -
     * {@inheritDoc}
     */
    @Override public byte[] getEncodedBytes() {
        return delegation.getEncodedBytes();
    }
    /**
     * @param encoded_bytes have to be previously encrypted -
     * {@inheritDoc}
     */
    @Override public void readFromEncodedBytes(byte[] encoded_bytes) {
        delegation.readFromEncodedBytes(encoded_bytes);
    }



    //simple delegation:

    @Override public boolean exists(String tag) {
        return delegation.exists(tag);
    }
    @Override public String[] getTags() {
        return delegation.getTags();
    }
    @Override public AESContentEncryptingBytesEncoder clear() {
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
