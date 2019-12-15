package jokrey.utilities.network.mcnp.io;

import jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.type.transformer.LITypeToBytesTransformer;
import jokrey.utilities.encoder.type_transformer.TypeToFromRawTransformer;
import jokrey.utilities.network.mcnp.MCNP_Connection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;

/**
 * A simple comfort wrapper for the client side of a MCNP c.
 *
 * Provides Wrapping functionality for all MCNP_HELPER methods.
 * @author jokrey
 */
public class MCNP_ConnectionIO implements MCNP_Connection {
    private final Socket connection;
    private final OutputStream out;
    private final InputStream in;
    public MCNP_ConnectionIO(Socket connection) throws IOException {
        this.connection = connection;
        this.out = connection.getOutputStream();
        this.in = connection.getInputStream();
    }

    @Override public void close() throws IOException {
        connection.getOutputStream().flush();
        connection.close();//also closes streams
    }
    public boolean isClosed() {
        return connection.isClosed();
    }


    public void setTimeout(int timeout) throws SocketException {
        connection.setSoTimeout(timeout);
    }


    public void flush() throws IOException {
        out.flush();
    }

    @Override public void send_fixed(byte[] arr) throws IOException {
        out.write(arr);
    }

    @Override public void send_fixed(byte[] arr, int offset, int limit) throws IOException {
        out.write(arr, offset, limit);
    }

    @Override public int receive_fixed(byte[] b, int off, int len) throws IOException {
        return in.read(b, off, len);
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