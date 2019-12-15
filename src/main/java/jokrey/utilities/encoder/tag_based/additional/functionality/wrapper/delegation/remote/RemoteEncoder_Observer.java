package jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.remote;

import jokrey.utilities.encoder.tag_based.TagEntryObservable;
import jokrey.utilities.encoder.tag_based.TagEntryObserver;
import jokrey.utilities.network.mcnp.io.MCNP_ClientIO;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Allows a user to listen to remote changes. Either from other clients or a client in the same process.
 * One can listen without editing, one can edit without listening.
 */
public class RemoteEncoder_Observer implements AutoCloseable, TagEntryObservable {
    private MCNP_ClientIO observer_client;

    /**
     * Connects to RemoteEncoderServer at url and port.
     *
     * Timeouts after three seconds
     *
     * @param url server url
     * @param port server port
     * @throws IOException on network error
     */
    public RemoteEncoder_Observer(String url, int port) throws IOException {
        observer_client = new MCNP_ClientIO(url, port, 3000);
        new Thread(() -> {
            try {
                observer_client.send_cause(RemoteEncoderMCNPCauses.CONNECTION_TYPE__IS_OBSERVER);
                while (!observer_client.isClosed()) {
                            int update_kind = observer_client.receive_cause();
                            String tag;
                            switch (update_kind) {
                                case RemoteEncoderMCNPCauses.ADD_ENTRY_BYTE_ARR:
                                case RemoteEncoderMCNPCauses.ADD_ENTRY_BYTE_ARR_NOCHECK:
                                    tag = observer_client.receive_utf8();
                                    fire_update_add(tag);
                                    break;
                                case RemoteEncoderMCNPCauses.DELETE_ENTRY_BYTE_ARR:
                                case RemoteEncoderMCNPCauses.DELETE_NO_RETURN:
                                    tag = observer_client.receive_utf8();
                                    fire_update_delete(tag);
                                    break;
                                case RemoteEncoderMCNPCauses.CLEAR:
                                case RemoteEncoderMCNPCauses.SET_CONTENT:
                                    fire_update_set_content();
                                    break;
                                default:
                                    System.out.println("unrecognised update kind detected");
                                    break;
                            }
                }
            } catch (IOException ignored) {
                System.err.println("Observer Socket c closed");
            }
        }).start();
    }

    /**
     * Closes the c to the server
     * @throws IOException on io error closing the underlying network socket c
     */
    @Override public void close() throws IOException {
        observer_client.close();
        listeners.clear();
    }

    private final List<TagEntryObserver> listeners = new ArrayList<>();
    /**
     * Adds TagEntryObserver
     * @param rul the listener has to be not null
     */
    @Override public synchronized void addTagEntryObserver(TagEntryObserver rul) {
        listeners.add(rul);
    }
    /**
     * Removes TagEntryObserver
     * @param rul the listener has to be not null
     */
    public synchronized void removeTagEntryObserver(TagEntryObserver rul) {
        listeners.remove(rul);
    }

    private synchronized void fire_update_add(String tag) {
        for(TagEntryObserver rul:listeners)
            rul.update_add(tag);
    }
    private synchronized void fire_update_delete(String tag) {
        for(TagEntryObserver rul:listeners)
            rul.update_delete(tag);
    }
    private synchronized void fire_update_set_content() {
        for(TagEntryObserver rul:listeners)
            rul.update_set_content();
    }
}
