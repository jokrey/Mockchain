package jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.remote;

/**
 * Connection causes for remote c
 * Interestingly, but not surprisingly also a collection of all methods the TagBasedEncoder API offers
 *
 * !!!!! keep consistent with other implementations !!!!!
 *
 * @author jokrey
 */
public class RemoteEncoderMCNPCauses {
    public static final int DEFAULT_SERVER_PORT = 59183;

    public static final int ADD_ENTRY_BYTE_ARR = 7;
    public static final int ADD_ENTRY_BYTE_ARR_NOCHECK = 18;
    public static final int GET_ENTRY_BYTE_ARR = 29;
    public static final int DELETE_ENTRY_BYTE_ARR = 40;


    public static final int DELETE_NO_RETURN = 43;
    public static final int CLEAR = 44;
    public static final int EXISTS = 45;
    public static final int GET_TAGS = 46;
    public static final int LENGTH = 47;
    public static final int SET_CONTENT = 48;
    public static final int GET_CONTENT = 49;



    public static final int CONNECTION_TYPE__IS_CLIENT = 1;
    public static final int CONNECTION_TYPE__IS_OBSERVER = 2;


    public static final byte NO_ERROR = -13;
    public static final byte ERROR = -66;
    public static final byte TRUE = 1;
    public static final byte FALSE = 0;
}
