package jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.remote.authenticated;

import jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.remote.RemoteEncoderObserverConnection;
import jokrey.utilities.network.mcnp.MCNP_Connection;

/**
 * Observer c to an AuthenticatedRemoteEncoderServer.
 * AuthenticatedRemoteEncoderServer stores these to be able to callback when an update happens.
 *
 * Internal use only.
 */
class AuthenticatedRemoteObserverConnection extends RemoteEncoderObserverConnection {
    final byte[] sess_key;
    AuthenticatedRemoteObserverConnection(MCNP_Connection connection, byte[] sess_key) {
        super(connection);
        this.sess_key = sess_key;
    }
}