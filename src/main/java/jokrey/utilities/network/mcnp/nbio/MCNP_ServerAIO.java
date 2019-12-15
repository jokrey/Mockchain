package jokrey.utilities.network.mcnp.nbio;

import jokrey.utilities.network.mcnp.MCNP_Server;
import jokrey.utilities.network.mcnp.io.ConnectionHandler;
import jokrey.utilities.network.mcnp.io.ConnectionHandler.ConnectionState;
import jokrey.utilities.network.mcnp.nbio.MCNP_ConnectionAIO.CauseReceivedHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

/**
 *
 * @author jokrey
 */
public class MCNP_ServerAIO<CT extends ConnectionState> extends MCNP_Server<CT> implements AutoCloseable {
    private AsynchronousServerSocketChannel serverSocket;

    private AsyncConnectionHandler<CT> connectionHandler;

    public MCNP_ServerAIO(int port, int min_threads, int max_threads) {
        super(port, min_threads, max_threads);
    }
    public MCNP_ServerAIO(int port, int min_threads, int max_threads, AsyncConnectionHandler<CT> connectionHandler) {
        this(port, min_threads, max_threads);
        setConnectionHandler(connectionHandler);
    }

    public void setConnectionHandler(AsyncConnectionHandler<CT> connectionHandler) {
        this.connectionHandler = connectionHandler;
    }

    @Override public void close() throws IOException {
        stop();
        serverSocket.close();
    }
    public boolean isProperlyClosed() {
        return !isRunning() && serverSocket.isOpen();
    }

    private CauseReceivedHandler<CT> cause_handler;

    public void runListenerLoop() throws IOException {
        if(connectionHandler == null) throw new IllegalStateException("No c handler set.");

        cause_handler = (conn, cause, state) -> {
            connectionHandler.handleInteraction(conn, new ConnectionHandler.TypedCause(state.connection_type, cause), state,
                    newState -> conn.expect_cause(newState, cause_handler)); //complex recursion
        };


        serverSocket = AsynchronousServerSocketChannel.open();
        serverSocket.bind(new InetSocketAddress("localhost", port));

        System.out.println("SERVER READY");

        CompletionHandler<AsynchronousSocketChannel, Object> connection_acceptor = new CompletionHandler<AsynchronousSocketChannel, Object>() {
            @Override public void completed(AsynchronousSocketChannel asynchronousSocketChannel, Object o) {
                serverSocket.accept(null, this); //recursion
                pool.submit(() -> {
                    new MCNP_ConnectionAIO(asynchronousSocketChannel).expect_cause(null, (CauseReceivedHandler<CT>) (conn, cause, state) -> {
                        connectionHandler.newConnection(cause, conn,
                                newState -> conn.expect_cause(newState, cause_handler));
                    });
                });
            }

            @Override public void failed(Throwable throwable, Object o) {
                throwable.printStackTrace();
            }
        };

        serverSocket.accept(null, connection_acceptor);
    }
}





































//package jokrey.utilities.network.mcnp.mio;
//
//        import jokrey.utilities.network.mcnp.ConnectionHandler;
//        import jokrey.utilities.network.mcnp.ConnectionHandler.ConnectionState;
//        import jokrey.utilities.network.mcnp.io.MCNP_ConnectionIO;
//
//        import java.io.IOException;
//        import java.net.InetSocketAddress;
//        import java.nio.channels.SelectionKey;
//        import java.nio.channels.Selector;
//        import java.nio.channels.ServerSocketChannel;
//        import java.nio.channels.SocketChannel;
//        import java.util.Set;
//        import java.util.concurrent.atomic.AtomicInteger;
//
///**
// *
// * @author jokrey
// */
//public class MCNP_ServerIO<S extends ConnectionState> implements AutoCloseable {
//    private boolean running = true;
//
//    private final ServerSocketChannel serverSocket;
//
//    private final ConnectionHandler<S> connectionHandler;
//
//    private final AtomicInteger numberOfConnections = new AtomicInteger(0);
//    private final Selector[] selectors;
//
//    public MCNP_ServerIO(int port, int nThreads, ConnectionHandler<S> connectionHandler) throws IOException {
//        this.connectionHandler=connectionHandler;
//
//        serverSocket = ServerSocketChannel.open();
//        serverSocket.bind(new InetSocketAddress("localhost", port));
//        serverSocket.configureBlocking(false);
//
//
//        this.selectors = new Selector[nThreads];
//        for(int i=0;i<selectors.length;i++) {
//            Selector selector = Selector.open();
//            selectors[i] = selector;
//            int final_i = i;
//            new Thread(() -> {
//                try {
//                    while(running && serverSocket.isOpen()) {
//                        System.out.println("test1: "+final_i);
//                        int sel_r = selector.select();
//                        System.out.println("test2: "+sel_r);
//
//                        Set<SelectionKey> keys = selector.selectedKeys();
//                        System.out.println("test3: "+keys);
//
//                        for(SelectionKey myKey : keys) {
//                            System.out.println("test4 -for: "+myKey);
//                            if (myKey.isReadable()) {
//                                System.out.println("test5 in if");
//                                SocketChannel readyChannel = (SocketChannel) myKey.channel();
//
////                                readyChannel.configureBlocking(true);
//                                S s = (S) myKey.attachment();
//                                MCNP_ConnectionIO c = new MCNP_ConnectionIO(readyChannel.socket());
//                                int cause = c.read_cause();
//                                S newState = connectionHandler.handleInteraction(c, new ConnectionHandler.TypedCause(s.connection_type, cause), s);
//                                myKey.attach(newState);
////                                readyChannel.configureBlocking(false);
//                            }
//                        }
//                    }
//                } catch(IOException e) {
//                    e.printStackTrace();
//                }
//            }).start();
//        }
//    }
//
//    @Override public void close() throws IOException {
//        running = false;
//        serverSocket.close();
//    }
//    public boolean isProperlyClosed() {
//        return !running && serverSocket.isOpen();
//    }
//
//    public void runListenerLoopInThread() {
//        new Thread(() -> {
//            try {
//                runListenerLoop();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }).start();
//    }
//    public void runListenerLoop() throws IOException {
//        if(serverSocket == null) throw new IllegalStateException("Server Socket has to be previously initialized.");
//
//        System.out.println("SERVER READY");
//
//        Selector selector = Selector.open();
//        SelectionKey selectKy = serverSocket.register(selector, SelectionKey.OP_ACCEPT, null);
//
//        while(running && serverSocket.isOpen()) {
//
//            selector.select();
//
//            Set<SelectionKey> keys = selector.selectedKeys();
//
//            for (SelectionKey myKey : keys) {
//                if (myKey.isAcceptable()) {
//                    SocketChannel newClient = serverSocket.accept();
//                    int connectionID = numberOfConnections.getAndIncrement();
//                    new Thread(() -> {
//                        try {
//                            newClient.configureBlocking(true);
//                            MCNP_ConnectionIO conn = new MCNP_ConnectionIO(newClient.socket());
//                            int initial_cause = conn.read_cause();
//                            S s = connectionHandler.newConnection(initial_cause, conn);
//                            newClient.configureBlocking(false);
//                            System.out.println("bef register");
//                            selectors[connectionID % selectors.length].wakeup();
//                            newClient.register(selectors[connectionID % selectors.length], SelectionKey.OP_READ, s);
//                            System.out.println("registered");
//                        } catch (IOException e) {
//                            e.printStackTrace();
//                        }
//                    }).start();
//                }
//            }
//        }
//        System.out.println("server socket closing");
//        try {
//            close();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
//}








