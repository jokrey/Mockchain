package jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.remote.authenticated;

import jokrey.utilities.encoder.helper.ConcurrentMultiMap;
import jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.remote.RemoteEncoderMCNPCauses;
import jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.type.transformer.LITypeToBytesTransformer;
import jokrey.utilities.encoder.tag_based.tuple_tag.DelegatingTupleTagBasedEncoderBytes;
import jokrey.utilities.encoder.tag_based.tuple_tag.SynchronizedTupleTagBasedEncoderBytes;
import jokrey.utilities.encoder.tag_based.tuple_tag.TupleTagBasedEncoderBytes;
import jokrey.utilities.encoder.type_transformer.TypeToFromRawTransformer;
import jokrey.utilities.network.mcnp.MCNP_Connection;
import jokrey.utilities.network.mcnp.io.CauseHandlerMap;
import jokrey.utilities.network.mcnp.io.MCNP_ServerIO;
import jokrey.utilities.simple.data_structure.pairs.Pair;
import jokrey.utilities.transparent_storage.StorageSystemException;
import jokrey.utilities.transparent_storage.bytes.remote.RemoteStorageMCNPCauses;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static jokrey.utilities.encoder.type_transformer.bytes.TypeToBytesTransformer.bytes_to_string;
import static jokrey.utilities.encoder.type_transformer.bytes.TypeToBytesTransformer.string_to_bytes;

/**
 * Detail can be found in the_theory_of_areb.txt
 *
 * Provides a backend server to server many concurrent AuthenticatedRemoteEncoderBytes's.
 * In itself it is also a TupleTagBasedEncoder, where the super tag is interpreted as the user name.
 *
 * Thread safe.
 *
 * @author jokrey
 */
public class AuthenticatedRemoteEncoderServer extends DelegatingTupleTagBasedEncoderBytes<SynchronizedTupleTagBasedEncoderBytes<? extends TupleTagBasedEncoderBytes>> implements AutoCloseable {
    private final MCNP_ServerIO server;
    private final ConcurrentMultiMap<String, AuthenticatedRemoteObserverConnection> observers = new ConcurrentMultiMap<>();

    private final ExecutorService observers_send_thread_pool = Executors.newFixedThreadPool(5);

    /**
     * Creates a new server listening to provided port
     * @param port port at which to listen
     * @param thread_safe_encoder A tuple tag encoder to be used as storage.
     */
    public AuthenticatedRemoteEncoderServer(int port, SynchronizedTupleTagBasedEncoderBytes<? extends TupleTagBasedEncoderBytes> thread_safe_encoder) {
        super(thread_safe_encoder);

        server = new MCNP_ServerIO<>(port, new CauseHandlerMap<AuthenticatedConnectionState>() {
            @Override public AuthenticatedConnectionState newConnection(int initial_cause, MCNP_Connection connection) throws IOException {
                 return handle_connection_initialization(initial_cause, connection);
            }
                
            {
                add_handlers(AuthenticatedRemoteEncoderMCNPCauses.LOGIN_CAUSE);
                add_handlers(AuthenticatedRemoteEncoderMCNPCauses.REGISTER_CAUSE);
            }
            private void add_handlers(int type) {
                add_handler(type, RemoteEncoderMCNPCauses.ADD_ENTRY_BYTE_ARR, (connection2, s2) -> AuthenticatedRemoteEncoderServer.this.handle_add_entry_byte_arr_by(connection2, s2));
                add_handler(type, RemoteEncoderMCNPCauses.ADD_ENTRY_BYTE_ARR_NOCHECK, (connection1, s1) -> AuthenticatedRemoteEncoderServer.this.handle_add_entry_byte_arr_nocheck_by(connection1, s1));
                add_handler(type, RemoteEncoderMCNPCauses.GET_ENTRY_BYTE_ARR, (connection1, s1) -> AuthenticatedRemoteEncoderServer.this.handle_get_entry_byte_arr_by(connection1, s1));
                add_handler(type, RemoteEncoderMCNPCauses.DELETE_ENTRY_BYTE_ARR, (connection, s) -> AuthenticatedRemoteEncoderServer.this.handle_delete_entry_byte_arr_by(connection, s));
                add_handler(type, RemoteEncoderMCNPCauses.DELETE_NO_RETURN, (connection, s) -> AuthenticatedRemoteEncoderServer.this.handle_delete_no_return_by(connection, s));
                add_handler(type, RemoteEncoderMCNPCauses.EXISTS, (connection, s) -> AuthenticatedRemoteEncoderServer.this.handle_exists_by(connection, s));
                add_handler(type, RemoteEncoderMCNPCauses.GET_TAGS, (connection, s) -> AuthenticatedRemoteEncoderServer.this.handle_get_tags_by(connection, s));
                add_handler(type, RemoteEncoderMCNPCauses.LENGTH, (connection1, s1) -> AuthenticatedRemoteEncoderServer.this.handle_length_by(connection1, s1));
                add_handler(type, RemoteEncoderMCNPCauses.CLEAR, (connection1, s1) -> AuthenticatedRemoteEncoderServer.this.handle_clear_by(connection1, s1));
                add_handler(type, AuthenticatedRemoteEncoderMCNPCauses.UNREGISTER_CAUSE, (connection, s) -> AuthenticatedRemoteEncoderServer.this.handle_unregister_by(connection, s));
            }
        });
        server.runListenerLoopInThread();
    }

    @Override public void close() throws IOException {
        server.close();
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

//    @Override public AuthenticatedConnectionState handleConnection(MCNP_Connection c) throws IOException {
//        AuthenticatedConnectionState connectionState;
//        try {
//            connectionState = handle_connection_initialization(initial_cause, c);
//            if(connectionState==null) return;
//        } catch(StorageSystemException ex) {
//            System.err.println("Auth err: "+ex.getMessage());
//            c.close();
//            return;
//        }
//
//        System.err.println("new c authenticated - Auth-User: "+connectionState.user_name);
//
////        c.setTimeout(5000); //This is sadly not possible, since reading the cause might take forever..
//                                           //todo: setting it after and before... maybe
//
//    }

    private static final String PASSWORD_STORE_TAG = "passwords";
    private AuthenticatedConnectionState handle_connection_initialization(int initial_cause, MCNP_Connection connection) throws IOException {
        try {
            byte[] user_name_bytes = connection.receive_variable();
            String user_name = bytes_to_string(user_name_bytes);

            KeyPair myKeyPair = AuthenticationHelper.generate_ec_key_pair();
            byte[] to_send_public_key = AuthenticationHelper.get_raw_public_key_bytes(myKeyPair);

            connection.send_variable(to_send_public_key);
            byte[] received_remote_public_key = connection.receive_variable();


            byte[] exchanged_key = AuthenticationHelper.do_key_exchange(myKeyPair.getPrivate(), to_send_public_key, received_remote_public_key);
            byte[] nonce = connection.receive_fixed(16);
            byte[] encrypted_password = connection.receive_variable();

            byte[] password_received = AuthenticationHelper.aes_crt_np_128_decrypt(encrypted_password, exchanged_key, nonce);

            if (PASSWORD_STORE_TAG.equals(user_name)) {
                connection.send_byte(RemoteStorageMCNPCauses.ERROR);
                throw new StorageSystemException("user name cannot equal password tag");
            }

            //we may require an addEntry after the getEntry.. Therefore we need to lock server until that either happened or is guaranteed not to happen.
            //   since we are just locking the PASSWORD_STORE_TAG only other registrations and unregister's are blocked.
            return delegation.doWriteLocked(PASSWORD_STORE_TAG, (super_tag, encoder) -> {
                byte[] password_on_file = delegation.getEntry(PASSWORD_STORE_TAG, user_name);
                try {
                    switch (initial_cause) {
                        case AuthenticatedRemoteEncoderMCNPCauses.LOGIN_CAUSE:
                            if (password_on_file != null) {
                                if (Arrays.equals(password_received, password_on_file)) {
                                    connection.send_byte(AuthenticatedRemoteEncoderMCNPCauses.LOGIN_SUCCESSFUL);
                                    return new AuthenticatedConnectionState(AuthenticatedRemoteEncoderMCNPCauses.LOGIN_CAUSE, user_name, exchanged_key);
                                } else {
                                    connection.send_byte(AuthenticatedRemoteEncoderMCNPCauses.LOGIN_FAILED_WRONG_PASSWORD);
                                    throw new StorageSystemException("wrong pw");
                                }
                            } else {
                                connection.send_byte(AuthenticatedRemoteEncoderMCNPCauses.LOGIN_FAILED_WRONG_NAME);
                                throw new StorageSystemException("wrong name");
                            }
                        case AuthenticatedRemoteEncoderMCNPCauses.REGISTER_CAUSE:
                            if (password_on_file == null) {
                                delegation.addEntry_nocheck(PASSWORD_STORE_TAG, user_name, password_received); //nocheck because we already checked, by doing the null check above
                                connection.send_byte(AuthenticatedRemoteEncoderMCNPCauses.REGISTER_SUCCESSFUL);
                                return new AuthenticatedConnectionState(AuthenticatedRemoteEncoderMCNPCauses.REGISTER_CAUSE, user_name, exchanged_key);
                            } else {
                                if (Arrays.equals(password_received, password_on_file)) {
                                    connection.send_byte(AuthenticatedRemoteEncoderMCNPCauses.LOGIN_SUCCESSFUL);

                                    return new AuthenticatedConnectionState(AuthenticatedRemoteEncoderMCNPCauses.REGISTER_CAUSE, user_name, exchanged_key);
                                } else {
                                    connection.send_byte(AuthenticatedRemoteEncoderMCNPCauses.REGISTER_FAILED_USER_NAME_TAKEN);
                                    throw new StorageSystemException("name taken");
                                }
                            }
                        case RemoteEncoderMCNPCauses.CONNECTION_TYPE__IS_OBSERVER:
                            if (password_on_file != null) {
                                if (Arrays.equals(password_received, password_on_file)) {
                                    observers.putEntry(user_name, new AuthenticatedRemoteObserverConnection(connection, exchanged_key));
                                    connection.send_byte(AuthenticatedRemoteEncoderMCNPCauses.LOGIN_SUCCESSFUL);
                                    return null;
                                } else {
                                    connection.send_byte(AuthenticatedRemoteEncoderMCNPCauses.LOGIN_FAILED_WRONG_PASSWORD);
                                    throw new StorageSystemException("wrong pw");
                                }
                            } else {
                                connection.send_byte(AuthenticatedRemoteEncoderMCNPCauses.LOGIN_FAILED_WRONG_NAME);
                                throw new StorageSystemException("wrong name");
                            }
                        default:
                            connection.send_byte(RemoteStorageMCNPCauses.ERROR);
                            throw new CauseHandlerMap.UnknownCauseException();
                    }
                } catch(RuntimeException e) {
                    try { connection.send_byte(RemoteEncoderMCNPCauses.ERROR); } catch (IOException ignored) {}
                    throw e;
                } catch (IOException e) {
                    try { connection.send_byte(RemoteEncoderMCNPCauses.ERROR); } catch (IOException ignored) {} //actually pretty unlikely that works,
                                                                                                                            // because the only way an io exception is thrown is through a failed send.....
                    throw new StorageSystemException("IO Exception thrown during send ops: "+e.getMessage());
                }
            });

        } catch (NoSuchAlgorithmException e) {
            throw new StorageSystemException("A required algorithm was not found in the environment("+e.getMessage()+"). Please add the algorithm or contact an admin.");
        } catch (InvalidKeySpecException | InvalidKeyException | BadPaddingException | InvalidAlgorithmParameterException | NoSuchPaddingException | IllegalBlockSizeException e) {
//            c.send_byte(RemoteEncoderMCNPCauses.ERROR);  //not part of the protocol here, on error the problem is severe
            throw new StorageSystemException("other unlikely error: "+e.getMessage());
        }
    }

    private AuthenticatedConnectionState handle_add_entry_byte_arr_by(MCNP_Connection connection, AuthenticatedConnectionState s) throws IOException {
        try {
            String actual_tag = AuthenticationHelper.authenticate_receive_tag(connection, s);

            Pair<Long, InputStream> entry_to_add = connection.get_variable_receiver();
            if(delegation.addEntry(s.user_name, actual_tag, entry_to_add.r, entry_to_add.l))
                connection.send_byte(RemoteEncoderMCNPCauses.TRUE);
            else
                connection.send_byte(RemoteEncoderMCNPCauses.FALSE);

            send_update_callback(RemoteEncoderMCNPCauses.ADD_ENTRY_BYTE_ARR, actual_tag, s.user_name);
        } catch(RuntimeException ex) {
            connection.send_byte(RemoteEncoderMCNPCauses.ERROR);
            throw ex;
        } catch (NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | NoSuchPaddingException | InvalidAlgorithmParameterException | InvalidKeyException e) {
            connection.send_byte(RemoteEncoderMCNPCauses.ERROR);
            throw new StorageSystemException("other exception: "+e.getMessage());
        }
        return s;
    }
    private AuthenticatedConnectionState handle_add_entry_byte_arr_nocheck_by(MCNP_Connection connection, AuthenticatedConnectionState s) throws IOException {
        try {
            String actual_tag = AuthenticationHelper.authenticate_receive_tag(connection, s);

            Pair<Long, InputStream> entry_to_add = connection.get_variable_receiver();

            delegation.addEntry_nocheck(s.user_name, actual_tag, entry_to_add.r, entry_to_add.l);
            connection.send_byte(RemoteEncoderMCNPCauses.NO_ERROR);

            send_update_callback(RemoteEncoderMCNPCauses.ADD_ENTRY_BYTE_ARR_NOCHECK, actual_tag, s.user_name);
        } catch(RuntimeException ex) {
            connection.send_byte(RemoteEncoderMCNPCauses.ERROR);
            throw ex;
        } catch (NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | NoSuchPaddingException | InvalidAlgorithmParameterException | InvalidKeyException e) {
            connection.send_byte(RemoteEncoderMCNPCauses.ERROR);
            throw new StorageSystemException("other exception: "+e.getMessage());
        }
        return s;
    }
    private AuthenticatedConnectionState handle_get_entry_byte_arr_by(MCNP_Connection connection, AuthenticatedConnectionState s) throws IOException {
        try {
            String actual_tag = AuthenticationHelper.authenticate_receive_tag(connection, s);

            delegation.doWriteLocked(s.user_name, (super_tag, encoder) -> {
                try {
                    Pair<Long, InputStream> entry = delegation.getEntry_asLIStream(super_tag, actual_tag);
                    connection.send_variable_from(entry);
                } catch (IOException e) {
                    throw new StorageSystemException("IOException: "+e.getMessage());
                }
                return null;
            });
            //the following is actually much slower(because of RAM copy) than to just block the super tag
//                byte[] entry = delegation.getEntry(s.user_name, actual_tag);
//                c.send_variable_from(entry);
        } catch (NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | NoSuchPaddingException | InvalidAlgorithmParameterException | InvalidKeyException e) {
            connection.start_variable(RemoteEncoderMCNPCauses.ERROR);
            throw new StorageSystemException("other exception: "+e.getMessage());
        } catch(RuntimeException ex) {
            connection.start_variable(RemoteEncoderMCNPCauses.ERROR);
            throw ex;
        }
        return s;
    }
    private AuthenticatedConnectionState handle_delete_entry_byte_arr_by(MCNP_Connection connection, AuthenticatedConnectionState s) throws IOException {
        try {
            String actual_tag = AuthenticationHelper.authenticate_receive_tag(connection, s);

            byte[] entry = delegation.deleteEntry(s.user_name, actual_tag);
            connection.send_variable(entry);

            if(entry != null)
                send_update_callback(RemoteEncoderMCNPCauses.DELETE_ENTRY_BYTE_ARR, actual_tag, s.user_name);
        } catch(RuntimeException ex) {
            connection.start_variable(RemoteEncoderMCNPCauses.ERROR);
            throw ex;
        } catch (NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | NoSuchPaddingException | InvalidAlgorithmParameterException | InvalidKeyException e) {
            connection.start_variable(RemoteEncoderMCNPCauses.ERROR);
            throw new StorageSystemException("other exception: "+e.getMessage());
        }
        return s;
    }
    private AuthenticatedConnectionState handle_delete_no_return_by(MCNP_Connection connection, AuthenticatedConnectionState s) throws IOException {
        try {
            String actual_tag = AuthenticationHelper.authenticate_receive_tag(connection, s);

            boolean entry_deleted = delegation.deleteEntry_noReturn(s.user_name, actual_tag);
            if (entry_deleted)
                connection.send_byte(RemoteEncoderMCNPCauses.TRUE);
            else
                connection.send_byte(RemoteEncoderMCNPCauses.FALSE);

            if(entry_deleted)
                send_update_callback(RemoteEncoderMCNPCauses.DELETE_NO_RETURN, actual_tag, s.user_name);
        } catch(RuntimeException ex) {
            connection.send_byte(RemoteEncoderMCNPCauses.ERROR);
            throw ex;
        } catch (NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | NoSuchPaddingException | InvalidAlgorithmParameterException | InvalidKeyException e) {
            connection.send_byte(RemoteEncoderMCNPCauses.ERROR);
            throw new StorageSystemException("other exception: "+e.getMessage());
        }
        return s;
    }
    private AuthenticatedConnectionState handle_exists_by(MCNP_Connection connection, AuthenticatedConnectionState s) throws IOException {
        try {
            String actual_tag = AuthenticationHelper.authenticate_receive_tag(connection, s);

            if (delegation.exists(s.user_name, actual_tag))
                connection.send_byte(RemoteEncoderMCNPCauses.TRUE);
            else
                connection.send_byte(RemoteEncoderMCNPCauses.FALSE);
        } catch (NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | NoSuchPaddingException | InvalidAlgorithmParameterException | InvalidKeyException e) {
            connection.send_byte(RemoteEncoderMCNPCauses.ERROR);
            throw new StorageSystemException("other exception: "+e.getMessage());
        } catch(RuntimeException r) {
            connection.send_byte(RemoteEncoderMCNPCauses.ERROR);
            throw r;
        }
        return s;
    }
    private AuthenticatedConnectionState handle_get_tags_by(MCNP_Connection connection, AuthenticatedConnectionState s) throws IOException {
                try {
            AuthenticationHelper.authenticate_receive_authenticatable(connection, s);

            byte[] encoded_user_tags = getTypeTransformer().transform(delegation.getTags(s.user_name));
            byte[] nonce = AuthenticationHelper.generate_nonce();
            connection.send_fixed(nonce);
            connection.send_variable(AuthenticationHelper.aes_crt_np_128_encrypt(encoded_user_tags, s.session_key, nonce));
        } catch (NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | NoSuchPaddingException | InvalidAlgorithmParameterException | InvalidKeyException e) {
            connection.start_variable(RemoteEncoderMCNPCauses.ERROR);
            throw new StorageSystemException("other exception: "+e.getMessage());
        } catch(RuntimeException r) {
            connection.start_variable(RemoteEncoderMCNPCauses.ERROR);
            throw r;
        }
        return s;
    }
    private AuthenticatedConnectionState handle_length_by(MCNP_Connection connection, AuthenticatedConnectionState s) throws IOException {
        try {
            String actual_tag = AuthenticationHelper.authenticate_receive_tag(connection, s);

            long length = delegation.length(s.user_name, actual_tag);
            connection.send_int64(length);
        } catch (NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | NoSuchPaddingException | InvalidAlgorithmParameterException | InvalidKeyException e) {
            connection.send_int64(RemoteEncoderMCNPCauses.ERROR);
            throw new StorageSystemException("other exception: "+e.getMessage());
        } catch(RuntimeException r) {
            connection.send_int64(RemoteEncoderMCNPCauses.ERROR);
            throw r;
        }
        return s;
    }
    private AuthenticatedConnectionState handle_unregister_by(MCNP_Connection connection, AuthenticatedConnectionState s) throws IOException {
        try {
            AuthenticationHelper.authenticate_receive_authenticatable(connection, s);

            delegation.doWriteLocked(s.user_name, (super_tag, encoder) -> {
                delegation.deleteEntry_noReturn(PASSWORD_STORE_TAG, s.user_name);
                delegation.clear(s.user_name);
                return null;
            });

            connection.send_byte(RemoteEncoderMCNPCauses.NO_ERROR);

            send_update_callback(AuthenticatedRemoteEncoderMCNPCauses.UNREGISTER_CAUSE, null, s.user_name);
        }catch (NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | NoSuchPaddingException | InvalidAlgorithmParameterException | InvalidKeyException e) {
            connection.send_byte(RemoteEncoderMCNPCauses.ERROR);
            throw new StorageSystemException("other exception: "+e.getMessage());
        } catch (RuntimeException ex) {
            connection.send_byte(RemoteEncoderMCNPCauses.ERROR);
            throw ex;
        }
        return s;
    }
    private AuthenticatedConnectionState handle_clear_by(MCNP_Connection connection, AuthenticatedConnectionState s) throws IOException {
        try {
            AuthenticationHelper.authenticate_receive_authenticatable(connection, s);

            delegation.clear(s.user_name);

            connection.send_byte(RemoteEncoderMCNPCauses.NO_ERROR);

            send_update_callback(RemoteEncoderMCNPCauses.CLEAR, null, s.user_name);
        } catch (RuntimeException ex) {
            connection.send_byte(RemoteEncoderMCNPCauses.ERROR);
            throw ex;
        } catch (NoSuchAlgorithmException | IllegalBlockSizeException | BadPaddingException | NoSuchPaddingException | InvalidAlgorithmParameterException | InvalidKeyException e) {
            connection.send_byte(RemoteEncoderMCNPCauses.ERROR);
            throw new StorageSystemException("other exception: "+e.getMessage());
        }
        return s;
    }

    private void send_update_callback(int operation_done_cause, String tag_altered, String user_name) {
        List<AuthenticatedRemoteObserverConnection> observers_for_user = observers.get(user_name);
        if(observers_for_user==null) return;
        for(AuthenticatedRemoteObserverConnection observer:observers_for_user) {
            MCNP_Connection c = observer.connection;
            if (c.isClosed()) {
                observers.removeEntry(user_name, observer);
            } else {
                //todo: this might execute on the client c thread, might therefore block - it could also throw a RejectedExecutionException.
                observers_send_thread_pool.execute(() -> {
                    try {
                        synchronized (c) {
                            c.send_cause(operation_done_cause);
                            if (tag_altered != null) {
                                byte[] nonce = AuthenticationHelper.generate_nonce();
                                c.send_fixed(nonce);
                                byte[] encrypted = AuthenticationHelper.aes_crt_np_128_encrypt(string_to_bytes(tag_altered), observer.sess_key, nonce);
                                c.send_variable(encrypted);
                            }
                            c.flush();
                        }
                    } catch (IOException e) {
                        System.err.println("An observer couldn't be written to(" + e.getMessage() + "), closing observer c");
                        try {
                            c.close();
                        } catch (Exception ignored) {}
                    } catch (NoSuchAlgorithmException | IllegalBlockSizeException | InvalidKeyException | NoSuchPaddingException | InvalidAlgorithmParameterException | BadPaddingException e) {
                        System.err.println("Encryption error, closing observer c");
                        try {
                            c.close();
                        } catch (Exception ignored) {}
                    }
                });
            }
        }
    }
}
