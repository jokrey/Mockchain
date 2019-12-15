package jokrey.utilities.network.mcnp;

import jokrey.utilities.encoder.type_transformer.TypeToFromRawTransformer;
import jokrey.utilities.simple.data_structure.pairs.Pair;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public interface MCNP_Connection extends AutoCloseable {
    long NULL_INDICATOR = -1;

    default void tryClose() {
        try { close(); } catch (Exception ignored) {}
    }
    boolean isClosed();


    void setTimeout(int timeout) throws IOException;

    void flush() throws IOException;
    void send_fixed(byte[] arr) throws IOException;
    default void send_fixed(byte[] arr, int offset, int limit) throws IOException {
        if(offset == 0 && limit == arr.length)
            send_fixed(arr);
        else
            send_fixed(Arrays.copyOfRange(arr, offset, limit));
    }
    int receive_fixed(byte[] b, int off, int len) throws IOException;
    default byte[] receive_fixed(int length) throws IOException {
        byte[] total = new byte[length];
        byte[] buffer = new byte[1024*4];
        long byteCounter = 0;
        while(byteCounter<length) {
            int read = receive_fixed(buffer, 0, Math.min(buffer.length, (int) (length-byteCounter)));
            if(read == -1)
                throw new EOFException();
            System.arraycopy(buffer, 0, total, (int) byteCounter, read);
            byteCounter+=read;
        }

        if(byteCounter == length)
            return total;
        else
            throw new IOException("Could not read promised amount of bytes (read: "+byteCounter+", expected:"+length+".");
    }


    default void send_cause(int cause) throws IOException {
        send_int32(cause);
    }
    default int receive_cause() throws IOException {
        return receive_int32();
    }

    default void start_variable(long variable_length) throws IOException {
        send_int64(variable_length);
    }
    default void send_variable(byte[] arr) throws IOException {
        if(arr == null) {
            start_variable(NULL_INDICATOR);
        } else {
            start_variable(arr.length);
            send_fixed(arr);
        }
    }
    default byte[] receive_variable() throws IOException {
        long length = receive_int64();
        if(length == NULL_INDICATOR)
            return null;
        else if(length > Integer.MAX_VALUE)
            throw new IOException("value to large, consider reading into stream instead");
        return receive_fixed((int) length);
    }


    default void send_utf8(String s) throws IOException {
        send_variable(s.getBytes(StandardCharsets.UTF_8));
    }
    default String receive_utf8() throws IOException {
        byte[] arr = receive_variable();
        if(arr == null)
            return null;
        return new String(arr, StandardCharsets.UTF_8);
    }

    default void send_byte(byte b) throws IOException {
        send_fixed(getTransformer().transform(b));
    }
    default byte receive_byte() throws IOException {
        return getTransformer().detransform_byte(receive_fixed(1));
    }
    default void send_int32(int b) throws IOException {
        send_fixed(getTransformer().transform(b));
    }
    default int receive_int32() throws IOException {
        return getTransformer().detransform_int(receive_fixed(4));
    }
    default void send_int64(long b) throws IOException {
        send_fixed(getTransformer().transform(b));
    }
    default long receive_int64() throws IOException {
        return getTransformer().detransform_long(receive_fixed(8));
    }
    default void send_float64(double b) throws IOException {
        send_fixed(getTransformer().transform(b));
    }
    default double receive_float64() throws IOException {
        return getTransformer().detransform_double(receive_fixed(8));
    }


    TypeToFromRawTransformer<byte[]> getTransformer();







    default void send_variable_from(Pair<Long, InputStream> stream) throws IOException {
        send_variable_from(stream==null?NULL_INDICATOR:stream.l, stream==null?null:stream.r);
    }
    default void send_variable_from(long stream_length, InputStream stream) throws IOException {
        if(stream==null) {
            start_variable(NULL_INDICATOR);
        } else {
            start_variable(stream_length);
            byte[] buffer = new byte[1024];
            int byteCounter = 0;
            while (byteCounter < stream_length) {
                int read = stream.read(buffer);
                if (read == -1) break;
                byteCounter += read;

                if (read == buffer.length)
                    send_fixed(buffer);
                else
                    send_fixed(buffer, 0, read);
            }
            if (byteCounter != stream_length)
                throw new IOException("content did not deliver promised number of bytes. expected=" + stream_length + ", received=" + byteCounter);
        }
    }
    default Pair<Long, InputStream> get_variable_receiver() throws IOException {
        long length = receive_int64();
        if(length == NULL_INDICATOR) return null;
        if(length < 0) throw new EOFException("array length was negative. This may indicate that the server isn't able to fulfill the request");
        return new Pair<>(length, new InputStream() {
            long position = 0;
            @Override public int read() throws IOException {
                if(position>=length) return -1;

                position++;
                return receive_byte();
            }
            @Override public int read(byte[] b) throws IOException {
                return read(b, 0, b.length);
            }
            @Override public int read(byte[] b, int off, int len) throws IOException {
                if(position>=length) return -1;

                if(position + len >= length) {
                    len = (int) (length-position);
                }
                int read = receive_fixed(b, off, len);
                if(read >= 0) {
                    position += read;

                    return read;
                } else {
                    return read;
                }
            }
            @Override public long skip(long n) throws IOException {
                System.err.println("MCNP_HELPER.get_input_stream_for_variable_chunk.skip has been used and will work, but is not optimized. -> Optimize now");
                return super.skip(n);
            }
            @Override public void close() {
                //DO NOT CLOSE connectionInputStream INPUT STREAM.
                //We definitely need that one again
            }
        });
    }

    default void read_variable_in_parts(ReceivedCallback receiver) throws IOException {
        long length = receive_int64();
        if(length == NULL_INDICATOR) {            // throw new EOFException("length == "+NULL_INDICATOR+". This indicates that the server wasn't able to fulfill the request, but is also specifically reserved to indicate a null/none value");
            receiver.received(null);
        } else {
            if (length < 0)
                throw new EOFException("array length was negative. This may indicate that the server isn't able to fulfill the request");
            byte[] buffer = new byte[1024 * 4];
            int byteCounter = 0;
            while (byteCounter < length) {
                int read = receive_fixed(buffer, 0, (int) Math.min(buffer.length, length - byteCounter));
                byteCounter += read;

                if (read == buffer.length)
                    receiver.received(buffer);
                else
                    receiver.received(Arrays.copyOfRange(buffer, 0, read));
            }
        }
    }
}
