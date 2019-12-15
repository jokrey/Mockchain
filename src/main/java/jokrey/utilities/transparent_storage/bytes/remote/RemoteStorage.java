package jokrey.utilities.transparent_storage.bytes.remote;

import jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.remote.RemoteEncoderMCNPCauses;
import jokrey.utilities.network.mcnp.io.MCNP_ClientIO;
import jokrey.utilities.transparent_storage.StorageSystemException;
import jokrey.utilities.transparent_storage.bytes.TransparentBytesStorage;
import jokrey.utilities.transparent_storage.bytes.remote.server.RemoteStorageServer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Implementation of {@link TransparentBytesStorage} writing the bytes into RAM.
 * Uses a bare mcnp c to a remote server. One implementation is: {@link RemoteStorageServer}
 * That remote server actually stores that data.
 *
 * Thread safe when seen as only the storage, but not used with any consecutive operations..
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
 * @author jokrey
 */
public class RemoteStorage implements TransparentBytesStorage {
    private final MCNP_ClientIO client;

    /**
     * Connects to the {@link RemoteStorageServer} at url and port
     *
     * Throws a timeout exception if the c could not be established after three seconds.
     *
     * @param url server url
     * @param port server port
     * @throws IOException when creating the c fails
     */
    public RemoteStorage(String url, int port) throws IOException {
        client = new MCNP_ClientIO(url, port, 3000);
    }

    /**
     * Uses default port defined in {@link RemoteEncoderMCNPCauses#DEFAULT_SERVER_PORT}.
     * @param url server url
     * @throws IOException when creating the c fails
     */
    public RemoteStorage(String url) throws IOException {
        this(url, RemoteStorageMCNPCauses.DEFAULT_SERVER_PORT);
    }

    /**
     * Closes the c to the server
     * @throws IOException when creating the c fails
     */
    @Override public void close() throws IOException {
        client.close();
    }


    @Override public void clear() {
        setContent(new byte[0]);
    }


    @Override public void setContent(byte[] content) throws StorageSystemException {
        try {
            synchronized (client) {
                client.send_cause(RemoteStorageMCNPCauses.SET_CONTENT);
                client.send_variable(content);
                client.flush();
                if (client.receive_byte() == RemoteStorageMCNPCauses.ERROR)
                    throw new StorageSystemException("Server said that set content failed.");
            }
        } catch (IOException e) {
            throw new StorageSystemException("Internal IO-Error("+e.getMessage()+").");
        }
    }


    @Override public byte[] getContent() throws StorageSystemException {
        try {
            synchronized (client) {
                client.send_cause(RemoteStorageMCNPCauses.GET_CONTENT);
                client.flush();
                return client.receive_variable();
            }
        } catch (IOException e) {
            throw new StorageSystemException("Internal IO-Error("+e.getMessage()+").");
        }
    }


    @Override public TransparentBytesStorage delete(long start, long end) throws StorageSystemException {
        try {
            synchronized (client) {
                client.send_cause(RemoteStorageMCNPCauses.DELETE_RANGE);
                client.send_int64(start);
                client.send_int64(end);
                client.flush();
                if (client.receive_byte() == RemoteStorageMCNPCauses.NO_ERROR) //supposed to indicate that no error occurred
                    return this;
                else
                    throw new StorageSystemException("Server said that deletion failed.");
            }
        } catch (IOException e) {
            throw new StorageSystemException("Internal FileStorage-Error("+e.getMessage()+").");
        }
    }

    @Override public TransparentBytesStorage set(long start, byte[] part, int off, int off_end) throws StorageSystemException {
        try {
            synchronized (client) {
                client.send_cause(RemoteStorageMCNPCauses.SET);
                client.send_int64(start);
                client.send_variable(Arrays.copyOfRange(part, off, off_end));
                client.flush();
                if (client.receive_byte() == RemoteStorageMCNPCauses.NO_ERROR) //supposed to indicate that no error occurred
                    return this;
                else
                    throw new StorageSystemException("Server said that setting failed.");
            }
        } catch (IOException e) {
            throw new StorageSystemException("Internal FileStorage-Error("+e.getMessage()+").");
        }
    }

    @Override public TransparentBytesStorage set(long start, byte[] part, int off) throws StorageSystemException {
        return set(start, part, off, part.length);
    }

    @Override public TransparentBytesStorage set(long start, InputStream content, long content_length) throws StorageSystemException {
        try {
            synchronized (client) {
                client.send_cause(RemoteStorageMCNPCauses.SET);
                client.send_int64(start);
                client.send_variable_from(content_length, content);
                client.flush();
//                content.close(); should be done
                byte result = client.receive_byte();
                if(result == RemoteStorageMCNPCauses.NO_ERROR) //supposed to indicate that no error occurred
                    return this;
                else
                    throw new StorageSystemException("Server said that append failed.");
            }
        } catch (IOException ex) {
            throw new StorageSystemException("IO Exception thrown by provided InputStream("+ex.getMessage()+")");
        }
    }


    @Override public TransparentBytesStorage append(byte[] val) throws StorageSystemException {
        try {
            synchronized (client) {
                client.send_cause(RemoteStorageMCNPCauses.APPEND);
                client.send_variable(val);
                client.flush();
                if (client.receive_byte() == RemoteStorageMCNPCauses.NO_ERROR) //supposed to indicate that no error occurred
                    return this;
                else
                    throw new StorageSystemException("Server said that append failed.");
            }
        } catch (IOException e) {
            throw new StorageSystemException("Internal FileStorage-Error("+e.getMessage()+").");
        }
    }


    @Override public TransparentBytesStorage append(InputStream content, long content_length) throws StorageSystemException {
        try {
            synchronized (client) {
                client.send_cause(RemoteStorageMCNPCauses.APPEND);
                client.send_variable_from(content_length, content);
                client.flush();
//                content.close(); should be done
                byte result = client.receive_byte();
                if(result == RemoteStorageMCNPCauses.NO_ERROR) //supposed to indicate that no error occurred
                    return this;
                else
                    throw new StorageSystemException("Server said that append failed.");
            }
        } catch (IOException ex) {
            throw new StorageSystemException("IO Exception thrown by provided InputStream("+ex.getMessage()+")");
        }
    }


    @Override public byte[] sub(long start, long end) throws StorageSystemException {
        try {
            synchronized (client) {
                client.send_cause(RemoteStorageMCNPCauses.SUB_ARRAY);
                client.send_int64(start);
                client.send_int64(end);
                client.flush();
                return client.receive_variable();
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new StorageSystemException("Internal FileStorage-Error("+e.getMessage()+").");
        }
    }


    @Override public InputStream substream(long start, long end) throws StorageSystemException {
        try {
            synchronized (client) {
                client.send_cause(RemoteStorageMCNPCauses.SUB_ARRAY);//same functionality internally.
                client.send_int64(start);
                client.send_int64(end);
                client.flush();
                return client.get_variable_receiver().r;
            }
        } catch (IOException e) {
            throw new StorageSystemException("Internal FileStorage-Error("+e.getMessage()+").");
        }
    }

    @Override public InputStream stream() {
        return substream(0, contentSize());
    }

    @Override public long contentSize() throws StorageSystemException {
        try {
            synchronized (client) {
                client.send_cause(RemoteStorageMCNPCauses.GET_CONTENT_SIZE);//same functionality internally.
                client.flush();
                return client.receive_int64();
            }
        } catch (IOException e) {
            throw new StorageSystemException("Internal FileStorage-Error("+e.getMessage()+").");
        }
    }
}
