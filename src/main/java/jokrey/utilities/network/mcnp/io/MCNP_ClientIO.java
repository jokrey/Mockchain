package jokrey.utilities.network.mcnp.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

/**
 * Initiates a client mcnp c to a server.
 *
 * @author jokrey
 */
public class MCNP_ClientIO extends MCNP_ConnectionIO {
    /**
     * Initiates an mcnp c to the server at url and port, waiting for timeout until that is given up.
     * @param url server ip address or server dns address that resolves to server ip address.
     * @param port a port
     * @param timeout timeout after which to give up on getting the c.
     * @throws IOException when the timeout was reached or another io error occurred
     */
    public MCNP_ClientIO(String url, int port, int timeout) throws IOException {
        super(getConnectionToServer(url, port, timeout));
    }

    private static Socket getConnectionToServer(String url, int port, int timeout) throws IOException {
        SocketAddress address = new InetSocketAddress(url, port);
        Socket serverConnection = new Socket();
        serverConnection.connect(address, timeout);
        return serverConnection;
    }
}
