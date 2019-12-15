package jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.bytes.remote.encoder.authenticated;

import jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.remote.RemoteEncoderMCNPCauses;
import jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.remote.authenticated.AuthenticatedRemoteEncoderServer;
import jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.bytes.tuple.TupleTagSingleLITagEncoderBytesSynchronized;
import jokrey.utilities.transparent_storage.bytes.TransparentBytesStorage;
import jokrey.utilities.transparent_storage.bytes.file.FileStorage;

import java.io.File;
import java.io.IOException;

/**
 * Implementation of RemoteEncoderServer providing additional constructors to automatically use a {@link TupleTagSingleLITagEncoderBytesSynchronized}.
 *
 * @see TupleTagSingleLITagEncoderBytesSynchronized
 * @author jokrey
 */
public class LIAuthenticatedRemoteEncoderServer extends AuthenticatedRemoteEncoderServer {
    /**
     * Creates a new server listener on the default port and writing it's data to storage_file
     * @param storage_file location to create new FileStorage (should either not exist(will attempt to create file), be empty or be previously encoded using LITagBytesEncoder)
     * @throws IOException on error setting up the server socket port listener
     */
    public LIAuthenticatedRemoteEncoderServer(File storage_file) throws IOException {
        this(RemoteEncoderMCNPCauses.DEFAULT_SERVER_PORT, storage_file);
    }

    /**
     * Creates a new server listener on the port and writing it's data to storage_file
     * @param port port to listen to
     * @param storage_file location to create new FileStorage (should either not exist(will attempt to create file), be empty or be previously encoded using LITagBytesEncoder)
     * @throws IOException on error setting up the server socket port listener
     */
    public LIAuthenticatedRemoteEncoderServer(int port, File storage_file) throws IOException {
        this(port, new FileStorage(storage_file));
    }

    /**
     * Creates a new server listener on the port and writing it's data to storage_file
     * @param port port to listen to
     * @param storage storage to save to
     */
    public LIAuthenticatedRemoteEncoderServer(int port, TransparentBytesStorage storage) {
        super(port, new TupleTagSingleLITagEncoderBytesSynchronized(storage));
    }
}
