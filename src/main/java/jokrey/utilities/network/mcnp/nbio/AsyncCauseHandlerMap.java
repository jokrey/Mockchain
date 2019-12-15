package jokrey.utilities.network.mcnp.nbio;

import jokrey.utilities.network.mcnp.io.ConnectionHandler.ConnectionState;
import jokrey.utilities.network.mcnp.io.ConnectionHandler.TypedCause;

import java.io.IOException;
import java.util.HashMap;

/**
 * Convenience/Best practise pattern to handle c causes.
 *
 * @author jokrey
 */
public abstract class AsyncCauseHandlerMap<CT extends ConnectionState> implements AsyncConnectionHandler<CT> {
    private final HashMap<TypedCause, Handler<CT>> map = new HashMap<>();
    public boolean cause_handled(TypedCause type_cause) {
        return map.get(type_cause) != null;
    }
    public void add_handler(int type, int cause, Handler<CT> handler) {
        map.put(new TypedCause(type, cause), handler);
    }
    public void remove_handler(TypedCause type_cause) {
        map.remove(type_cause);
    }


    public abstract void newConnection(int initial_cause, MCNP_ConnectionAIO connection, InteractionCompletedCallback<CT> callback) throws IOException;

    public void handleInteraction(MCNP_ConnectionAIO connection, TypedCause type_cause, CT state, InteractionCompletedCallback<CT> callback) throws IOException {
        Handler<CT> handler = map.get(type_cause);
        if(handler==null)
            throw new UnknownCauseException();
        else
            handler.handle(connection, state, callback);
    }


    public interface Handler<CT extends ConnectionState> {
        void handle(MCNP_ConnectionAIO conn, CT state, InteractionCompletedCallback<CT> callback) throws IOException;
    }
    public interface InteractionCompletedCallback<CT extends ConnectionState> {
        void completed(CT newState);
    }
    public static class UnknownCauseException extends RuntimeException {  }
}
