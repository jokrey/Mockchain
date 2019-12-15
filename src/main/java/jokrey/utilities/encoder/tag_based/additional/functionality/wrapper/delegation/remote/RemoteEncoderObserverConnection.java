package jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.remote;

import jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.cached.CachedTagBasedEncoderBytes;
import jokrey.utilities.network.mcnp.MCNP_Connection;

/**
 * Observer c to an RbaeServer.
 * RbaeServer stores these to be able to callback when an update happens.
 *
 * This class should be used by {@link CachedTagBasedEncoderBytes} as the remote_callback_supplier, if a {@link RemoteEncoderBytes} is cached.
 *
 * @author jokrey
 */
public class RemoteEncoderObserverConnection {
    public final MCNP_Connection connection;
    public RemoteEncoderObserverConnection(MCNP_Connection connection) {
        this.connection = connection;
    }

    @Override public boolean equals(Object obj) {
        return this == obj; //since c has this equals implementation we can only use the same.. (MCNP_ConnectionIO will also never get another equals)
    }
}