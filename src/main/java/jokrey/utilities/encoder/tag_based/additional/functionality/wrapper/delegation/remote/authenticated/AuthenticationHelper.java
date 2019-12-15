package jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.remote.authenticated;

import jokrey.utilities.network.mcnp.MCNP_Connection;
import jokrey.utilities.transparent_storage.StorageSystemException;
import sun.security.ec.ECPublicKeyImpl;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

import static jokrey.utilities.encoder.type_transformer.bytes.TypeToBytesTransformer.bytes_to_string;
import static jokrey.utilities.encoder.type_transformer.bytes.TypeToBytesTransformer.string_to_bytes;

/**
 * The functionality here is actually standardized across all implementations of AuthenticatedRemoteEncoderBytes..
 * It has to be..
 * Specs and documentation are in the_theory_of_areb.txt
 *
 * INTERNAL USAGE
 *
 * @author jokrey
 */
final class AuthenticationHelper {
    private static final SecureRandom secure_random = new SecureRandom();

    //ECDH Key Agreement and Exchange...
    public static KeyPair generate_ec_key_pair() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(256, new SecureRandom());
        return kpg.generateKeyPair();
    }
    public static byte[] get_raw_public_key_bytes(KeyPair kp) {
        return ((ECPublicKeyImpl)kp.getPublic()).getEncodedPublicValue();

//        //ANDROID::
//        byte[] enc = kp.getPublic().getEncoded();
//        byte[] actual_without_der = new byte[65];
//        System.arraycopy(enc, enc.length-actual_without_der.length, actual_without_der, 0, actual_without_der.length);
//        return actual_without_der;
    }
    public static PublicKey get_ec_public_key_from(byte[] encoded_pub_key) throws InvalidKeySpecException {
        return convertP256Key(encoded_pub_key);
//        KeyFactory kf = KeyFactory.getInstance("EC");
//        X509EncodedKeySpec pkSpec = new X509EncodedKeySpec(encoded_pub_key);
//        return kf.generatePublic(pkSpec);
    }
    public static byte[] get_ecdh_shared_secret_for(PrivateKey privateKey, byte[] remote_public_key) throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException {
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(privateKey);
        ka.doPhase(get_ec_public_key_from(remote_public_key), true);

        return ka.generateSecret();
    }
    public static byte[] generate_secure_secret(byte[] shared_secret, byte[] pub_key_1, byte[] pub_key_2) throws NoSuchAlgorithmException {
        Boolean pub1bigger = null;
        //supposed to treat the bytes as unsigned..
        for (int i=0;i<pub_key_1.length && pub1bigger==null;i++) {
            if ((pub_key_1[i] & 0xFF) > (pub_key_2[i] & 0xFF)) {
                pub1bigger = true;
            } else if ((pub_key_1[i] & 0xFF) < (pub_key_2[i] & 0xFF)) {
                pub1bigger = false;
            }
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(shared_secret);
        if(pub1bigger==null) { //if pub1bigger == null, then both public keys are equal by impossible chance and the order does not matter.
            digest.update(pub_key_2);
            digest.update(pub_key_1);
        } else if(pub1bigger == true) { //I know, but if i compare against null before, then this would be just confusing to be
            digest.update(pub_key_1);
            digest.update(pub_key_2);
        } else if(pub1bigger == false) { //I know, but if i compare against null before, then this would be just confusing to be
            digest.update(pub_key_2);
            digest.update(pub_key_1);
        }
        return digest.digest();
    }

    public static byte[] do_key_exchange(PrivateKey myPrivateKey, byte[] my_raw_public_key, byte[] received_remote_public_key) throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException {
        byte[] shared_secret = AuthenticationHelper.get_ecdh_shared_secret_for(myPrivateKey, received_remote_public_key);
        return AuthenticationHelper.generate_secure_secret(shared_secret, my_raw_public_key, received_remote_public_key);
    }


    //AES CRT No Padding 128bit
    public static byte[] generate_nonce() {
        return generate_random(16);
    }
    public static byte[] generate_random(int numBytes) {
        return secure_random.generateSeed(numBytes);
    }
    public static byte[] aes_crt_np_128_encrypt(byte[] message, byte[] key, byte[] nonce) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        if(key.length!=16) key = Arrays.copyOfRange(key, 0, 16);
        if(nonce.length!=16) nonce = Arrays.copyOfRange(nonce, 0, 16);

        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(nonce));

        return cipher.doFinal(message);
    }
    public static byte[] aes_crt_np_128_decrypt(byte[] message, byte[] key, byte[] nonce) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        return aes_crt_np_128_encrypt(message, key, nonce);
    }
    public static byte[] sha256(byte[] message) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(message);
        return digest.digest();
    }
    public static String base64(byte[] message) {
        return Base64.getEncoder().encodeToString(message);

//        //ANDROID::
//        return android.util.Base64.encodeToString(message, android.util.Base64.DEFAULT);
    }



    // is it fine to send the nonce like this? Otherwise we might have to permutate the nonce into multiple blocks at least..

    public static void send_tag(MCNP_Connection connection, String tag, byte[] session_key) throws NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, IOException {
        byte[] nonce = AuthenticationHelper.generate_nonce();
        connection.send_fixed(nonce);
        connection.send_variable(AuthenticationHelper.aes_crt_np_128_encrypt(concat(nonce, string_to_bytes(tag)), session_key, nonce));
    }
    public static String authenticate_receive_tag(MCNP_Connection connection, AuthenticatedConnectionState s) throws IOException, NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {
        byte[] nonce = connection.receive_fixed(16);
        byte[][] noncesig_tagbytes = split(AuthenticationHelper.aes_crt_np_128_decrypt(connection.receive_variable(), s.session_key, nonce), 16);
        byte[] nonce_signature = noncesig_tagbytes[0];
        if(!Arrays.equals(nonce, nonce_signature))
            throw new StorageSystemException("Signature Authentication failed. User not verified.");
        byte[] tag_bytes = noncesig_tagbytes[1];
        return bytes_to_string(tag_bytes);
    }

    public static void send_authenticatable(MCNP_Connection connection, byte[] session_key) throws IOException, NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {
        byte[] nonce = AuthenticationHelper.generate_nonce();
        connection.send_fixed(nonce);
        byte[] nonce_signature = AuthenticationHelper.aes_crt_np_128_encrypt(nonce, session_key, nonce);
        connection.send_fixed(nonce_signature);
    }
    public static void authenticate_receive_authenticatable(MCNP_Connection connection, AuthenticatedConnectionState s) throws IOException, NoSuchPaddingException, InvalidKeyException, NoSuchAlgorithmException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {
        byte[] nonce = connection.receive_fixed(16);
        byte[] nonce_signature = AuthenticationHelper.aes_crt_np_128_decrypt(connection.receive_fixed(16), s.session_key, nonce);
        if(!Arrays.equals(nonce, nonce_signature))
            throw new StorageSystemException("Signature Authentication failed. User not verified.");
    }



    //HELPER
    //adapted from https://stackoverflow.com/questions/30445997/loading-raw-64-byte-long-ecdsa-public-key-in-java

    private static final byte[] P256_HEAD = Base64.getDecoder().decode("MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE");
//    //ANDROID::
//    private static final byte[] P256_HEAD = android.util.Base64.decode("MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE", android.util.Base64.DEFAULT);
    public static ECPublicKey convertP256Key(byte[] w) throws InvalidKeySpecException {
        //encoded always starts with a 4..
        if(w[0] != 4) throw new IllegalArgumentException("unexpected first byte (!=4)");
        final byte[] encodedKey = new byte[P256_HEAD.length + w.length-1];
        System.arraycopy(P256_HEAD, 0, encodedKey, 0, P256_HEAD.length);
        System.arraycopy(w, 1, encodedKey, P256_HEAD.length, w.length-1);
        KeyFactory eckf;
        try {
            eckf = KeyFactory.getInstance("EC");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("EC key factory not present in runtime");
        }
        X509EncodedKeySpec ecpks = new X509EncodedKeySpec(encodedKey);
        return (ECPublicKey) eckf.generatePublic(ecpks);
    }

    public static byte[] concat(byte[] b1, byte[] b2) {
        byte[] concat = new byte[b1.length+b2.length];
        System.arraycopy(b1, 0, concat, 0, b1.length);
        System.arraycopy(b2, 0, concat, b1.length, b2.length);
        return concat;
    }
    public static byte[][] split(byte[] b, int pos) {
        byte[] b1 = new byte[pos];
        byte[] b2 = new byte[b.length-pos];
        System.arraycopy(b, 0, b1, 0, b1.length);
        System.arraycopy(b, pos, b2, 0, b2.length);
        return new byte[][] {b1,b2};
    }
}
