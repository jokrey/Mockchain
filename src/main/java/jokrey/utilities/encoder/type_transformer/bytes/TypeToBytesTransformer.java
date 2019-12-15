package jokrey.utilities.encoder.type_transformer.bytes;

import jokrey.utilities.bitsandbytes.BitHelper;
import jokrey.utilities.encoder.type_transformer.NotTransformableException;
import jokrey.utilities.encoder.type_transformer.TypeToFromRawTransformer;
import jokrey.utilities.encoder.type_transformer.bytes.special.bool.BoolArraySerializer;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Implementation of a raw type transformer for byte strings.
 * Does both transformation and de-transformation.
 *
 * Has to be consistent across implementations.
 * TODO: document how each transformation works, so that it can be kept consistent.
 *
 * Does not support arrays of all supported types(so also not nested arrays(for example 2d arrays)).
 *    There is support in the length indicator package.
 *
 * @author jokrey
 */
public abstract class TypeToBytesTransformer implements TypeToFromRawTransformer<byte[]> {
    @Override public boolean canTransform(Class<?> c) {
        for(Class<?> assignable_c:SHOULD_SUPPORT_ASSIGNABLE)
            if(assignable_c.isAssignableFrom(c))
                return true;
        if(c.isArray() && Object[].class.isAssignableFrom(c)) {
            return canTransform(c.getComponentType());
        }
        return SHOULD_SUPPORT_TYPES.contains(c);
    }
    @Override public boolean canDetransform(Class<?> c) {
        return canTransform(c);
    }

    @Override public byte[] transform(boolean entry) throws NotTransformableException {
        return new byte[] {(byte) (entry?1:0)};
    }

    @Override public Boolean detransform_boolean(byte[] entry) throws NotTransformableException {
        return entry[0]!=0;
    }

    @Override public byte[] transform(boolean[] entry) throws NotTransformableException {
        return BoolArraySerializer.transform(entry);
    }

    @Override public boolean[] detransform_booleans(byte[] entry) throws NotTransformableException {
        return BoolArraySerializer.detransform(entry);
    }


    @Override public byte[] transform(byte entry) throws NotTransformableException {
        return new byte[] {entry};
    }

    @Override public Byte detransform_byte(byte[] entry) throws NotTransformableException {
        return entry[0];
    }

    @Override public byte[] transform(byte[] entry) throws NotTransformableException {
        return entry;
    }

    @Override public byte[] detransform_bytes(byte[] entry) throws NotTransformableException {
        return entry;
    }


    @Override public byte[] transform(short entry) throws NotTransformableException {
        return BitHelper.getBytes(entry);
    }

    @Override public Short detransform_short(byte[] entry) throws NotTransformableException {
        return BitHelper.getInt16From(entry);
    }

    @Override public byte[] transform(short[] entry) throws NotTransformableException {
        byte[] bytes = new byte[Short.BYTES * entry.length];
        int i=0;
        for(short e:entry)
            for (int c = 0; c < Short.BYTES; c++, i++)
                bytes[i] = BitHelper.getByte(e, (Short.BYTES-1)-c);
        return bytes;
    }

    @Override public short[] detransform_shorts(byte[] entry) throws NotTransformableException {
        short[] result = new short[entry.length / Short.BYTES];
        for(int i=0;i<result.length;i++)
            result[i] = (short)((entry[(i * Short.BYTES)] << 8) | (entry[i * Short.BYTES + 1] & 0xff));
        return result;
    }


    @Override public byte[] transform(int entry) throws NotTransformableException {
        return BitHelper.getBytes(entry);
    }

    @Override public Integer detransform_int(byte[] entry) throws NotTransformableException {
        return BitHelper.getInt32From(entry);
    }

    @Override public byte[] transform(int[] entry) throws NotTransformableException {
        byte[] bytes = new byte[Integer.BYTES * entry.length];
        int i=0;
        for(int e:entry)
            for (int c = 0; c < Integer.BYTES; c++, i++)
                bytes[i] = BitHelper.getByte(e, (Integer.BYTES-1)-c);
        return bytes;
    }
    @Override public int[] detransform_ints(byte[] entry) throws NotTransformableException {
        int[] result = new int[entry.length / Integer.BYTES];
        for(int i=0;i<result.length;i++)
            result[i] = (((entry[(i * Integer.BYTES)]          ) << 24) |
                         ((entry[(i * Integer.BYTES) +1] & 0xff) << 16) |
                         ((entry[(i * Integer.BYTES) +2] & 0xff) <<  8) |
                         ((entry[(i * Integer.BYTES) +3] & 0xff)      ));
        return result;
    }


    @Override public byte[] transform(long entry) throws NotTransformableException {
        return BitHelper.getBytes(entry);
    }

    @Override public Long detransform_long(byte[] entry) throws NotTransformableException {
        return BitHelper.getInt64From(entry);
    }

    @Override public byte[] transform(long[] entry) throws NotTransformableException {
        byte[] bytes = new byte[Long.BYTES * entry.length];
        int i=0;
        for(long e:entry)
            for (int c = 0; c < Long.BYTES; c++, i++)
                bytes[i] = BitHelper.getByte(e, (Long.BYTES-1)-c);
        return bytes;
    }

    @Override public long[] detransform_longs(byte[] entry) throws NotTransformableException {
        long[] result = new long[entry.length / Long.BYTES];
        for(int i=0;i<result.length;i++)
            result[i] = ((((long)entry[(i * Long.BYTES)]          ) << 56) |
                         (((long)entry[(i * Long.BYTES) +1] & 0xff) << 48) |
                         (((long)entry[(i * Long.BYTES) +2] & 0xff) << 40) |
                         (((long)entry[(i * Long.BYTES) +3] & 0xff) << 32) |
                         (((long)entry[(i * Long.BYTES) +4] & 0xff) << 24) |
                         (((long)entry[(i * Long.BYTES) +5] & 0xff) << 16) |
                         (((long)entry[(i * Long.BYTES) +6] & 0xff) <<  8) |
                         (((long)entry[(i * Long.BYTES) +7] & 0xff)      ));
        return result;
    }


    @Override public byte[] transform(float entry) throws NotTransformableException {
        return BitHelper.getBytes(entry);
    }

    @Override public Float detransform_float(byte[] entry) throws NotTransformableException {
        return BitHelper.getFloat32From(entry);
    }

    @Override public byte[] transform(float[] entry) throws NotTransformableException {
        byte[] bytes = new byte[Float.BYTES * entry.length];
        int i=0;
        for(float e:entry) {
            byte[] e_bytes = BitHelper.getBytes(e);
            for (int c = 0; c < e_bytes.length; c++, i++)
                bytes[i] = e_bytes[c];
        }
        return bytes;
    }

    @Override public float[] detransform_floats(byte[] entry) throws NotTransformableException {
        float[] result = new float[entry.length / Float.BYTES];
        for(int i=0;i<result.length;i++)
            result[i] = ByteBuffer.wrap(entry, i * Float.BYTES, Float.BYTES).getFloat();
        return result;
    }


    @Override public byte[] transform(double entry) throws NotTransformableException {
        return BitHelper.getBytes(entry);
    }

    @Override public Double detransform_double(byte[] entry) throws NotTransformableException {
        return BitHelper.getFloat64From(entry);
    }

    @Override public byte[] transform(double[] entry) throws NotTransformableException {
        byte[] bytes = new byte[Double.BYTES * entry.length];
        int i=0;
        for(double e:entry) {
            byte[] e_bytes = BitHelper.getBytes(e);
            for (int c = 0; c < e_bytes.length; c++, i++)
                bytes[i] = e_bytes[c];
        }
        return bytes;
    }

    @Override public double[] detransform_doubles(byte[] entry) throws NotTransformableException {
        double[] result = new double[entry.length / Double.BYTES];
        for(int i=0;i<result.length;i++)
            result[i] = ByteBuffer.wrap(entry, i * Double.BYTES, Double.BYTES).getDouble();
        return result;
    }


    @Override public byte[] transform(char entry) throws NotTransformableException {
        return transform(new char[] {entry});
    }

    @Override public Character detransform_char(byte[] entry) throws NotTransformableException {
        return detransform_chars(entry)[0];
    }

    @Override public byte[] transform(char[] entry) throws NotTransformableException {
//        ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(entry));
//        return Arrays.copyOfRange(byteBuffer.array(), byteBuffer.position(), byteBuffer.limit());
        return new String(entry).getBytes(StandardCharsets.UTF_8);
    }

    @Override public char[] detransform_chars(byte[] entry) throws NotTransformableException {
//        return StandardCharsets.UTF_8.decode(ByteBuffer.wrap(entry)).array();
        return new String(entry, StandardCharsets.UTF_8).toCharArray();
    }


    @Override public byte[] transform(String entry) throws NotTransformableException {
        return entry.getBytes(StandardCharsets.UTF_8);
    }


    @Override public String detransform_string(byte[] entry) throws NotTransformableException {
        return new String(entry, StandardCharsets.UTF_8);
    }



    public static byte[] string_to_bytes(String entry) {
        return entry.getBytes(StandardCharsets.UTF_8);
    }
    public static String bytes_to_string(byte[] entry) {
        return new String(entry, StandardCharsets.UTF_8);
    }
}
