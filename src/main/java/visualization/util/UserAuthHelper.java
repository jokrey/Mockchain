package visualization.util;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * Contains shortcuts for all required crypto primitives.
 * How the Crypto Modell works(i.e. which crypto primitives are used in which order and why),
 *     can be read under the following link:
 *     https://confluence.cadeia.org/pages/viewpage.action?spaceKey=EVKA18&title=Coin+Crypto+Modell
 */
public class UserAuthHelper {
    /**
     * Decodes the given private_key bytes and provides a {@link PrivateKey}.
     * @param private_key encoded with PKCS8
     * @return usable PrivateKey
     * @throws NoSuchAlgorithmException upon missing crypto primitives - this is a fatal error, the node will not be able to function.
     * @throws InvalidKeySpecException upon invalid private key format
     */
    public static PrivateKey getPrivateKey(byte[] private_key) throws NoSuchAlgorithmException, InvalidKeySpecException {
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(private_key));
    }
    /**
     * Decodes the given public_key bytes and provides a {@link PublicKey}.
     * @param public_key encoded with X509
     * @return usable PublicKey
     * @throws NoSuchAlgorithmException upon missing crypto primitives - this is a fatal error, the node will not be able to function.
     * @throws InvalidKeySpecException upon invalid public key format
     */
    public static PublicKey getPublicKey(byte[] public_key) throws NoSuchAlgorithmException, InvalidKeySpecException {
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(public_key));
    }

    /**
     * Randomly generates a new key pair.
     * @return a new RSA key pair.
     * @throws NoSuchAlgorithmException upon missing crypto primitives - this is a fatal error, the node will not be able to function.
     */
    public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048, new SecureRandom());
        return generator.generateKeyPair();
    }

    /**
     * Reads a key pair from the provided encoded public and private keys.
     * @param publicRaw public key encoded (X509)
     * @param privateRaw private key encoded (pkcs8)
     * @return key pair of the two
     */
    public static KeyPair readKeyPair(byte[] publicRaw, byte[] privateRaw) throws InvalidKeySpecException, NoSuchAlgorithmException {
        return new KeyPair(getPublicKey(publicRaw), getPrivateKey(privateRaw));
    }

    /**
     * Signs the given message with the given private key
     * @param message_to_sign given message
     * @param privateKey given private ke
     * @return a signature that can be later verified with the corresponding public key
     * @throws NoSuchAlgorithmException upon missing crypto primitives - this is a fatal error, the node will not be able to function.
     * @throws InvalidKeyException if the given private key is invalid
     * @throws SignatureException upon invalid signature
     */
    public static byte[] sign(byte[] message_to_sign, PrivateKey privateKey) throws InvalidKeyException, NoSuchAlgorithmException, SignatureException {
        Signature privateSignature = Signature.getInstance("SHA256withRSA");
        privateSignature.initSign(privateKey);
        privateSignature.update(message_to_sign);
        return privateSignature.sign();
    }

    /**
     * Allows verification of a given message.
     * @param message_to_sign given message
     * @param signature given signature
     * @param publicKey given public key, should be corresponding to the private key the message was originally encoded with.
     * @return whether or the verification was correct
     * @throws NoSuchAlgorithmException upon missing crypto primitives - this is a fatal error, the node will not be able to function.
     * @throws InvalidKeyException if the given public key is invalid
     * @throws SignatureException upon invalid signature
     */
    public static boolean verify(byte[] message_to_sign, byte[] signature, PublicKey publicKey) throws InvalidKeyException, NoSuchAlgorithmException, SignatureException {
        Signature publicSignature = Signature.getInstance("SHA256withRSA");
        publicSignature.initVerify(publicKey);
        publicSignature.update(message_to_sign);
        return publicSignature.verify(signature);
    }

    /**
     * Shortcut for: sign(message_to_sign, getPrivateKey(private_key))
     * @see #sign(byte[], PrivateKey)
     * @see #getPrivateKey(byte[])
     */
    public static byte[] sign(byte[] message_to_sign, byte[] private_key) throws InvalidKeySpecException, NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        return sign(message_to_sign, getPrivateKey(private_key));
    }
    /**
     * Shortcut for: verify(message_to_sign, signature, getPublicKey(public_key))
     * @see #verify(byte[], byte[], PublicKey)
     * @see #getPublicKey(byte[])
     */
    public static boolean verify(byte[] message_to_sign, byte[] signature, byte[] public_key) throws InvalidKeySpecException, NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        return verify(message_to_sign, signature, getPublicKey(public_key));
    }


    /**
     * The encrypted private key that is part of every CurrencyBlockChainAccount can be generated using this function
     * @param unencryptedPrivateKey the not yet encrypted private key {@link PrivateKey#getEncoded()}, likely encoded using pkcs8
     * @param key the aes key to use
     * @return a combination of iv and cipher text so that upon decryption only the aes key is required.
     * @throws NoSuchPaddingException upon aes error
     * @throws NoSuchAlgorithmException upon missing crypto primitives - this is a fatal error, the node will not be able to function.
     * @throws InvalidKeyException upon aes error
     * @throws InvalidAlgorithmParameterException upon aes error
     * @throws BadPaddingException upon aes error
     * @throws IllegalBlockSizeException upon aes error
     */
    public static byte[] getEncryptedPrivateKey_withIV(byte[] unencryptedPrivateKey, byte[] key) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, InvalidAlgorithmParameterException, BadPaddingException, IllegalBlockSizeException {
        byte[] iv = new SecureRandom().generateSeed(16);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] encrypted = cipher.doFinal(unencryptedPrivateKey);

        byte[] joined = new byte[16 + encrypted.length];
        System.arraycopy(iv, 0, joined, 0, iv.length);
        System.arraycopy(encrypted, 0, joined, iv.length, encrypted.length);
        return joined;
    }

    /**
     * Allows decrypting the encrypted private key
     * @param withIV the cipher text with the iv, as generated by {@link #getEncryptedPrivateKey_withIV(byte[], byte[])}
     * @param key the aes key to use
     * @return the decrypted key that can then again be used by {@link #getPrivateKey(byte[])}
     * @throws NoSuchPaddingException upon aes error
     * @throws NoSuchAlgorithmException upon missing crypto primitives - this is a fatal error, the node will not be able to function.
     * @throws InvalidKeyException upon aes error
     * @throws InvalidAlgorithmParameterException upon aes error
     * @throws BadPaddingException upon aes error
     * @throws IllegalBlockSizeException upon aes error
     */
    public static byte[] getPrivateKey_FromEncryptedWithIV(byte[] withIV, byte[] key) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        byte[] iv = new byte[16];
        System.arraycopy(withIV, 0, iv, 0, iv.length);
        byte[] encrypted = new byte[withIV.length - iv.length];
        System.arraycopy(withIV, 16, encrypted, 0, encrypted.length);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        return cipher.doFinal(encrypted);
    }

    /**
     * @param aString string to hash
     * @return 16 bytes representing the hash of the given string
     * @throws NoSuchAlgorithmException upon missing crypto primitives - this is a fatal error, the node will not be able to function.
     */
    public static byte[] sha128(String aString) throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-1").digest(aString.getBytes(StandardCharsets.UTF_8));
    }
}