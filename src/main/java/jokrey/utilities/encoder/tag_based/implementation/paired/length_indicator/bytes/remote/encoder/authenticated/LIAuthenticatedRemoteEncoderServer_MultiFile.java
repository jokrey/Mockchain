package jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.bytes.remote.encoder.authenticated;

import jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.remote.RemoteEncoderMCNPCauses;
import jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.remote.authenticated.AuthenticatedRemoteEncoderServer;
import jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.bytes.tuple.TupleTagMultiFilesLIEncodersBytesSynchronized;

import java.io.File;
import java.io.IOException;

/**
 * Implementation of RemoteEncoderServer providing additional constructors to automatically use a {@link TupleTagMultiFilesLIEncodersBytesSynchronized}
 *
 * Will employ a directory to store the requests of users.
 * Each user gets their own file
 *
 * @see TupleTagMultiFilesLIEncodersBytesSynchronized
 * @author jokrey
 */
public class LIAuthenticatedRemoteEncoderServer_MultiFile extends AuthenticatedRemoteEncoderServer {
    /**
     * Creates a new server listener on the default port and writing it's data to storage_file
     * @param storage_dir location to create new FileStorage (should either not exist(will attempt to create file), be empty or be previously encoded using LITagBytesEncoder)
     * @throws IOException on error setting up the server socket port listener
     */
    public LIAuthenticatedRemoteEncoderServer_MultiFile(File storage_dir) throws IOException {
        this(RemoteEncoderMCNPCauses.DEFAULT_SERVER_PORT, storage_dir);
    }

    /**
     * Creates a new server listener on the port and writing it's data to storage_file
     * @param port port to listen to
     * @param storage_dir location to create new FileStorage (should either not exist(will attempt to create file), be empty or be previously encoded using LITagBytesEncoder)
     */
    public LIAuthenticatedRemoteEncoderServer_MultiFile(int port, File storage_dir) {
        super(port, new TupleTagMultiFilesLIEncodersBytesSynchronized(storage_dir));
    }
}
