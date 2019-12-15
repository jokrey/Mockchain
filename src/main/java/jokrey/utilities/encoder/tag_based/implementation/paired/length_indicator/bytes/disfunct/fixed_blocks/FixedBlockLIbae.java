package jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.bytes.disfunct.fixed_blocks;

import jokrey.utilities.bitsandbytes.BitHelper;
import jokrey.utilities.encoder.as_union.li.LIPosition;
import jokrey.utilities.encoder.as_union.li.bytes.LIbae;
import jokrey.utilities.transparent_storage.StorageSystemException;
import jokrey.utilities.transparent_storage.TransparentStorage;
import jokrey.utilities.transparent_storage.bytes.TransparentBytesStorage;
import jokrey.utilities.transparent_storage.bytes.non_persistent.ByteArrayStorage;

/**
 * Can encode and decode multiple chunks of bytes into a single one.
 *
 * This is done by using a single byte to indicate how many bytes are required store the chunk length
 *   (at most 8, because that a lot of Terrabytes and long in java just isn't longer...)
 *   Then it writes how many bytes are required to store the chunk
 *   Then comes the chunk itself.
 *
 * At decoding time this way every byte chunk can be read in sequence from the underlying storage system.
 *   If a chunk is read with li_decode, the read pointer is increased so that when calling the method again the next chunk is read.
 *
 * LI-Encoding
 * Length-Indicator based byte array encoding
 **
 * @author jokrey
 */
public class FixedBlockLIbae extends LIbae {
    public FixedBlockLIbae() {
        this(100000, 1024);
    }
    public FixedBlockLIbae(int maxBlocks, int blockSize) {
        this(new FixedBlockBytesStorage(new ByteArrayStorage(),maxBlocks,blockSize));
    }
    public FixedBlockLIbae(byte[] orig) {
        this(new FixedBlockBytesStorage(new ByteArrayStorage(orig), 100000, 1024));
    }

    public FixedBlockLIbae(TransparentBytesStorage storageSystem) {
        this(new FixedBlockBytesStorage(storageSystem, 100000, 1024));
    }
    public FixedBlockLIbae(FixedBlockBytesStorage storageSystem) {
        super(storageSystem);
    }

    @Override public FixedBlockBytesStorage getStorageSystem() {
        return (FixedBlockBytesStorage)super.getStorageSystem();
    }

    //ACTUAL LIBAE FUNCTIONALITY

    @Override protected byte[] getLengthIndicatorFor(byte[] arr) {
        return getLengthIndicatorFor(arr.length);
    }
    private static byte[] getLengthIndicatorFor(long length) {
        int byte_count = (int) Math.max(0, Math.ceil(Math.floor((Math.log(length)/Math.log(2)) + 1) / 8));
        byte[] bytes = new byte[byte_count + 1];
        bytes[0] = (byte) byte_count;//cast possible because it cannot be more than 8 anyways.
        for(int n=1;n<bytes.length;n++)
            bytes[n] = BitHelper.getByte(length, (bytes.length-1)-n);
        return bytes;

        //one more array allocation, more declarative:
//        byte[] li_bytes = getMinimalByteArrayFromInt(length); //cannot be more than 8 in size.
//        byte leading_li = (byte) li_bytes.length; //cast possible because it cannot be more than 8 anyways.
//        byte[] li_bytes_with_leading_li = new byte[li_bytes.length+1];
//        li_bytes_with_leading_li[0] = leading_li;
//        System.arraycopy(li_bytes, 0, li_bytes_with_leading_li, 1, li_bytes.length);
//        return li_bytes_with_leading_li;
    }


    @Override protected long[] get_next_li_bounds(LIPosition start_pos, TransparentStorage<byte[]> contentBuilder) throws StorageSystemException {
        long i = start_pos.pointer;
        i = getStorageSystem().nextHigherBlockStartIndex(i);
        long content_size = contentBuilder.contentSize();
        if(i+1>content_size)
            return null;
        byte[] cache = contentBuilder.sub(i, i+9); //cache maximum number of required bytes. (to minimize possibly slow sub calls)

        byte leading_li = cache[0];

//        byte[] lengthIndicator = subarray(cache, 1, 1 + leading_li);
//        byte[] lengthIndicator = Arrays.copyOfRange(cache, 1, 1 + leading_li);
        long lengthIndicator_asInt = getIntFromByteArray(cache, 1, leading_li);
        if(lengthIndicator_asInt==-1 || i+lengthIndicator_asInt>contentBuilder.contentSize())
            return null;
        i+=leading_li + 1; //to skip all the li information.
        return new long[]{i, i + lengthIndicator_asInt};
    }
}