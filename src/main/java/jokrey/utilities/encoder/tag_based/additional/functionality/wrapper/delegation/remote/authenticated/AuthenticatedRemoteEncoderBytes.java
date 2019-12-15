package jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.remote.authenticated;

import jokrey.utilities.encoder.tag_based.TagBasedEncoderBytes;
import jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.remote.RemoteEncoderMCNPCauses;
import jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.type.transformer.LITypeToBytesTransformer;
import jokrey.utilities.encoder.type_transformer.TypeToFromRawTransformer;
import jokrey.utilities.network.mcnp.io.MCNP_ClientIO;
import jokrey.utilities.network.mcnp.io.MCNP_ConnectionIO;
import jokrey.utilities.simple.data_structure.pairs.Pair;
import jokrey.utilities.transparent_storage.StorageSystemException;
import jokrey.utilities.transparent_storage.bytes.TransparentBytesStorage;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Iterator;

/**
 * Detail can be found in the_theory_of_areb.txt
 *
 * AuthenticatedRemoteEncoderBytes is imagined as a lightweight network c to the actual data.
 * Searched for data and similar complex operations are all executed on the server.
 * Multiple clients can see, edit and be notified about changes on that remote data.
 * It differs from RemoteEncoderBytes in that multiple clients do not necessarily see the same data.
 *    To get access to ones own data one has to login using username and password.
 *    After login the client will not notice a difference between RemoteEncoderBytes and this.
 *        AccessTransparency.
 *
 * All calls are synchronous, meaning they only return results when they have them.
 *     This follows the idea of remote procedure calls.
 *     However a timeout can be set.
 *
 * Thread safe. Which is required by the Areb Server
 *
 * @see AuthenticatedRemoteEncoderServer
 * @author jokrey
 */
public class AuthenticatedRemoteEncoderBytes implements TagBasedEncoderBytes, AutoCloseable {
    private final MCNP_ClientIO client;
    private final byte[] session_key;
    private AuthenticatedRemoteEncoderBytes(MCNP_ClientIO client, byte[] session_key) {
        this(client, session_key, -1);
    }
    private AuthenticatedRemoteEncoderBytes(MCNP_ClientIO client, byte[] session_key, int timeout) {
        this.client = client;
        this.session_key=session_key;
        setResultTimeout(timeout);
    }

    @Override public void close() throws IOException {
        client.close();
    }

    /** @see MCNP_ConnectionIO#setTimeout(int) */
    public void setResultTimeout(int timeout) {
        if(timeout<0)timeout=0;
        try {
            client.setTimeout(timeout);
        } catch (SocketException e) {
            throw new StorageSystemException("Error setting timeout: "+e.getMessage());
        }
    }

    /** Unsupported. */
    @Override public TransparentBytesStorage getRawStorageSystem() {
        throw new UnsupportedOperationException("remote storage system is remote");
    }
    /** Unsupported. */
    @Override public Iterator<TaggedEntry<byte[]>> iterator() {
        throw new UnsupportedOperationException("iterator not supported within RBAE");
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

    /**
     * Open a network c to AuthenticatedRemoteEncoderServer at url and port and instantly login using user_name and password
     *
     * Will throw a StorageSystemException if the username or password are wrong or not registered
     *
     * @param url server url
     * @param port server port
     * @param user_name login user name
     * @param password register password
     * @return the newly created AuthenticatedRemoteEncoderBytes
     * @throws StorageSystemException on io errors, crypto error, remote storage system errors and on invalid uname, pw or something
     */
    public static AuthenticatedRemoteEncoderBytes login(String url, int port, String user_name, String password, int timeout) throws StorageSystemException {
        Pair<MCNP_ClientIO, byte[]> pair =  initialize_connection(url, port, AuthenticatedRemoteEncoderMCNPCauses.LOGIN_CAUSE, user_name, password);
        return new AuthenticatedRemoteEncoderBytes(pair.l, pair.r, timeout);
    }
    /** Calls {@link #login(String, int, String, String, int)} with infinite timeout */
    public static AuthenticatedRemoteEncoderBytes login(String url, int port, String user_name, String password) throws StorageSystemException {
        return login(url, port, user_name, password, -1);
    }

    /**
     * Open a network c to AuthenticatedRemoteEncoderServer at url and port and instantly register using user_name and password
     *
     * Will throw a StorageSystemException if the username is taken
     *
     * @param url server url
     * @param port server port
     * @param user_name register user name
     * @param password register password
     * @return the newly created AuthenticatedRemoteEncoderBytes
     * @throws StorageSystemException on io errors, crypto error, remote storage system errors and on invalid uname, pw or something
     */
    public static AuthenticatedRemoteEncoderBytes register(String url, int port, String user_name, String password, int timeout) throws StorageSystemException {
        Pair<MCNP_ClientIO, byte[]> pair = initialize_connection(url, port, AuthenticatedRemoteEncoderMCNPCauses.REGISTER_CAUSE, user_name, password);
        return new AuthenticatedRemoteEncoderBytes(pair.l, pair.r, timeout);
    }
    /** Calls {@link #register(String, int, String, String, int)} with infinite timeout */
    public static AuthenticatedRemoteEncoderBytes register(String url, int port, String user_name, String password) throws StorageSystemException {
        return register(url, port, user_name, password, -1);
    }

    static Pair<MCNP_ClientIO, byte[]> initialize_connection(String url, int port, int cause, String user_name, String password) {
        MCNP_ClientIO client = null;
        try {
            client = new MCNP_ClientIO(url, port, 3000);
            System.out.println("con: "+client);

            client.send_cause(cause);
            client.send_variable(user_name.getBytes(StandardCharsets.UTF_8));

            byte[] server_public_key = client.receive_variable();

            KeyPair myKeyPair = AuthenticationHelper.generate_ec_key_pair();
            byte[] to_send_public_key = AuthenticationHelper.get_raw_public_key_bytes(myKeyPair);
            client.send_variable(to_send_public_key);

            byte[] exchanged_key = AuthenticationHelper.do_key_exchange(myKeyPair.getPrivate(), to_send_public_key, server_public_key);
            byte[] nonce = AuthenticationHelper.generate_nonce();
            byte[] encrypted_password = AuthenticationHelper.aes_crt_np_128_encrypt(AuthenticationHelper.sha256(password.getBytes(StandardCharsets.UTF_8)), exchanged_key, nonce);

            client.send_fixed(nonce);
            client.send_variable(encrypted_password);

            byte result = client.receive_byte();
            switch (result) {
                case AuthenticatedRemoteEncoderMCNPCauses.LOGIN_SUCCESSFUL:
                    return new Pair<>(client, exchanged_key);
                case AuthenticatedRemoteEncoderMCNPCauses.REGISTER_SUCCESSFUL:
                    return new Pair<>(client, exchanged_key);
                case AuthenticatedRemoteEncoderMCNPCauses.REGISTER_FAILED_USER_NAME_TAKEN:
                    client.tryClose();
                    throw new StorageSystemException("name taken");
                case AuthenticatedRemoteEncoderMCNPCauses.LOGIN_FAILED_WRONG_NAME:
                    client.tryClose();
                    throw new StorageSystemException("wrong name");
                case AuthenticatedRemoteEncoderMCNPCauses.LOGIN_FAILED_WRONG_PASSWORD:
                    client.tryClose();
                    throw new StorageSystemException("wrong pw");
                default:
                    client.tryClose();
                    throw new StorageSystemException("server error(" + result + ")");
            }
        } catch (InvalidKeySpecException | InvalidKeyException e) {
            client.tryClose();
            throw new StorageSystemException("Error generating keys - ["+e.getClass().getSimpleName()+": "+e.getMessage()+"]");
        } catch (NoSuchAlgorithmException e) {
            client.tryClose();
            throw new StorageSystemException("Key Exchange error, missing EDCH, AES or SHA256 algorithm? - [NoSuchAlgorithmException: "+e.getMessage()+"]");
        } catch (BadPaddingException | NoSuchPaddingException | IllegalBlockSizeException | InvalidAlgorithmParameterException e) {
            client.tryClose();
            throw new StorageSystemException("Error with AES encryption - ["+e.getClass().getSimpleName()+": "+e.getMessage()+"]");
        } catch (IOException e) {
            e.printStackTrace();
            if(client!=null) client.tryClose();
            throw new StorageSystemException("Error obtaining network c - [IOException: "+e.getMessage()+"]");
        }
    }

    /**
     * Unregisteres client from server, entails loss of all stored data.
     * NOTE: also closes the c to the server, so any subsequent calls will fail.
     *
     * @throws StorageSystemException on any io error
     */
    public synchronized AuthenticatedRemoteEncoderBytes unregister() throws StorageSystemException {
        try {
            client.send_cause(AuthenticatedRemoteEncoderMCNPCauses.UNREGISTER_CAUSE);

            AuthenticationHelper.send_authenticatable(client, session_key);

            byte result = client.receive_byte();
            if(result != RemoteEncoderMCNPCauses.NO_ERROR)
                throw new StorageSystemException("Server returned unexpected("+result+"), error implying message");
            close();
            return this;
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new StorageSystemException("Exception thrown by provided InputStream("+ex.getMessage()+")");
        }
    }





    @Override public synchronized AuthenticatedRemoteEncoderBytes clear() {
        try {
            client.send_cause(RemoteEncoderMCNPCauses.CLEAR);

            AuthenticationHelper.send_authenticatable(client, session_key);

            byte result = client.receive_byte();
            if(result != RemoteEncoderMCNPCauses.NO_ERROR)
                throw new StorageSystemException("Server returned unexpected("+result+"), error implying message");
            return this;
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new StorageSystemException("Exception thrown by provided InputStream("+ex.getMessage()+")");
        }
    }

    @Override public synchronized boolean addEntry(String tag, byte... arr) {
        try {
            client.send_cause(RemoteEncoderMCNPCauses.ADD_ENTRY_BYTE_ARR);

            AuthenticationHelper.send_tag(client, tag, session_key);

            client.send_variable(arr);
            byte result = client.receive_byte();
            if(result== RemoteEncoderMCNPCauses.ERROR)
                throw new StorageSystemException("Error getting that status");
            else if(result== RemoteEncoderMCNPCauses.TRUE)
                return true;
            else if(result== RemoteEncoderMCNPCauses.FALSE)
                return false;
            else
                throw new StorageSystemException("Invalid response("+result+") from server");
        } catch (Exception ex) {
            throw new StorageSystemException("Exception thrown by provided InputStream("+ex.getMessage()+")");
        }
    }

    @Override public synchronized AuthenticatedRemoteEncoderBytes addEntry_nocheck(String tag, byte[] arr) throws StorageSystemException {
        try {
            client.send_cause(RemoteEncoderMCNPCauses.ADD_ENTRY_BYTE_ARR_NOCHECK);

            AuthenticationHelper.send_tag(client, tag, session_key);

            client.send_variable(arr);
            byte result = client.receive_byte();
            if(result != RemoteEncoderMCNPCauses.NO_ERROR) //supposed to indicate that no error occurred
                throw new StorageSystemException("Server returned unexpected("+result+"), error implying message");
        } catch (Exception ex) {
            throw new StorageSystemException("IO Exception thrown by provided InputStream("+ex.getMessage()+")");
        }
        return this;
    }

    @Override public synchronized byte[] getEntry(String tag) throws StorageSystemException {
        try {
            client.send_cause(RemoteEncoderMCNPCauses.GET_ENTRY_BYTE_ARR);

            AuthenticationHelper.send_tag(client, tag, session_key);

            return client.receive_variable();
        } catch (Exception e) {
            throw new StorageSystemException(e);
        }
    }

    @Override public synchronized byte[] deleteEntry(String tag) throws StorageSystemException {
        try {
            client.send_cause(RemoteEncoderMCNPCauses.DELETE_ENTRY_BYTE_ARR);

            AuthenticationHelper.send_tag(client, tag, session_key);

            return client.receive_variable();
        } catch (Exception e) {
            throw new StorageSystemException(e);
        }
    }

    @Override public synchronized boolean deleteEntry_noReturn(String tag) {
        try {
            client.send_cause(RemoteEncoderMCNPCauses.DELETE_NO_RETURN);

            AuthenticationHelper.send_tag(client, tag, session_key);

            byte result = client.receive_byte();
            if(result== RemoteEncoderMCNPCauses.ERROR)
                throw new StorageSystemException("Error getting that status");
            else if(result== RemoteEncoderMCNPCauses.TRUE)
                return true;
            else if(result== RemoteEncoderMCNPCauses.FALSE)
                return false;
            else
                throw new StorageSystemException("Invalid response from server");
        } catch (Exception e) {
            throw new StorageSystemException(e);
        }
    }

    @Override public synchronized boolean exists(String tag) {
        try {
            client.send_cause(RemoteEncoderMCNPCauses.EXISTS);

            AuthenticationHelper.send_tag(client, tag, session_key);

            byte result = client.receive_byte();
            if(result== RemoteEncoderMCNPCauses.ERROR)
                throw new StorageSystemException("Error getting that status");
            else if(result== RemoteEncoderMCNPCauses.TRUE)
                return true;
            else if(result== RemoteEncoderMCNPCauses.FALSE)
                return false;
            else
                throw new StorageSystemException("Invalid response from server");
        } catch (Exception e) {
            throw new StorageSystemException(e);
        }
    }

    @Override public synchronized String[] getTags() {
        try {
            client.send_cause(RemoteEncoderMCNPCauses.GET_TAGS);

            AuthenticationHelper.send_authenticatable(client, session_key);

            byte[] nonce = client.receive_fixed(16);

            byte[] encoded_tags = AuthenticationHelper.aes_crt_np_128_decrypt(client.receive_variable(), session_key, nonce);
            return getTypeTransformer().detransform_array(encoded_tags, String[].class);
        } catch (Exception e) {
            throw new StorageSystemException(e);
        }
    }

    @Override public synchronized long length(String tag) throws StorageSystemException {
        try {
            client.send_cause(RemoteEncoderMCNPCauses.LENGTH);

            AuthenticationHelper.send_tag(client, tag, session_key);

            long result = client.receive_int64();
            if(result== RemoteEncoderMCNPCauses.ERROR)
                throw new StorageSystemException("Error getting length .");
            else
                return result;
        } catch (Exception e) {
            throw new StorageSystemException(e);
        }
    }

    @Override public synchronized boolean addEntry(String tag, InputStream content, long content_length) throws StorageSystemException {
        try {
            client.send_cause(RemoteEncoderMCNPCauses.ADD_ENTRY_BYTE_ARR);

            AuthenticationHelper.send_tag(client, tag, session_key);

            client.send_variable_from(content_length, content);
            byte result = client.receive_byte();
            if(result== RemoteEncoderMCNPCauses.ERROR)
                throw new StorageSystemException("Error getting that status");
            else if(result== RemoteEncoderMCNPCauses.TRUE)
                return true;
            else if(result== RemoteEncoderMCNPCauses.FALSE)
                return false;
            else
                throw new StorageSystemException("Invalid response("+result+") from server");
        } catch (Exception ex) {
            throw new StorageSystemException("IO Exception thrown by provided InputStream("+ex.getMessage()+")");
        }
    }

    @Override public synchronized Pair<Long, InputStream> getEntry_asLIStream(String tag) throws StorageSystemException {
        try {
            client.send_cause(RemoteEncoderMCNPCauses.GET_ENTRY_BYTE_ARR);

            AuthenticationHelper.send_tag(client, tag, session_key);

            return client.get_variable_receiver();
        } catch (Exception e) {
            throw new StorageSystemException(e);
        }
    }

    @Override public synchronized AuthenticatedRemoteEncoderBytes addEntry_nocheck(String tag, InputStream content, long content_length) throws StorageSystemException {
        try {
            client.send_cause(RemoteEncoderMCNPCauses.ADD_ENTRY_BYTE_ARR_NOCHECK);

            AuthenticationHelper.send_tag(client, tag, session_key);

            client.send_variable_from(content_length, content);
            byte result = client.receive_byte();
            if(result != RemoteEncoderMCNPCauses.NO_ERROR) //supposed to indicate that no error occurred
                throw new StorageSystemException("Server returned unexpected("+result+"), error implying message");
        } catch (Exception ex) {
            throw new StorageSystemException("IO Exception thrown by provided InputStream("+ex.getMessage()+")");
        }
        return this;
    }


    //illegal ops blocked
    /** Not supported */
    @Override public void readFromEncodedBytes(byte[] encoded_bytes) {
        throw new StorageSystemException("\"readFromEncodedBytes\" not an allowed operation with areb");
    }
    /** Not supported */
    @Override public byte[] getEncodedBytes() throws StorageSystemException {
        throw new StorageSystemException("\"getEncodedBytes\" not an allowed operation with areb");
    }
}
