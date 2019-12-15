package jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.remote.authenticated;

import jokrey.utilities.encoder.tag_based.TagEntryObservable;
import jokrey.utilities.encoder.tag_based.TagEntryObserver;
import jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.cached.CachedTagBasedEncoderBytes;
import jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.remote.RemoteEncoderMCNPCauses;
import jokrey.utilities.network.mcnp.io.MCNP_ClientIO;
import jokrey.utilities.simple.data_structure.pairs.Pair;
import jokrey.utilities.transparent_storage.StorageSystemException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static jokrey.utilities.encoder.type_transformer.bytes.TypeToBytesTransformer.bytes_to_string;

/**
 * Allows a user to listen to remote changes on their remote profile. Either from other clients or a client in the same process.
 *
 * It is NOT required to also maintain a normal editing c via {@link AuthenticatedRemoteEncoderBytes}.
 * One can listen without editing, one can edit without listening.
 *
 * This class should be used by {@link CachedTagBasedEncoderBytes} as the remote_callback_supplier, if a {@link AuthenticatedRemoteEncoderBytes} is cached.
 *
 * @author jokrey
 */
public class AuthenticatedRemoteEncoder_Observer implements AutoCloseable, TagEntryObservable {
    private final MCNP_ClientIO observer_client;

    /**
     * Connects to RemoteEncoderServer at url and port. And connects to profile using user_name and password.
     *
     * Timeouts after three seconds
     *
     * Throws a StorageSystemException if the login(using user_name and password) fails.
     *
     * @param url server url
     * @param port server port
     * @param user_name user name
     * @param password pw
     * @throws StorageSystemException on any io, crypto or other error
     */
    public AuthenticatedRemoteEncoder_Observer(String url, int port, String user_name, String password) throws StorageSystemException {
        Pair<MCNP_ClientIO, byte[]> client_key = AuthenticatedRemoteEncoderBytes.initialize_connection(url, port, RemoteEncoderMCNPCauses.CONNECTION_TYPE__IS_OBSERVER, user_name, password);
        observer_client = client_key.l;
        byte[] session_key = client_key.r;
        new Thread(() -> {
            try {
                while (!observer_client.isClosed()) {
                            int update_kind = observer_client.receive_cause();
                            byte[] nonce;
                            byte[] tagbytes;
                            String tag;
                            switch (update_kind) {
                                case RemoteEncoderMCNPCauses.ADD_ENTRY_BYTE_ARR:
                                case RemoteEncoderMCNPCauses.ADD_ENTRY_BYTE_ARR_NOCHECK:
                                    nonce = observer_client.receive_fixed(16);
                                    tagbytes = AuthenticationHelper.aes_crt_np_128_decrypt(observer_client.receive_variable(), session_key, nonce);
                                    tag = bytes_to_string(tagbytes);
                                    fire_update_add(tag);
                                    break;
                                case RemoteEncoderMCNPCauses.DELETE_ENTRY_BYTE_ARR:
                                case RemoteEncoderMCNPCauses.DELETE_NO_RETURN:
                                    nonce = observer_client.receive_fixed(16);
                                    tagbytes = AuthenticationHelper.aes_crt_np_128_decrypt(observer_client.receive_variable(), session_key, nonce);
                                    tag = bytes_to_string(tagbytes);
                                    fire_update_delete(tag);
                                    break;
                                case RemoteEncoderMCNPCauses.CLEAR:
                                    fire_update_clear();
                                    break;
                                case AuthenticatedRemoteEncoderMCNPCauses.UNREGISTER_CAUSE:
                                    fire_update_unregister();
                                    break;
                                default:
                                    System.out.println("unrecognised update kind detected");
                                    break;
                            }
                }
            } catch (IOException ignored) {
                System.err.println("Observer Socket c closed");
            } catch (NoSuchAlgorithmException | InvalidKeyException | InvalidAlgorithmParameterException | BadPaddingException | NoSuchPaddingException | IllegalBlockSizeException e) {
                System.err.println("Decryption error");
            }
        }).start();
    }

    /**
     * Closes the c to the server
     * @throws IOException on io error
     */
    @Override public void close() throws IOException {
        observer_client.close();
        listeners.clear();
    }

    private final List<TagEntryObserver> listeners = new CopyOnWriteArrayList<>();
    @Override public void addTagEntryObserver(TagEntryObserver obs) {
        listeners.add(obs);
    }
    @Override public void removeTagEntryObserver(TagEntryObserver obs) {
        listeners.remove(obs);
    }

    private void fire_update_add(String tag) {
        for(TagEntryObserver rul:listeners)
            rul.update_add(tag);
    }
    private void fire_update_delete(String tag) {
        for(TagEntryObserver rul:listeners)
            rul.update_delete(tag);
    }
    private void fire_update_clear() {
        for(TagEntryObserver rul:listeners)
            rul.update_set_content();
    }
    private void fire_update_unregister() {
        for(TagEntryObserver rul:listeners)
            if(rul instanceof AuthenticatedRemoteUpdateListener)
                ((AuthenticatedRemoteUpdateListener)rul).update_unregister();
    }
}
