package jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.compression.deflate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterOutputStream;

/**
 * @author jokrey
 */
public class DeflateAlgorithmHelper {
    public static byte[] compress(byte[] data, int compression_level) {
        if(data==null)return null;
        ByteArrayOutputStream storage = new ByteArrayOutputStream(data.length);
        DeflaterOutputStream stream = new DeflaterOutputStream(storage, new Deflater(compression_level), 4096);
        try {
            stream.write(data);
            stream.finish();
            return storage.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }
    public static byte[] decompress(byte[] data) {
        if(data==null)return null;
        ByteArrayOutputStream storage = new ByteArrayOutputStream(data.length);
        InflaterOutputStream stream = new InflaterOutputStream(storage, new Inflater(), 4096);
        try {
            stream.write(data);
            stream.finish();
            return storage.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }
}
