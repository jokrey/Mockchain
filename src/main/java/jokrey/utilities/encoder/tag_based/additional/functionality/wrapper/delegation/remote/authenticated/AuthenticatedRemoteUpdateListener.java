package jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.remote.authenticated;

import jokrey.utilities.encoder.tag_based.TagEntryObserver;

/**
 * Allows callbacks from the server for successfully completed altering operations on the remote encoder.
 *
 * @author jokrey
 */
public interface AuthenticatedRemoteUpdateListener extends TagEntryObserver {
    /**
     * Called when the remote profile the observer is subscribed to is unregistered.
     */
    void update_unregister();
}