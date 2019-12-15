package jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.bytes.remote.storage;

import jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.bytes.LITagBytesEncoder;
import jokrey.utilities.transparent_storage.bytes.TransparentBytesStorage;
import jokrey.utilities.transparent_storage.bytes.remote.server.RemoteStorageServer;

import java.io.File;
import java.io.IOException;

/**
 * A simple wrapper that allows the contents of the RemoteStorageServer to be accessed via a LITagBytesEncoder
 * For notes on thread safety issues see: {@link RemoteStorageServer}
 *
 * @author jokrey
 */
public class LIRemoteStorageServer extends LITagBytesEncoder {
    /**
     * Creates a RemoteStorageServer with provided parameters
     * Provided storage parameter will have to be encoded using LITabBytesEncoder.
     * When accessed remotely a LITabBytesEncoder will also have to be used.
     * @see RemoteStorageServer#RemoteStorageServer(File)
     * @see LITagBytesEncoder#LITagBytesEncoder(TransparentBytesStorage)
     */
    public LIRemoteStorageServer(File storage_file) throws IOException {
        super(new RemoteStorageServer(storage_file));
    }
    /**
     * Creates a RemoteStorageServer with provided parameters
     * Provided storage parameter will have to be encoded using LITabBytesEncoder.
     * When accessed remotely a LITabBytesEncoder will also have to be used.
     * @see RemoteStorageServer#RemoteStorageServer(int, File)
     * @see LITagBytesEncoder#LITagBytesEncoder(TransparentBytesStorage)
     */
    public LIRemoteStorageServer(int port, File storage_file) throws IOException {
        super(new RemoteStorageServer(port, storage_file));
    }
    /**
     * Creates a RemoteStorageServer with provided parameters
     * Provided storage parameter will have to be encoded using LITabBytesEncoder.
     * When accessed remotely a LITabBytesEncoder will also have to be used.
     * @see RemoteStorageServer#RemoteStorageServer(int, TransparentBytesStorage)
     * @see LITagBytesEncoder#LITagBytesEncoder(TransparentBytesStorage)
     */
    public LIRemoteStorageServer(int port, TransparentBytesStorage storage) {
        super(new RemoteStorageServer(port, storage));
    }
}