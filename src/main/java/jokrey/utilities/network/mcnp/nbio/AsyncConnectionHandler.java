package jokrey.utilities.network.mcnp.nbio;

import jokrey.utilities.network.mcnp.io.ConnectionHandler.ConnectionState;
import jokrey.utilities.network.mcnp.io.ConnectionHandler.TypedCause;

import java.io.IOException;

public interface AsyncConnectionHandler<CT extends ConnectionState> {
    /** Called after the server has received a new c
     *
     * @param initial_cause initial cause
     * @param connection the c to the client
     * @throws IOException on i/o error
     * @return returns the generated state. Can be null.
     */
    void newConnection(int initial_cause, MCNP_ConnectionAIO connection, AsyncCauseHandlerMap.InteractionCompletedCallback<CT> callback) throws IOException;

    /** Called after the client has new data to read(without reading that data)
     * Should return to wait for new data.
     * @param connection the c to the client
     * @param type_cause interaction cause
     * @param state old state of the c
     * @return the altered or unaltered state
     * @throws IOException on i/o error
     */
    void handleInteraction(MCNP_ConnectionAIO connection, TypedCause type_cause, CT state, AsyncCauseHandlerMap.InteractionCompletedCallback<CT> callback) throws IOException;
}