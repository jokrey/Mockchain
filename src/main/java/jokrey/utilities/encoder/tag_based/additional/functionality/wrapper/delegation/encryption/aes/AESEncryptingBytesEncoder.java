package jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.encryption.aes;

import jokrey.utilities.encoder.helper.AESHelper;
import jokrey.utilities.encoder.tag_based.TagBasedEncoderBytes;

/**
 * Uses AES with CBC to encrypt content and tag of a provided other TagBasedEncoderBytes,
 *  {@link AESHelper}.
 * It does this by wrapping an {@link AESContentEncryptingBytesEncoder} into an {@link AESTagEncryptingBytesEncoder}.
 *
 * @see AESContentEncryptingBytesEncoder
 * @see AESTagEncryptingBytesEncoder
 * @author jokrey
 */
public final class AESEncryptingBytesEncoder extends AESTagEncryptingBytesEncoder {
    /**
     * Constructor
     * @param delegation the actual tbe
     */
    public AESEncryptingBytesEncoder(TagBasedEncoderBytes delegation) {
        super(new AESContentEncryptingBytesEncoder(delegation));
    }

    /**
     * Constructor
     * @param delegation the actual tbe
     * @param key aes key, should be length 16
     */
    public AESEncryptingBytesEncoder(TagBasedEncoderBytes delegation, byte[] key) {
        this(delegation);
        setKey(key);
    }

    /**
     * Re-initiates the internal aes params.
     * Might make previously encoded stuff using old params inaccessible
     * @param key aes key, should be length 16
     */
    public void setKey(byte[] key) {
        super.setKey(key);
        ((AESContentEncryptingBytesEncoder)delegation).setKey(key);
    }
}
