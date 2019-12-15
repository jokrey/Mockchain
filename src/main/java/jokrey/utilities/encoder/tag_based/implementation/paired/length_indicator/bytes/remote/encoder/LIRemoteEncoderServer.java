package jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.bytes.remote.encoder;

import jokrey.utilities.encoder.tag_based.TagBasedEncoderBytes;
import jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.remote.RemoteEncoderMCNPCauses;
import jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.remote.RemoteEncoderServer;
import jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.bytes.LITagBytesEncoder;
import jokrey.utilities.transparent_storage.bytes.TransparentBytesStorage;
import jokrey.utilities.transparent_storage.bytes.file.FileStorage;

import java.io.File;
import java.io.IOException;

/**
 * Implementation of RemoteEncoderServer providing additional constructors to automatically use a {@link LITagBytesEncoder}
 *
 * @see LITagBytesEncoder
 * @author jokrey
 */
public class LIRemoteEncoderServer extends RemoteEncoderServer {
    /**
     * Creates a new server listener on the default port and writing it's data to storage_file
     * @see #LIRemoteEncoderServer(int, TransparentBytesStorage)
     * @param storage_file location to create new FileStorage (should either not exist(will attempt to create file), be empty or be previously encoded using LITagBytesEncoder)
     * @throws IOException on error setting up the server socket port listener
     */
    public LIRemoteEncoderServer(File storage_file) throws IOException {
        this(RemoteEncoderMCNPCauses.DEFAULT_SERVER_PORT, storage_file);
    }

    /**
     * Creates a new server listener on the port and writing it's data to storage_file
     * @see #LIRemoteEncoderServer(int, TransparentBytesStorage)
     * @param port port to listen to
     * @param storage_file location to create new FileStorage (should either not exist(will attempt to create file), be empty or be previously encoded using LITagBytesEncoder)
     * @throws IOException on error setting up the server socket port listener
     */
    public LIRemoteEncoderServer(int port, File storage_file) throws IOException {
        this(port, new FileStorage(storage_file));
    }

    /**
     * Creates a new server listener on the port and writing it's data to storageSystem
     * @see RemoteEncoderServer#RemoteEncoderServer(int, TagBasedEncoderBytes)
     * @param port The port for the server to run. Make sure it is accessible from the outside(if desired). Can be between 0 and 65535, should be between 1024 and 65535
     * @param storageSystem the underlying storage system to be used - will be fed into a {@link LITagBytesEncoder}
     */
    public LIRemoteEncoderServer(int port, TransparentBytesStorage storageSystem) {
        super(port, new LITagBytesEncoder(storageSystem));
    }
}