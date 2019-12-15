package jokrey.utilities.transparent_storage.bytes.remote;

/**
 * Internal MCNP protocol causes.
 *
 * !!Have to be kept consistent with other implementations""
 *
 * @author jokrey
 */
public class RemoteStorageMCNPCauses {
    public static final int DEFAULT_SERVER_PORT = 59182;


    public static final int SET_CONTENT = 0;
    public static final int GET_CONTENT = 1;
    public static final int DELETE_RANGE = 2;
    public static final int APPEND = 3;
    public static final int SUB_ARRAY = 4;
    public static final int GET_CONTENT_SIZE = 5;
    public static final int SET = 6;




    public static final byte NO_ERROR = 13;
    public static final byte ERROR = 66;
}