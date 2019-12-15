package jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.remote;

import jokrey.utilities.encoder.tag_based.TagBasedEncoderBytes;
import jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.thread_safe.SynchronizingTagBasedEncoderBytes;
import jokrey.utilities.network.mcnp.MCNP_Connection;
import jokrey.utilities.network.mcnp.io.CauseHandlerMap;
import jokrey.utilities.network.mcnp.io.ConnectionHandler.ConnectionState;
import jokrey.utilities.network.mcnp.io.MCNP_ServerIO;
import jokrey.utilities.simple.data_structure.pairs.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static jokrey.utilities.encoder.type_transformer.bytes.TypeToBytesTransformer.bytes_to_string;

/**
 * A RemoteEncoderServer to be connected to by {@link RemoteEncoderBytes} and {@link RemoteEncoder_Observer}.
 * In itself is also a LITagBytesEncoder and can therefore easily access the data the clients send.
 *
 * @author jokrey
 */
public class RemoteEncoderServer extends SynchronizingTagBasedEncoderBytes implements AutoCloseable {
    private final MCNP_ServerIO<ConnectionState> server;
    private final CopyOnWriteArrayList<RemoteEncoderObserverConnection> observers = new CopyOnWriteArrayList<>();
    private final ExecutorService observers_send_thread_pool = Executors.newFixedThreadPool(5);

    /**
     * Starts the server. Runs a c listener on a separate thread.
     * If a new c comes in that c will also receive a separate thread(until the c decides to close).
     *     This may be susceptible to a very simple DOS attack(since it could just spam the jvm process with a ton of threads)
     *     But solving it is too much work at this time.
     *     Possible solution: Limit amount of connections coming in from a single ip at the same time.
     *
     * @param port The port for the server to run. Make sure it is accessible from the outside(if desired). Can be between 0 and 65535, should be between 1024 and 65535
     * @param encoder the encoder to store the incoming data in
     */
    public RemoteEncoderServer(int port, TagBasedEncoderBytes encoder) {
        super(encoder);

        server = new MCNP_ServerIO<>(port, new CauseHandlerMap<ConnectionState>() {
            @Override public ConnectionState newConnection(int initial_cause, MCNP_Connection connection) {
                switch (initial_cause) {
                    case RemoteEncoderMCNPCauses.CONNECTION_TYPE__IS_OBSERVER:
                        observers.add(new RemoteEncoderObserverConnection(connection));
                        break;
                    case RemoteEncoderMCNPCauses.CONNECTION_TYPE__IS_CLIENT:
                        break;
                    default:
                        throw new UnknownCauseException();
                }
                return null;
            }

            {
                add_handler(RemoteEncoderMCNPCauses.CONNECTION_TYPE__IS_CLIENT, RemoteEncoderMCNPCauses.ADD_ENTRY_BYTE_ARR, RemoteEncoderServer.this::handle_add_entry_byte_arr_by);
                add_handler(RemoteEncoderMCNPCauses.CONNECTION_TYPE__IS_CLIENT, RemoteEncoderMCNPCauses.ADD_ENTRY_BYTE_ARR_NOCHECK, RemoteEncoderServer.this::handle_add_entry_byte_arr_nocheck_by);
                add_handler(RemoteEncoderMCNPCauses.CONNECTION_TYPE__IS_CLIENT, RemoteEncoderMCNPCauses.GET_ENTRY_BYTE_ARR, RemoteEncoderServer.this::handle_get_entry_byte_arr_by);
                add_handler(RemoteEncoderMCNPCauses.CONNECTION_TYPE__IS_CLIENT, RemoteEncoderMCNPCauses.DELETE_ENTRY_BYTE_ARR, RemoteEncoderServer.this::handle_delete_entry_byte_arr_by);
                add_handler(RemoteEncoderMCNPCauses.CONNECTION_TYPE__IS_CLIENT, RemoteEncoderMCNPCauses.DELETE_NO_RETURN, RemoteEncoderServer.this::handle_delete_no_return_by);
                add_handler(RemoteEncoderMCNPCauses.CONNECTION_TYPE__IS_CLIENT, RemoteEncoderMCNPCauses.EXISTS, RemoteEncoderServer.this::handle_exists_by);
                add_handler(RemoteEncoderMCNPCauses.CONNECTION_TYPE__IS_CLIENT, RemoteEncoderMCNPCauses.GET_TAGS, RemoteEncoderServer.this::handle_get_tags_by);
                add_handler(RemoteEncoderMCNPCauses.CONNECTION_TYPE__IS_CLIENT, RemoteEncoderMCNPCauses.LENGTH, RemoteEncoderServer.this::handle_length_by);
                add_handler(RemoteEncoderMCNPCauses.CONNECTION_TYPE__IS_CLIENT, RemoteEncoderMCNPCauses.SET_CONTENT, RemoteEncoderServer.this::handle_set_content_by);
                add_handler(RemoteEncoderMCNPCauses.CONNECTION_TYPE__IS_CLIENT, RemoteEncoderMCNPCauses.GET_CONTENT, RemoteEncoderServer.this::handle_get_content_by);
                add_handler(RemoteEncoderMCNPCauses.CONNECTION_TYPE__IS_CLIENT, RemoteEncoderMCNPCauses.CLEAR, RemoteEncoderServer.this::handle_clear_by);
            }
        });
        server.runListenerLoopInThread();

    }


    /**
     * Closes the server, releases the port
     * @throws IOException on error closing the server down
     */
    @Override public void close() throws IOException {
        server.close();
    }

//    private final AtomicLong global_connection_counter = new AtomicLong(0); //special annotation for non thread private variable?
//    private final AtomicLong currently_connected_counter = new AtomicLong(0); //special annotation for non thread private variable?
//    //handles new connections from the server
//    public ConnectionState handleConnection(MCNP_Connection c) {
//        long connection_number = global_connection_counter.getAndIncrement();
//        long currently_connected_count = currently_connected_counter.getAndIncrement();
//        System.out.println("Client connected. Number "+connection_number+".");
//        System.out.println("Number of clients currently connected: "+currently_connected_count);
//        try {
//            int read_cause = c.read_cause();
//            if (read_cause == RemoteEncoderMCNPCauses.CONNECTION_TYPE__IS_OBSERVER) {
//
//                observers.add(new RemoteEncoderObserverConnection(c));
//
//            } else if (read_cause == RemoteEncoderMCNPCauses.CONNECTION_TYPE__IS_CLIENT) {
//
////                while (!c.isClosed()) { //will most likely end with an EOF still though.
////                    //     (Because it hangs in read_cause waiting for a new transmission,
////                    //         where it will then receive an eof notification by the socket(using a -1 signal))
////                    int cause = c.read_cause();
////                    try {
////
////                        handle(cause, c);
////                        c.flush();
////
////                    } catch (CauseHandlerMap.UnknownCauseException e) {
////                        System.out.println("client send an unrecognised c cause(" + cause + ").");
////                    } catch (RuntimeException ex) {
////                        ex.printStackTrace();
////                        System.out.println("Error handling request: " + ex.getMessage());
////                        //do not break here. Error's indicated here may only be temporary and the server could therefore still answer valid requests after this.
////                    }
////                }
//
//            } else {
//                System.err.println("client send unrecognised initial c cause");
//            }
//        } catch (EOFException e) {
//            if(!server.isProperlyClosed()  && !c.isClosed()) {
//                //client closed c
//                try { c.close(); } catch (IOException ioe) { ioe.printStackTrace(); } //close c on our side to free potentially used resources
//            }// else - server closed or c closed by this server (both perfectly normal)
//        } catch(IOException e) {
//            e.printStackTrace();
//        }
//        System.out.println("Client disconnected(thread gone). Number "+connection_number+".");
//        System.out.println("Number of clients currently connected: "+currently_connected_counter);
//    }


    //todo succeptible to a denial of service attack.
    //todo The server can be endlessly blocked should someone send a stream with mcnp length indication n, but only supply m(where m < n) bytes without closing the stream
    private ConnectionState handle_add_entry_byte_arr_by(MCNP_Connection connection, ConnectionState state) throws IOException {
        byte[] tag = connection.receive_variable();
        String tag_as_string = bytes_to_string(tag);
        Pair<Long, InputStream> entry_to_add = connection.get_variable_receiver();
        try {
            if(addEntry(tag_as_string, entry_to_add.r, entry_to_add.l))
                connection.send_byte(RemoteEncoderMCNPCauses.TRUE);
            else
                connection.send_byte(RemoteEncoderMCNPCauses.FALSE);
            send_update_callback(RemoteEncoderMCNPCauses.ADD_ENTRY_BYTE_ARR, tag_as_string);
        } catch(RuntimeException ex) {
            connection.send_byte(RemoteEncoderMCNPCauses.ERROR);
            throw ex;
        }
        return state;
    }
    //todo succeptible to a denial of service attack.
    //todo The server can be endlessly blocked should someone send a stream with mcnp length indication n, but only supply m(where m < n) bytes without closing the stream
    private ConnectionState handle_add_entry_byte_arr_nocheck_by(MCNP_Connection connection, ConnectionState state) throws IOException {
        byte[] tag = connection.receive_variable();
        String tag_as_string = bytes_to_string(tag);
        Pair<Long, InputStream> entry_to_add = connection.get_variable_receiver();
        try {
            addEntry_nocheck(tag_as_string, entry_to_add.r, entry_to_add.l);
            connection.send_byte(RemoteEncoderMCNPCauses.NO_ERROR);

            send_update_callback(RemoteEncoderMCNPCauses.ADD_ENTRY_BYTE_ARR_NOCHECK, tag_as_string);
        } catch(RuntimeException ex) {
            connection.send_byte(RemoteEncoderMCNPCauses.ERROR);
            throw ex;
        }
        return state;
    }
    private ConnectionState handle_get_entry_byte_arr_by(MCNP_Connection connection, ConnectionState state) throws IOException {
        byte[] tag = connection.receive_variable();
        String tag_as_string = bytes_to_string(tag);
        try {
            w.lock();  //we require exclusive access to the entire server for this bit
                       //   Because the stream has to be fully read before any changes are done.
                       //   Sadly the getEntry_asStream doesn't guarantee that (for good reason).
                       //   Therefore it has to be done manually by us.
            try {
                Pair<Long, InputStream> entry = getEntry_asLIStream(tag_as_string);
                connection.send_variable_from(entry);
            } finally {
                w.unlock();
            }
        } catch(RuntimeException ex) {
            connection.start_variable(RemoteEncoderMCNPCauses.ERROR);
            throw ex;
        }
        return state;
    }
    private ConnectionState handle_delete_entry_byte_arr_by(MCNP_Connection connection, ConnectionState state) throws IOException {
        byte[] tag = connection.receive_variable();
        String tag_as_string = bytes_to_string(tag);
        try {
            byte[] entry = deleteEntry(tag_as_string);
            connection.send_variable(entry);

            send_update_callback(RemoteEncoderMCNPCauses.DELETE_ENTRY_BYTE_ARR, tag_as_string);
        } catch(RuntimeException ex) {
            connection.start_variable(RemoteEncoderMCNPCauses.ERROR);
            throw ex;
        }
        return state;
    }
    private ConnectionState handle_delete_no_return_by(MCNP_Connection connection, ConnectionState state) throws IOException {
        byte[] tag = connection.receive_variable();
        String tag_as_string = bytes_to_string(tag);
        try {
            if (deleteEntry_noReturn(tag_as_string))
                connection.send_byte(RemoteEncoderMCNPCauses.TRUE);
            else
                connection.send_byte(RemoteEncoderMCNPCauses.FALSE);

            send_update_callback(RemoteEncoderMCNPCauses.DELETE_NO_RETURN, tag_as_string);
        } catch(RuntimeException ex) {
            connection.send_byte(RemoteEncoderMCNPCauses.ERROR);
            throw ex;
        }
        return state;
    }
    private ConnectionState handle_exists_by(MCNP_Connection connection, ConnectionState state) throws IOException {
        byte[] tag = connection.receive_variable();
        try {
            if(exists(bytes_to_string(tag)))
                connection.send_byte(RemoteEncoderMCNPCauses.TRUE);
            else
                connection.send_byte(RemoteEncoderMCNPCauses.FALSE);
        } catch(RuntimeException ex) {
            connection.send_byte(RemoteEncoderMCNPCauses.ERROR);
            throw ex;
        }
        return state;
    }
    private ConnectionState handle_get_tags_by(MCNP_Connection connection, ConnectionState state) throws IOException {
        try {
            byte[] encoded_user_tags = getTypeTransformer().transform(getTags());
            connection.send_variable(encoded_user_tags);
        } catch(RuntimeException ex) {
            connection.start_variable(RemoteEncoderMCNPCauses.ERROR);
            throw ex;
        }
        return state;
    }
    private ConnectionState handle_length_by(MCNP_Connection connection, ConnectionState state) throws IOException {
        byte[] tag = connection.receive_variable();
        String tag_as_string = bytes_to_string(tag);
        try {
            long length = length(tag_as_string);
            connection.send_int64(length);
        } catch(RuntimeException ex) {
            connection.send_int64(RemoteEncoderMCNPCauses.ERROR);
            throw ex;
        }
        return state;
    }
    private ConnectionState handle_get_content_by(MCNP_Connection connection, ConnectionState state) throws IOException {
        try {
            connection.send_variable(getEncodedBytes());
        } catch(RuntimeException ex) {
            connection.start_variable(RemoteEncoderMCNPCauses.ERROR);
            throw ex;
        }
        return state;
    }
    private ConnectionState handle_set_content_by(MCNP_Connection connection, ConnectionState state) throws IOException {
        try {
            readFromEncodedBytes(connection.receive_variable());//should probably read from stream
            connection.send_byte(RemoteEncoderMCNPCauses.NO_ERROR);

            send_update_callback(RemoteEncoderMCNPCauses.SET_CONTENT, null);
        } catch(RuntimeException ex) {
            connection.send_byte(RemoteEncoderMCNPCauses.ERROR);
            throw ex;
        }
        return state;
    }
    private ConnectionState handle_clear_by(MCNP_Connection connection, ConnectionState state) throws IOException {
        try {
            clear();
            connection.send_byte(RemoteEncoderMCNPCauses.NO_ERROR);

            send_update_callback(RemoteEncoderMCNPCauses.CLEAR, null);
        } catch (RuntimeException ex) {
            connection.send_byte(RemoteEncoderMCNPCauses.ERROR);
            throw ex;
        }
        return state;
    }

    private void send_update_callback(int operation_done_cause, String tag_altered) {
        for (int i=0;i<observers.size();i++) {
            RemoteEncoderObserverConnection obs_con = observers.get(i);
            MCNP_Connection c = obs_con.connection;
            if(c.isClosed()) {
                observers.remove(i--);
            } else {
                //todo: this might execute on the client c thread, might therefore block - it could also throw a RejectedExecutionException.
                observers_send_thread_pool.execute(() -> {
                    try {
                        synchronized(c) { //so that multiple observers are not written to at the same time.
                            c.send_cause(operation_done_cause);
                            if (tag_altered != null)
                                c.send_utf8(tag_altered);
                            c.flush();
                        }
                    } catch (IOException e) {
                        System.err.println("An observer couldn't be written to("+e.getMessage()+"), closing observer c"); //todo remove println?  This is one valid way of a dying c...
                        try {
                            c.close();
                        } catch (Exception ignored) {}
                    }
                });
            }
        }
        // don't even wait for the observers to be fully sent. They will be at some point, but the server does not have to wait for that even a little bit.
    }
}
