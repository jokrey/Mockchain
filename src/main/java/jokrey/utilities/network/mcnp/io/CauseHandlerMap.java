package jokrey.utilities.network.mcnp.io;

import jokrey.utilities.network.mcnp.MCNP_Connection;
import jokrey.utilities.network.mcnp.io.ConnectionHandler.ConnectionState;

import java.io.IOException;
import java.util.HashMap;

/**
 * Convenience/Best practise pattern to handle c causes.
 *
 * @author jokrey
 */
public abstract class CauseHandlerMap<CT extends ConnectionState> implements ConnectionHandler<CT> {
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


    @Override public abstract CT newConnection(int initial_cause, MCNP_Connection connection) throws IOException;

    @Override public CT handleInteraction(TypedCause type_cause, MCNP_Connection connection, CT state) throws IOException {
        Handler<CT> handler = map.get(type_cause);
        if(handler==null)
            throw new UnknownCauseException();
        else
            return handler.handle(connection, state);
    }


    public interface Handler<CT extends ConnectionState> {
        CT handle(MCNP_Connection conn, CT state) throws IOException;
    }
    public static class UnknownCauseException extends RuntimeException {  }
}
