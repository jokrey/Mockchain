package jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.encryption.aes;

import jokrey.utilities.encoder.helper.AESHelper;
import jokrey.utilities.encoder.tag_based.TagBasedEncoderBytes;
import jokrey.utilities.encoder.type_transformer.TypeToFromRawTransformer;
import jokrey.utilities.simple.data_structure.pairs.Pair;
import jokrey.utilities.transparent_storage.StorageSystemException;
import jokrey.utilities.transparent_storage.bytes.TransparentBytesStorage;

import java.io.InputStream;
import java.util.Iterator;

/**
 * Uses AES to encrypt the tags {@link AESHelper}.
 * Might produce slightly longer output because the String encryption for the tags uses Base64 which results in longer strings.
 *
 * The same nonce is used for all of the tags.
 * This is technically wrong, but using different nonce's would produce different tags.
 * Which would be against the logic of tags...
 * 
 * For the caller it is completely transparent that the tags are encrypted, as long as the key is correct.
 *
 * Does not encrypt content. Use {@link AESContentEncryptingBytesEncoder} for that.
 * 
 * @author jokrey
 */
public class AESTagEncryptingBytesEncoder implements TagBasedEncoderBytes {
    protected final TagBasedEncoderBytes delegation;
    private final AESHelper aes;
    private final byte[] nonce = new byte[16];

    /**
     * Constructor
     * @param delegation the actual tbe
     */
    public AESTagEncryptingBytesEncoder(TagBasedEncoderBytes delegation) {
        this.delegation = delegation;
        aes=new AESHelper();
        randomNonce();
    }

    /**
     * Constructor
     * @param delegation the actual tbe
     * @param key aes key, should be length 16
     */
    public AESTagEncryptingBytesEncoder(TagBasedEncoderBytes delegation, byte[] key) {
        this.delegation = delegation;
        aes=new AESHelper(key);
        randomNonce();
    }

    /**
     * Re-initiates the internal aes params.
     * Might make previously encoded stuff using old params inaccessible
     * @param key aes key, should be length 16
     */
    public void setKey(byte[] key) {
        aes.setKey(key);
        randomNonce();
    }

    /**
     * Creates a new random nonce.
     * Will make old tags inaccessible, unless the old nonce is set again.
     */
    private void randomNonce() {
        aes.randomize(getNonce());
    }
//    private void setNonce(byte[] nonce) {
//        System.arraycopy(nonce, 0, getNonce(), 0, 16);
//    }
    private byte[] getNonce() {return nonce;}






    @Override public String[] getTags() {
        String[] encrypted_tags = delegation.getTags();
        String[] tags = new String[encrypted_tags.length];
        for(int i=0;i<tags.length;i++)
            tags[i] = aes.decrypt(encrypted_tags[i], getNonce());
        return tags;
    }
    @Override public boolean exists(String tag) {
        return delegation.exists(aes.encrypt(tag, getNonce()));
    }
    @Override public boolean deleteEntry_noReturn(String tag) {
        return delegation.deleteEntry_noReturn(aes.encrypt(tag, getNonce()));
    }
    @Override public AESTagEncryptingBytesEncoder addEntry_nocheck(String tag, byte[] entry) {
        delegation.addEntry_nocheck(aes.encrypt(tag, getNonce()), entry);
        return this;
    }
    @Override public byte[] getEntry(String tag) {
        return delegation.getEntry(aes.encrypt(tag, getNonce()));
    }
    @Override public byte[] deleteEntry(String tag) {
        return delegation.deleteEntry(aes.encrypt(tag, getNonce()));
    }
    @Override public boolean addEntry(String tag, InputStream content, long content_length) throws StorageSystemException {
        return delegation.addEntry(aes.encrypt(tag, getNonce()), content, content_length);
    }
    @Override public Pair<Long, InputStream> getEntry_asLIStream(String tag) throws StorageSystemException {
        return delegation.getEntry_asLIStream(aes.encrypt(tag, getNonce()));
    }
    @Override public TagBasedEncoderBytes addEntry_nocheck(String tag, InputStream content, long content_length) throws StorageSystemException {
        delegation.addEntry_nocheck(aes.encrypt(tag, getNonce()), content, content_length);
        return this;
    }
    @Override public long length(String tag) {
        return delegation.length(aes.encrypt(tag, getNonce()));
    }

    @Override public Iterator<TaggedEntry<byte[]>> iterator() {
        Iterator<TaggedEntry<byte[]>> iterator = delegation.iterator();
        return new Iterator<TaggedEntry<byte[]>>() {
            @Override public boolean hasNext() {
                return iterator.hasNext();
            }
            @Override public TaggedEntry<byte[]> next() {
                TaggedEntry<byte[]> encrypted = iterator.next();
                return new TaggedEntry<>(aes.decrypt(encrypted.tag, getNonce()), encrypted.val);
            }
            @Override public void remove() {
                iterator.remove();
            }
        };
    }

    /**
     * {@inheritDoc}
     * Returns raw encrypted bytes
     */
    @Override public byte[] getEncodedBytes() {
        byte[] encrypted = delegation.getEncodedBytes();

        byte[] encrypted_with_nonce = new byte[16 + encrypted.length];
        System.arraycopy(getNonce(), 0, encrypted_with_nonce, 0, 16);
        System.arraycopy(encrypted, 0, encrypted_with_nonce, 16, encrypted.length);

        return encrypted_with_nonce;
    }
    /**
     * {@inheritDoc}
     * @param encoded_bytes have to be previously encrypted
     */
    @Override public void readFromEncodedBytes(byte[] encoded_bytes) {
        byte[] encrypted_with_nonce = delegation.getEncodedBytes();

        byte[] encrypted = new byte[encrypted_with_nonce.length - 16];
        System.arraycopy(encrypted_with_nonce, 0, getNonce(), 0, 16);
        System.arraycopy(encrypted_with_nonce, 16, encrypted, 0, encrypted.length);
    }



    //simple delegation:

    @Override public TypeToFromRawTransformer<byte[]> createTypeTransformer() {
        return delegation.getTypeTransformer();
    }
    @Override public TransparentBytesStorage getRawStorageSystem() {
        return delegation.getRawStorageSystem();
    }
    @Override public AESTagEncryptingBytesEncoder clear() {
        delegation.clear();
        return this;
    }
}
