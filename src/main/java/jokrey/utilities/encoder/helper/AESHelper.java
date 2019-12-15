package jokrey.utilities.encoder.helper;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Helper class for aes functionality.
 *
 * Uses AES, ECB and PKCS5Padding.
 *    (Just kidding it uses CBC)
 *
 * Internal usage only (other use is discouraged, as this class may be subject to change)
 *
 * @author jokrey
 */
public class AESHelper {
    private final SecureRandom random = new SecureRandom();

    private SecretKeySpec key;

    /**
     * Requires a setKey call after
     */
    public AESHelper() {}

    /**
     * initializes the key with array
     * @param key raw key, should be 16 bytes
     */
    public AESHelper(byte[] key) {
        setKey(key);
    }


    /**
     * initializes the key with array
     * @param key raw key, should be 16 bytes
     */
    public void setKey(byte[] key) {
        this.key = new SecretKeySpec(key, "AES");
    }

    private Cipher getCipher() throws NoSuchPaddingException, NoSuchAlgorithmException {
        return Cipher.getInstance("AES/CBC/PKCS5Padding");
    }

    /**
     * @return a random byte array of 16 bytes
     */
    private byte[] randomIV() {
        return random.generateSeed(16);
    }

    /**
     * @param bytes to be randomized
     */
    public void randomize(byte[] bytes) {
        random.nextBytes(bytes);
    }

    /**
     * Takes a random nonce, encrypts arr with currently set key and returns a combination of both that can be decrypted using {@link #decrypt(byte[])}
     * Note: every call to this function will return a different result.
     * @param raw array to be encrypted
     * @return nonce + encrypted byte array (at least 16 bytes longer than input)
     */
    public byte[] encrypt(byte[] raw) {
        if(raw == null) return null;
        byte[] nonce = randomIV();
        byte[] encrypted_raw = encrypt(raw, nonce);
        byte[] encrypted_with_nonce = new byte[16 + encrypted_raw.length];
        System.arraycopy(nonce, 0, encrypted_with_nonce, 0, 16);
        System.arraycopy(encrypted_raw, 0, encrypted_with_nonce, 16, encrypted_raw.length);

        return encrypted_with_nonce;
    }

    /**
     * f^-1 for {@link #encrypt(byte[])}
     * @param encrypted_with_nonce 16 nonce bytes plus encrypted array
     * @return decrypted array, at least 16 bytes shorter than input
     */
    public byte[] decrypt(byte[] encrypted_with_nonce) {
        if(encrypted_with_nonce == null) return null;
        byte[] nonce = new byte[16];
        byte[] encrypted_raw = new byte[encrypted_with_nonce.length - 16];
        System.arraycopy(encrypted_with_nonce, 0, nonce, 0, 16);
        System.arraycopy(encrypted_with_nonce, 16, encrypted_raw, 0, encrypted_raw.length);

        return decrypt(encrypted_raw, nonce);
    }

    /**
     * Converts stateStr to bytes using utf8, encrypts it using {@link #encrypt(byte[])} and converts the result to string using Base64
     * @param raw to be encrypted
     * @return encrypted
     */
    public String encrypt(String raw) {
        if(raw == null) return null;
        return Base64.getEncoder().encodeToString(encrypt(raw.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Converts stateStr to bytes using Base64, decrypts it using {@link #decrypt(byte[])} and converts the result to string using utf8
     * @param encrypted_with_nonce to be encrypted
     * @return encrypted
     */
    public String decrypt(String encrypted_with_nonce) {
        if(encrypted_with_nonce == null) return null;
        return new String(decrypt(Base64.getDecoder().decode(encrypted_with_nonce)), StandardCharsets.UTF_8);
    }


    /**
     * Encrypts the provided arr using nonce as aes iv.
     * Note: for decryption the same nonce is required.
     * @param raw to be encrypted
     * @param nonce nonce
     * @return raw encrypted content
     */
    public byte[] encrypt(byte[] raw, byte[] nonce) {
        if(raw == null) return null;
        try {
            Cipher cipher = getCipher();
            cipher.init(Cipher.ENCRYPT_MODE, this.key, new IvParameterSpec(nonce));
            return cipher.doFinal(raw);
        } catch (IllegalBlockSizeException | BadPaddingException | NoSuchPaddingException | NoSuchAlgorithmException | InvalidAlgorithmParameterException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Decrypts the provided arr using nonce as aes iv.
     * @param raw_encrypted to be decrypted
     * @param nonce nonce, same one as used for encryption
     * @return decrypted content
     */
    public byte[] decrypt(byte[] raw_encrypted, byte[] nonce) {
        try {
            Cipher cipher = getCipher();
            IvParameterSpec iv = new IvParameterSpec(nonce);
            cipher.init(Cipher.DECRYPT_MODE, this.key, iv);
            return cipher.doFinal(raw_encrypted);
        } catch (IllegalBlockSizeException | BadPaddingException | NoSuchAlgorithmException | InvalidKeyException | InvalidAlgorithmParameterException | NoSuchPaddingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Converts stateStr to bytes using utf8, encrypts it using {@link #encrypt(byte[], byte[])} and converts the result to string using Base64
     * @param raw to be encrypted
     * @param nonce nonce
     * @return encrypted
     */
    public String encrypt(String raw, byte[] nonce) {
        if(raw == null) return null;
        return Base64.getEncoder().encodeToString(encrypt(raw.getBytes(StandardCharsets.UTF_8), nonce));
    }

    /**
     * Converts stateStr to bytes using Base64, decrypts it using {@link #decrypt(byte[], byte[])} and converts the result to string using utf8
     * @param raw to be encrypted
     * @param nonce nonce, same one as used for encryption
     * @return encrypted
     */
    public String decrypt(String raw, byte[] nonce) {
        if(raw == null) return null;
        return new String(decrypt(Base64.getDecoder().decode(raw), nonce), StandardCharsets.UTF_8);
    }


    /**
     * Returns how many bytes will be required for encrypted storage of a raw byte string.
     * @param raw_unencrypted_length non encrypted length
     * @return encrypted storage length
     */
    public long get_length_of_encrypted_with_nonce(long raw_unencrypted_length) {
        return (raw_unencrypted_length/16 + 1) * 16 + 16; //integer division magic. +16 for nonce, round up to 16 for padding
    }

    /**
     * The provided stream will be encrypted in real time as it is read.
     * The resulting stream will provide 16 initial bytes as the initial vector aes requires.
     * This vector will be previously generated at random.
     *    This iv will be automatically interpreted by {@link #decrypt_stream(InputStream)} }
     * @param raw_supplier raw stream to encrypt
     * @return an encrypting stream
     */
    public InputStream encrypt_stream(InputStream raw_supplier) {
        if(raw_supplier==null) return null;
        try {
            byte[] nonce = randomIV();
            Cipher cipher = getCipher();
            IvParameterSpec iv = new IvParameterSpec(nonce);
            cipher.init(Cipher.ENCRYPT_MODE, this.key, iv);

            CipherInputStream stream = new CipherInputStream(raw_supplier, cipher);
            return new SequenceInputStream(new ByteArrayInputStream(nonce), stream);
        } catch (NoSuchAlgorithmException | InvalidKeyException | InvalidAlgorithmParameterException | NoSuchPaddingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * The provided stream will be decrypted in real time as it is read.
     * The provided streams first 16 bits will be interpreted as the initial vector aes requires.
     * @param encrypted_with_nonce_supplier stream
     * @return a decrypting stream
     */
    public InputStream decrypt_stream(InputStream encrypted_with_nonce_supplier) {
        if(encrypted_with_nonce_supplier==null) return null;
        try {
            byte[] nonce = new byte[16];
            if(encrypted_with_nonce_supplier.read(nonce)==-1)
                throw new RuntimeException("nonce could not be fully read from input stream");

            Cipher cipher = getCipher();
            IvParameterSpec iv = new IvParameterSpec(nonce);
            cipher.init(Cipher.DECRYPT_MODE, this.key, iv);

            return new CipherInputStream(encrypted_with_nonce_supplier, cipher);
        } catch (NoSuchAlgorithmException | InvalidKeyException | InvalidAlgorithmParameterException | NoSuchPaddingException | IOException e) {
            throw new RuntimeException(e);
        }
    }
}