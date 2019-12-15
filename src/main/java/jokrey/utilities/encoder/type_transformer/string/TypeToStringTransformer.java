package jokrey.utilities.encoder.type_transformer.string;

import jokrey.utilities.encoder.type_transformer.NotTransformableException;
import jokrey.utilities.encoder.type_transformer.TypeToFromRawTransformer;

import java.util.Base64;

/**
 * Implementation of a raw type transformer for utf8 strings.
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
public class TypeToStringTransformer implements TypeToFromRawTransformer<String> {
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

    @Override public String transform(boolean entry) throws NotTransformableException {
        return entry ? "t":"f";
    }

    @Override public Boolean detransform_boolean(String entry) throws NotTransformableException {
        return entry.equals("t");
    }

    @Override public String transform(boolean[] entry) throws NotTransformableException {
        StringBuilder sb = new StringBuilder(entry.length);
        for(boolean b:entry) sb.append(transform(b));
        return sb.toString();
    }

    @Override public boolean[] detransform_booleans(String entry) throws NotTransformableException {
        boolean[] result = new boolean[entry.length()];
        for(int i=0;i<result.length;i++)
            result[i] = detransform_boolean(String.valueOf(entry.charAt(i)));
        return result;
    }


    @Override public String transform(byte entry) throws NotTransformableException {
        return transform(new byte[] {entry});
    }

    @Override public Byte detransform_byte(String entry) throws NotTransformableException {
        return detransform_bytes(entry)[0];
    }

    @Override public String transform(byte[] entry) throws NotTransformableException {
        return Base64.getEncoder().encodeToString(entry);
    }

    @Override public byte[] detransform_bytes(String entry) throws NotTransformableException {
        return Base64.getDecoder().decode(entry);
    }


    @Override public String transform(short entry) throws NotTransformableException {
        return String.valueOf(entry);
    }

    @Override public Short detransform_short(String entry) throws NotTransformableException {
        return Short.parseShort(entry);
    }

    @Override public String transform(short[] entry) throws NotTransformableException {
//        LIse lise = new LIse();
//        for(short s:entry) lise.encode(transform(s));
//        return lise.getEncodedString();
        Short[] result = new Short[entry.length];
        for(int i=0;i<result.length;i++)
            result[i] = entry[i];
        return transform(result);
    }

    @Override public short[] detransform_shorts(String entry) throws NotTransformableException {
//        String[] raw = new LIse(entry).li_decode_all();
//        short[] result = new short[raw.length];
//        for (int i = 0; i < raw.length; i++)
//            result[i] = detransform_short(raw[i]);
//        return result;
        Short[] raw = detransform_array(entry, Short[].class);
        short[] result = new short[raw.length];
        for(int i=0;i<result.length;i++)
            result[i] = raw[i];
        return result;
    }


    @Override public String transform(int entry) throws NotTransformableException {
        return String.valueOf(entry);
    }

    @Override public Integer detransform_int(String entry) throws NotTransformableException {
        try {
            return Integer.parseInt(entry);
        } catch(NumberFormatException e) {
            throw new NotTransformableException(entry);
        }
    }

    @Override public String transform(int[] entry) throws NotTransformableException {
//        LIse lise = new LIse();
//        for(int s:entry) lise.encode(transform(s));
//        return lise.getEncodedString();
        Integer[] result = new Integer[entry.length];
        for(int i=0;i<result.length;i++)
            result[i] = entry[i];
        return transform(result);
    }

    @Override public int[] detransform_ints(String entry) throws NotTransformableException {
//        String[] raw = new LIse(entry).li_decode_all();
//        int[] result = new int[raw.length];
//        for (int i = 0; i < raw.length; i++)
//            result[i] = detransform_int(raw[i]);
//        return result;
        Integer[] raw = detransform_array(entry, Integer[].class);
        int[] result = new int[raw.length];
        for(int i=0;i<result.length;i++)
            result[i] = raw[i];
        return result;
    }


    @Override public String transform(long entry) throws NotTransformableException {
        return String.valueOf(entry);
    }

    @Override public Long detransform_long(String entry) throws NotTransformableException {
        try {
            return Long.parseLong(entry);
        } catch(NumberFormatException e) {
            throw new NotTransformableException(entry);
        }
    }

    @Override public String transform(long[] entry) throws NotTransformableException {
//        LIse lise = new LIse();
//        for(long s:entry) lise.encode(transform(s));
//        return lise.getEncodedString();
        Long[] result = new Long[entry.length];
        for(int i=0;i<result.length;i++)
            result[i] = entry[i];
        return transform(result);
    }

    @Override public long[] detransform_longs(String entry) throws NotTransformableException {
//        String[] raw = new LIse(entry).li_decode_all();
//        long[] result = new long[raw.length];
//        for (int i = 0; i < raw.length; i++)
//            result[i] = detransform_long(raw[i]);
//        return result;
        Long[] raw = detransform_array(entry, Long[].class);
        long[] result = new long[raw.length];
        for(int i=0;i<result.length;i++)
            result[i] = raw[i];
        return result;
    }


    @Override public String transform(float entry) throws NotTransformableException {
        return String.valueOf(entry);
    }

    @Override public Float detransform_float(String entry) throws NotTransformableException {
        try {
            return Float.parseFloat(entry);
        } catch(NumberFormatException e) {
            throw new NotTransformableException(entry);
        }
    }

    @Override public String transform(float[] entry) throws NotTransformableException {
//        LIse lise = new LIse();
//        for(float s:entry) lise.encode(transform(s));
//        return lise.getEncodedString();
        Float[] result = new Float[entry.length];
        for(int i=0;i<result.length;i++)
            result[i] = entry[i];
        return transform(result);
    }

    @Override public float[] detransform_floats(String entry) throws NotTransformableException {
//        String[] raw = new LIse(entry).li_decode_all();
//        float[] result = new float[raw.length];
//        for (int i = 0; i < raw.length; i++)
//            result[i] = detransform_float(raw[i]);
//        return result;
        Float[] raw = detransform_array(entry, Float[].class);
        float[] result = new float[raw.length];
        for(int i=0;i<result.length;i++)
            result[i] = raw[i];
        return result;
    }


    @Override public String transform(double entry) throws NotTransformableException {
        return String.valueOf(entry);
    }

    @Override public Double detransform_double(String entry) throws NotTransformableException {
        try {
            return Double.parseDouble(entry);
        } catch(NumberFormatException e) {
            throw new NotTransformableException(entry);
        }
    }

    @Override public String transform(double[] entry) throws NotTransformableException {
//        LIse lise = new LIse();
//        for(double s:entry) lise.encode(transform(s));
//        return lise.getEncodedString();
        Double[] result = new Double[entry.length];
        for(int i=0;i<result.length;i++)
            result[i] = entry[i];
        return transform(result);
    }

    @Override public double[] detransform_doubles(String entry) throws NotTransformableException {
//        String[] raw = new LIse(entry).li_decode_all();
//        double[] result = new double[raw.length];
//        for (int i = 0; i < raw.length; i++)
//            result[i] = detransform_double(raw[i]);
//        return result;
        Double[] raw = detransform_array(entry, Double[].class);
        double[] result = new double[raw.length];
        for(int i=0;i<result.length;i++)
            result[i] = raw[i];
        return result;
    }


    @Override public String transform(char entry) throws NotTransformableException {
        return transform(new char[] {entry});
    }

    @Override public Character detransform_char(String entry) throws NotTransformableException {
        return detransform_chars(entry)[0];
    }

    @Override public String transform(char[] entry) throws NotTransformableException {
        return new String(entry);
    }

    @Override public char[] detransform_chars(String entry) throws NotTransformableException {
        return entry.toCharArray();
    }


    @Override public String transform(String entry) throws NotTransformableException {
        return entry;
    }


    @Override public String detransform_string(String entry) throws NotTransformableException {
        return entry;
    }
}