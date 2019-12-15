package jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.remote.authenticated;

import jokrey.utilities.network.mcnp.io.ConnectionHandler;

/**
 * Internal usage only.
 * Provides access to everything an areb cause handler requires.
 *
 * is not, but should be seen as immutable.
 *
 * @author jokrey
 */
class AuthenticatedConnectionState extends ConnectionHandler.ConnectionState {
    final String user_name;
    final byte[] session_key; //yes technically this could be altered and definitely should not, but doing a getter and cloning it each time is too slow!
    AuthenticatedConnectionState(int connection_type, String user_name, byte[] session_key) {
        super(connection_type);
        this.user_name = user_name;
        this.session_key = session_key;
    }
}