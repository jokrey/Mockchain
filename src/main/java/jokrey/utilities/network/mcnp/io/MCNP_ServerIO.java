package jokrey.utilities.network.mcnp.io;

import jokrey.utilities.network.mcnp.MCNP_Server;
import jokrey.utilities.network.mcnp.io.ConnectionHandler.ConnectionState;

import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author jokrey
 */
public class MCNP_ServerIO<CT extends ConnectionState> extends MCNP_Server<CT> implements AutoCloseable {
    private ServerSocket serverSocket;

    public MCNP_ServerIO(int port) {
        super(port);
    }
    public MCNP_ServerIO(int port, ConnectionHandler<CT> connectionHandler) {
        super(port, connectionHandler);
    }
    /**Should not be used, too few max_threads may cause max_number_of_concurrent_connections with blocking io*/
    public MCNP_ServerIO(int port, int min_threads, int max_threads) {
        super(port, min_threads, max_threads);
    }
    /**Should not be used, too few max_threads may cause max_number_of_concurrent_connections with blocking io*/
    public MCNP_ServerIO(int port, int min_threads, int max_threads, ConnectionHandler<CT> connectionHandler) {
        super(port, min_threads, max_threads, connectionHandler);
    }

    @Override public void close() throws IOException {
        stop();
        serverSocket.close();
    }
    @Override public boolean isProperlyClosed() {
        return !isRunning() && serverSocket.isClosed();
    }

    public void setConnectionHandler(ConnectionHandler<CT> connectionHandler) {
        this.connectionHandler = connectionHandler;
    }

    public void runListenerLoop() throws IOException {
        if(connectionHandler == null) throw new IllegalStateException("No c handler set.");

        serverSocket = new ServerSocket(port);

        System.out.println("SERVER READY");
        System.out.println("port: "+port);
        System.out.println("serverSocket.getLocalPort: "+serverSocket.getLocalPort());

        try {
            AtomicInteger number = new AtomicInteger(0);
            while (isRunning() && !serverSocket.isClosed()) {
                Socket newConnection = serverSocket.accept();

                Runnable r = () -> {
                    int numberOfSimultaneousConnections = number.getAndIncrement();
                    System.out.println("bef: "+numberOfSimultaneousConnections);
                    MCNP_ConnectionIO conn = null;
                    try {
                        conn = new MCNP_ConnectionIO(newConnection);
                        int initial_cause = conn.receive_cause();
                        CT state = connectionHandler.newConnection(initial_cause, conn);
                        while (!conn.isClosed()) {
                            int cause = conn.receive_cause();
                            state = connectionHandler.handleInteraction(new ConnectionHandler.TypedCause(initial_cause, cause), conn, state);
                        }
                    } catch (EOFException e) {
                        System.err.println("EOF from c(" + conn + "): " + e.getMessage());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    number.getAndDecrement();
                    System.out.println("aft: "+numberOfSimultaneousConnections);
                };
                new Thread(r).start();
//                pool.execute(r);  //this - weirdly - does not work(even with
            }
            System.out.println("server socket closing");

        } catch (IOException e) {
            if(isRunning()) throw e;
        } finally {
            if(!isProperlyClosed())
                close();
        }
    }
}
