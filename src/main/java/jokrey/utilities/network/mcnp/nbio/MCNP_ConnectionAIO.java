package jokrey.utilities.network.mcnp.nbio;

import jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.type.transformer.LITypeToBytesTransformer;
import jokrey.utilities.encoder.type_transformer.TypeToFromRawTransformer;
import jokrey.utilities.network.mcnp.MCNP_Connection;
import jokrey.utilities.network.mcnp.io.ConnectionHandler.ConnectionState;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A simple comfort wrapper for the client side of a MCNP c.
 *
 * Provides Wrapping functionality for all MCNP_HELPER methods.
 * @author jokrey
 */
public class MCNP_ConnectionAIO implements MCNP_Connection {
    /** can obviously also be used directly, for many operations it even has too */
    public final AsynchronousSocketChannel c;
    public MCNP_ConnectionAIO(AsynchronousSocketChannel connection) {
        this.c = connection;
    }

    @Override public void close() throws IOException {
        c.close();
    }
    public boolean isClosed() {
        return !c.isOpen();
    }


    private int timeout = Integer.MAX_VALUE;
    public void setTimeout(int timeout) {
        this.timeout=timeout;
    }


    public void flush() { }
    @Override public void send_fixed(byte[] arr) throws IOException {
        try {
            write_fixed(arr).get(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new IOException("[Channel threw exception: {"+e.getClass().getName()+" - "+e.getMessage()+"}]");
        }
    }

    @Override public int receive_fixed(byte[] b, int off, int len) throws IOException {
        try {
            return c.read(ByteBuffer.wrap(b, off, len)).get(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new IOException("[Channel threw exception: {"+e.getClass().getName()+" - "+e.getMessage()+"}]");
        }
    }





    public Future<Integer> write_fixed(byte[] arr) {
        return c.write(ByteBuffer.wrap(arr));
    }
    public Future<Integer> read_fixed(byte[] b, int off, int len) {
        return c.read(ByteBuffer.wrap(b, off, len));
    }

    public<V> void write_fixed(byte[] arr, V attach, CompletionHandler<Integer, V> handler) {
        c.write(ByteBuffer.wrap(arr), attach, handler);
    }

    public<V> void write_variable(byte[] arr, V attach, CompletionHandler<Integer, V> handler) {
        write_fixed(getTransformer().transform((long) arr.length), attach, new CompletionHandler<Integer, V>() {
            @Override public void completed(Integer result, V attachment) {
                write_fixed(arr, attachment, handler);
            }
            @Override public void failed(Throwable exc, V attachment) {
                handler.failed(exc, attachment);
            }
        });
    }

    public void expect_fixed(int len, ReceivedHandler handler) {
        ByteBuffer buffer = ByteBuffer.allocate(len);
        expect_fixed(buffer, len, handler);
    }
    public void expect_fixed(ByteBuffer buffer, int len, ReceivedHandler handler) {
        c.read(buffer, handler, new CompletionHandler<Integer, ReceivedHandler>() {
            @Override public void completed(Integer read, ReceivedHandler attachment) {
                if(handler==null)return;
                try {
                    buffer.flip();
                    if(read == len) {
                        handler.received(MCNP_ConnectionAIO.this, buffer.array());
                    } else if(read!= -1){
                        throw new RuntimeException("read wrong number of bytes");
                    }
                } catch (IOException e) {
                    throw new RuntimeException("read wrong number of bytes");
                }
            }

            @Override public void failed(Throwable throwable, ReceivedHandler handler) {
                throwable.printStackTrace();
            }
        });
    }
    public void expect_variable(ReceivedHandler handler) {
        expect_fixed(8, (conn, received) -> {
            long length = getTransformer().detransform_long(received);
            expect_fixed((int) length, handler);
        });
    }
    public<V> void expect_variable(ByteBuffer buf, V attachment, CompletionHandler<Integer, V> handler) {
        expect_fixed(8, (conn, received) -> {
            long length = getTransformer().detransform_long(received);
            c.read(buf, attachment, handler);
        });
    }
    public interface ReceivedHandler {
        void received(MCNP_ConnectionAIO conn, byte[] received) throws IOException;
    }

    private ByteBuffer cause_buffer = ByteBuffer.allocate(4);
    public<CT extends ConnectionState> void expect_cause(CT state, CauseReceivedHandler<CT> handler) {
        cause_buffer.clear();
        expect_fixed(cause_buffer, 4, (conn, received) -> handler.received_cause(MCNP_ConnectionAIO.this, getTransformer().detransform_int(received), state));
    }
    public interface CauseReceivedHandler<CT extends ConnectionState> {
        void received_cause(MCNP_ConnectionAIO conn, int cause, CT state) throws IOException;
    }








    @Override public TypeToFromRawTransformer<byte[]> getTransformer() {
        return new LITypeToBytesTransformer();
    }

    //reimplementing default implementation to indicate that this is very much the desired. and should not be changed
    @Override public final boolean equals(Object obj) {
        return this==obj;
    }
    @Override public final int hashCode() {
        return super.hashCode();
    }
}