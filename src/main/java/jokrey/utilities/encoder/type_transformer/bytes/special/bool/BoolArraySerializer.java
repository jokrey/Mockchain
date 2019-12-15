package jokrey.utilities.encoder.type_transformer.bytes.special.bool;

import jokrey.utilities.bitsandbytes.BitHelper;

/**
 * Memory efficient transformation.
 * Uses up at most 10(7+3) bits more than direct bit storage(which is impossible since everything is aligned to byte in java and most other languages)
 *
 * @author jokrey
 */
public class BoolArraySerializer {
    /**
     * Transforms any boolean array into an efficiently small array.
     * @param val array
     * @return byte[] representing the provided boolean[]
     */
    public static byte[] transform(boolean[] val) {
        if(val.length==0)
            return new byte[0];

        int number_of_bits_to_store = val.length + 3;
        int number_of_over_bits = val.length % 8;

        byte[] bs = new byte[number_of_bits_to_store/8 + (number_of_bits_to_store % 8 == 0?0:1)]; //1 bool = 1 bit | 8 bit = 1 byte(/8) | 1 byte is the smallest unit, so for every started 8th we require a full byte(+1)
        bs[0] = (byte) ((number_of_over_bits-1) << (8 - 3));

        for(int i=0;i<val.length;i++) {
            int bit_index = i+3;
            int byte_index = bit_index/8;
            if(val[i])
                bs[byte_index] = BitHelper.setBit(bs[byte_index], 7 - (bit_index % 8));
        }
        return bs;
    }

    /**
     * Transforms a byte[] array into the original boolean array created via {@link #transform(boolean[])}.
     * @param val array create using  {@link #transform(boolean[])}
     * @return decoded boolean[], or with some luck a random boolean[] when the given array was not created using {@link #transform(boolean[])}
     * throws RuntimeException if the provided array
     */
    public static boolean[] detransform(byte[] val) {
        if(val.length==0)
            return new boolean[0];

        //since we could not distinguish between an input array ending with many false's and the input array being smaller
        //     we have do indicate how many bytes of the last byte(bs[bs.length-1]) are valid. For that we require 3 bits. (3 bits can encode 8 values).
        //     so the first three bits will be reserved for that exact purpose. To determine how many bits of the last byte are to be interpreted

        int number_of_over_bits = ((val[0] >>> 5 & 0b111 ) + 1) % 8; //takes the first three bytes of first byte and interprets them as an unsigned integer
        //+1, because 0 bytes are not possible.
        // the byte would simply not be added.
        // instead it is possible that 8 bits are used.
        // this means that the three bits are not interpreted as base2 bits, but as base2 + 1
        boolean[] results = new boolean[(val.length - 1 ) * 8 + number_of_over_bits];
        for(int i=0;i<results.length;i++) {
            int bit_index = i+3; //jump bytes occupied by over bit indicator
            int byte_index = bit_index/8;
            results[i] = BitHelper.getBit(val[byte_index], 7 - (bit_index % 8)) == 1;
        }

        return results;
    }
}
