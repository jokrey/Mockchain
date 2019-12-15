package jokrey.utilities.transparent_storage.bytes.remote.server;

import jokrey.utilities.encoder.tag_based.TagBasedEncoder;
import jokrey.utilities.network.mcnp.MCNP_Connection;
import jokrey.utilities.network.mcnp.io.ConnectionHandler;
import jokrey.utilities.network.mcnp.io.ConnectionHandler.ConnectionState;
import jokrey.utilities.network.mcnp.io.MCNP_ServerIO;
import jokrey.utilities.transparent_storage.StorageSystemException;
import jokrey.utilities.transparent_storage.bytes.TransparentBytesStorage;
import jokrey.utilities.transparent_storage.bytes.file.FileStorage;
import jokrey.utilities.transparent_storage.bytes.remote.RemoteStorageMCNPCauses;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RemoteStorageServer to be accessed over a network by {@link RemoteStorage}.
 * On top of it's main use as the server backend it additionally is an Implementation of {@link TransparentBytesStorage}.
 *
 *
 * Thread safe when viewed as only Storage.
 *
 * Not thread safe when used as underlying storage for a {@link TagBasedEncoder} or other user classes that require chain calls.
 *    This is because those classes require certain storage operation to be executed atomically.
 *    This is not possible using only this server, because operations cannot be bundled.
 *    For remote encoders use the {@link jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.remote} package.
 *
 * Thread safety and even more importantly a impossibility of the server to know what a clients request is related to.
 * Meaning a locking mechanism on TagBasedEncoder level would have to present, telling the server what it requires to know.
 * This is obviously impossible using just the RemoteStorageSystem.
 * So this can just be used if it is absolutely certain that no two clients will run mutating code at the same time.
 *    As this is never certain in a distributed environment, this is an issue.
 *    However this can still be used as a proxy if a RemoteByteArrayEncoder is run as an intermediate.
 *    It would then take care of the locking, and as it is the only one with knowledge of the servers location it would be save.
 *    However a RemoteByteArrayEncoder typically offers better performance and additional authentication and encryption.
 *
 *
 * @author jokrey
 */
public class RemoteStorageServer implements TransparentBytesStorage {
    private final TransparentBytesStorage delegation;
    private final MCNP_ServerIO server;

    /**
     * Creates server listener socket at default port
     *
     * @see #RemoteStorageServer(int, File)
     * @see #RemoteStorageServer(int, TransparentBytesStorage)
     * @param storage_file data storage file
     * @throws IOException on error setting up the server socket port listener
     */
    public RemoteStorageServer(File storage_file) throws IOException {
        this(RemoteStorageMCNPCauses.DEFAULT_SERVER_PORT, storage_file);
    }

    /**
     * Creates server listener socket at port
     *
     * creates a new FileStorage at storage_file location
     *
     * @see #RemoteStorageServer(int, TransparentBytesStorage)
     * @param port listening to
     * @param storage_file data storage file
     * @throws IOException on error setting up the server socket port listener
     */
    public RemoteStorageServer(int port, File storage_file) throws IOException {
        this(port, new FileStorage(storage_file));
    }

    /**
     * Starts the server. Runs a c listener on a separate thread.
     * If a new c comes in that c will also receive a separate thread(until the c decides to close).
     *     This may be susceptible to a very simple DOS attack(since it could just spam the jvm process with a ton of threads)
     *     But solving it is too much work at this time.
     *     Possible solution: Limit amount of connections coming in from a single ip at the same time.
     *
     * @param port The port for the server to run. Make sure it is accessible from the outside(if desired). Can be between 0 and 65535, should be between 1024 and 65535
     * @param storageSystem can also supply another RemoteStorage here. Then this server will only serve as a proxy
     */
    public RemoteStorageServer(int port, TransparentBytesStorage storageSystem) {
        this.delegation=storageSystem;
        server = new MCNP_ServerIO<>(port, new RemoteStorageServer_ConnectionHandler());
        server.runListenerLoopInThread();
    }

    /**
     * Closes the server
     * @throws IOException on error closing the server socket port listener
     */
    @Override public void close() throws IOException {
        server.close();
    }


    //to hide it from outside access
    private class RemoteStorageServer_ConnectionHandler implements ConnectionHandler<ConnectionState> {
        private AtomicLong global_connection_counter = new AtomicLong(0);
        private AtomicLong current_connection_count = new AtomicLong(0);

        @Override public ConnectionState newConnection(int initial_cause, MCNP_Connection connection) {
            long thread_local_thread_id = global_connection_counter.getAndIncrement();
            long current_connection_count_start = current_connection_count.getAndIncrement();
            System.err.println("Client connected. Number "+thread_local_thread_id+".");
            System.err.println("Number of clients currently connected: "+current_connection_count_start);
            try {
                while(!connection.isClosed()) { //will most likely end with an EOF still though. (Because it hangs in read_cause waiting for a new transmission)
                    handle_request_by(initial_cause, connection);
                    initial_cause = connection.receive_cause();
                }
            } catch(EOFException ex) {
                if(!server.isProperlyClosed()) {
                    if(connection.isClosed())
                        System.err.println("c("+thread_local_thread_id+") closed: "+connection);
                    else {
                        System.err.println("c(" + thread_local_thread_id + ") threw an EOF, but wasn't closed - closing now");
                        try { connection.close(); } catch (Exception e) { e.printStackTrace(); } //just for safety
                    }
                } else {
                    System.err.println("server closed and c("+thread_local_thread_id+") was subsequently closed");
                    try { connection.close(); } catch (Exception e) { e.printStackTrace(); } //just for safety
                }
            } catch(IOException ex) {
                ex.printStackTrace();
            }
            long current_connection_count_end = current_connection_count.getAndDecrement();
            System.err.println("Client disconnected(thread gone). Number "+thread_local_thread_id+".");
            System.err.println("Number of clients currently connected: "+current_connection_count_end);
            return null;
        }
        @Override public ConnectionState handleInteraction(TypedCause type_cause, MCNP_Connection connection, ConnectionState aVoid) { return null; }
    }


    /**
     * Call this method if a client send a new cause.
     * This method is synchronized. Meaning only one thread can ever run this method at a time.
     *
     * @param cause mcnp protocol c cause
     * @param connection the c that send the cause
     * @throws IOException if something goes wrong in between
     */
    private synchronized void handle_request_by(int cause, MCNP_Connection connection) throws IOException {
        switch (cause) {
            case RemoteStorageMCNPCauses.SET_CONTENT:
                handle_set_content_by(connection);
                break;
            case RemoteStorageMCNPCauses.GET_CONTENT:
                handle_get_content_by(connection);
                break;
            case RemoteStorageMCNPCauses.DELETE_RANGE:
                handle_delete_range_by(connection);
                break;
            case RemoteStorageMCNPCauses.APPEND:
                handle_append_by(connection);
                break;
            case RemoteStorageMCNPCauses.SUB_ARRAY:
                handle_sub_array_by(connection);
                break;
            case RemoteStorageMCNPCauses.GET_CONTENT_SIZE:
                handle_get_content_size_by(connection);
                break;
            case RemoteStorageMCNPCauses.SET:
                handle_set_by(connection);
                break;
        }
    }
    private void handle_set_content_by(MCNP_Connection connection) throws IOException {
        byte[] content = connection.receive_variable();
        try {
            setContent(content);
            connection.send_byte(RemoteStorageMCNPCauses.NO_ERROR);
        } catch(StorageSystemException ex) {
            connection.send_byte(RemoteStorageMCNPCauses.ERROR);
        }
    }
    private void handle_set_by(MCNP_Connection connection) throws IOException {
        long start = connection.receive_int64();
        byte[] part = connection.receive_variable();
        try {
            set(start, part);
            connection.send_byte(RemoteStorageMCNPCauses.NO_ERROR);
        } catch(StorageSystemException ex) {
            connection.send_byte(RemoteStorageMCNPCauses.ERROR);
        }
    }

    private void handle_get_content_by(MCNP_Connection connection) throws IOException {
        connection.send_variable(getContent());
    }

    private void handle_delete_range_by(MCNP_Connection connection) throws IOException {
        long start = connection.receive_int64();
        long end = connection.receive_int64();
        try {
            delete(start, end);
            connection.send_byte(RemoteStorageMCNPCauses.NO_ERROR);
        } catch(StorageSystemException ex) {
            connection.send_byte(RemoteStorageMCNPCauses.ERROR);
        }
    }

    private void handle_append_by(MCNP_Connection connection) throws IOException {
        try {
            connection.read_variable_in_parts(this::append);
            connection.send_byte(RemoteStorageMCNPCauses.NO_ERROR);
        } catch(StorageSystemException ex) {
            connection.send_byte(RemoteStorageMCNPCauses.ERROR);
        }
    }

    private void handle_sub_array_by(MCNP_Connection connection) throws IOException {
        try {
            long start = connection.receive_int64();
            long end = connection.receive_int64();
            if(end > contentSize())
                end= contentSize();
            InputStream is = substream(start, end);
            connection.send_variable_from(end - start, is);
        } catch(StorageSystemException ex) {
            ex.printStackTrace();
            throw new IOException("Error handling sub array: "+ex.getMessage());
        }
    }

    private void handle_get_content_size_by(MCNP_Connection connection) throws IOException {
        connection.send_int64(contentSize());
    }










    //-- delegation for it to dabble as a TransparentBytesStorage --

    @Override public synchronized RemoteStorageServer append(InputStream content, long content_length) throws StorageSystemException {
        delegation.append(content, content_length);
        return this;
    }
    @Override public synchronized InputStream substream(long start, long end) throws StorageSystemException {
        return delegation.substream(start, end);
    }

    @Override public InputStream stream() {
        return substream(0, contentSize());
    }

    @Override public synchronized RemoteStorageServer append(byte[] val) throws StorageSystemException {
        delegation.append(val);
        return this;
    }
    @Override public synchronized byte[] sub(long start, long end) {
        return delegation.sub(start, end);
    }
    @Override public synchronized long contentSize() {
        return delegation.contentSize();
    }
    @Override public synchronized void clear() {
        delegation.clear();
    }
    @Override public synchronized void setContent(byte[] content) {
        delegation.setContent(content);
    }
    @Override public synchronized byte[] getContent() {
        return delegation.getContent();
    }
    @Override public synchronized RemoteStorageServer delete(long start, long end) throws StorageSystemException {
        delegation.delete(start, end);
        return this;
    }
    @Override public TransparentBytesStorage set(long start, byte[] part, int off, int off_end) throws StorageSystemException {
        delegation.set(start, part, off, off_end);
        return this;
    }
    @Override public synchronized RemoteStorageServer set(long start, byte[] part, int off) throws StorageSystemException {
        delegation.set(start, part, off);
        return this;
    }
    @Override public synchronized RemoteStorageServer set(long start, byte[] part) throws StorageSystemException {
        delegation.set(start, part);
        return this;
    }
    @Override public RemoteStorageServer set(long start, InputStream part, long part_length) throws StorageSystemException {
        delegation.set(start, part, part_length);
        return this;
    }
}
