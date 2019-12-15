package jokrey.utilities.network.mcnp.nbio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.*;

/**
 * Initiates a client mcnp c to a server.
 *
 * @author jokrey
 */
public class MCNP_ClientAIO extends MCNP_ConnectionAIO {
    /**
     * Initiates an mcnp c to the server at url and port, waiting for timeout until that is given up.
     * @param url server ip address or server dns address that resolves to server ip address.
     * @param port a port
     * @param timeout timeout after which to give up on getting the c. (in ms)
     * @throws IOException when the timeout was reached or another io error occurred
     */
    public MCNP_ClientAIO(String url, int port, int timeout) throws IOException, InterruptedException, ExecutionException, TimeoutException {
        super(getConnectionToServer(url, port, timeout));
    }
    protected MCNP_ClientAIO(AsynchronousSocketChannel c) {
        super(c);
    }

    private static AsynchronousSocketChannel getConnectionToServer(String url, int port, int timeout) throws IOException, ExecutionException, InterruptedException, TimeoutException {
        AsynchronousSocketChannel client = AsynchronousSocketChannel.open();
        InetSocketAddress hostAddress = new InetSocketAddress(url, port);
        Future<Void> future = client.connect(hostAddress);
        future.get(timeout, TimeUnit.MILLISECONDS);
        return client;
    }

    public static Future<AsynchronousSocketChannel> connect(String url, int port) throws IOException {
        CompletableFuture<AsynchronousSocketChannel> connection = new CompletableFuture<>();

        AsynchronousSocketChannel client = AsynchronousSocketChannel.open();
        InetSocketAddress hostAddress = new InetSocketAddress(url, port);
        client.connect(hostAddress, client, new CompletionHandler<Void, AsynchronousSocketChannel>() {
            @Override public void completed(Void result, AsynchronousSocketChannel client) {
                connection.complete(client);
            }

            @Override public void failed(Throwable exc, AsynchronousSocketChannel client) {
                connection.completeExceptionally(exc);
            }
        });

        return connection;
    }
}
