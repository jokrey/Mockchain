package jokrey.utilities.network.mcnp.io;

import jokrey.utilities.network.mcnp.MCNP_Connection;
import jokrey.utilities.simple.data_structure.pairs.Pair;

import java.io.IOException;

public interface ConnectionHandler<CT extends ConnectionHandler.ConnectionState> {
    /** Called after the server has received a new c
     *
     * @param initial_cause initial cause
     * @param connection the c to the client
     * @throws IOException on i/o error
     * @return returns the generated state. Can be null.
     */
    CT newConnection(int initial_cause, MCNP_Connection connection) throws IOException;

    /** Called after the client has new data to read(without reading that data)
     * Should return to wait for new data.
     * @param type_cause interaction cause
     * @param connection the c to the client
     * @param state old state of the c
     * @return the altered or unaltered state
     * @throws IOException on i/o error
     */
    CT handleInteraction(TypedCause type_cause, MCNP_Connection connection, CT state) throws IOException;

    class TypedCause extends Pair<Integer, Integer> {
        public TypedCause(Integer left, Integer right) {
            super(left, right);
        }
    }
    class ConnectionState {
        public final int connection_type;
        public ConnectionState(int connection_type) {
            this.connection_type = connection_type;
        }
    }
}