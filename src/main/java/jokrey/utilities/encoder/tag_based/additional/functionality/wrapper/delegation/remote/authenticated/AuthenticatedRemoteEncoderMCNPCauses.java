package jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.remote.authenticated;

import jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.remote.RemoteEncoderMCNPCauses;

/**
 * Connection causes for remote c within areb, that are required in addition to those already defined in {@link RemoteEncoderMCNPCauses}
 *
 *
 * !!!!! keep consistent with other implementations !!!!!
 *
 * @author jokrey
 */
public class AuthenticatedRemoteEncoderMCNPCauses {
    public static final int DEFAULT_SERVER_PORT = 59184;

    static final int LOGIN_CAUSE = 432101;
    static final int REGISTER_CAUSE = 432102;
    static final int UNREGISTER_CAUSE = 432103;


    static final byte LOGIN_SUCCESSFUL = 5;
    static final byte REGISTER_SUCCESSFUL = 6;

    static final byte LOGIN_FAILED_WRONG_PASSWORD = -111;
    static final byte LOGIN_FAILED_WRONG_NAME = -112;
    static final byte REGISTER_FAILED_USER_NAME_TAKEN = -113;
}
