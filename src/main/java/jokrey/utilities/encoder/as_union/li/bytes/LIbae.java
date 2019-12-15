package jokrey.utilities.encoder.as_union.li.bytes;

import jokrey.utilities.bitsandbytes.BitHelper;
import jokrey.utilities.encoder.EncodableAsBytes;
import jokrey.utilities.encoder.as_union.li.LIPosition;
import jokrey.utilities.encoder.as_union.li.LIe;
import jokrey.utilities.transparent_storage.StorageSystemException;
import jokrey.utilities.transparent_storage.TransparentStorage;
import jokrey.utilities.transparent_storage.bytes.TransparentBytesStorage;
import jokrey.utilities.transparent_storage.bytes.non_persistent.ByteArrayStorage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

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
 *
 * TODO:: switch to unsigned
 *
 * @author jokrey
 */
public class LIbae extends LIe<byte[]> implements EncodableAsBytes {
    /**
     * No-arg, do nothing constructor.
     */
    public LIbae() {
        super(new ByteArrayStorage());
    }

    /**
     * Initialized the underlying storage system with the provided TransparentBytesStorage
     * @param storageSystem a storage system
     */
    public LIbae(TransparentBytesStorage storageSystem) {
        super(storageSystem);
    }

    /**
     * Constructor
     * @param initial_capacity initial capacity - might save some unnecessary copy operations
     */
    public LIbae(int initial_capacity) {
        super(new ByteArrayStorage(initial_capacity));
    }

    /**
     * Shortcut for: new LIbae().readFromEncodedBytes(orig);
     * @param orig initial raw content
     */
    public LIbae(byte[] orig) {
        super(new ByteArrayStorage(orig));
    }

    /**
     * With buffer size 4096
     * {@link #LIbae(InputStream, int)}
     */
    public LIbae(InputStream is) throws IOException {
        this(is, 4096);
    }

    /**
     * Reads the input streams content into the storage system.
     * @param is InputStream
     * @param buffer_size buffer size should be = 2^n, for some n € N
     * @throws IOException when the provided stream is, fails
     */
    public LIbae(InputStream is, int buffer_size) throws IOException {
        super(new ByteArrayStorage());
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[buffer_size];
        while ((nRead = is.read(data, 0, data.length)) != -1)
            buffer.write(data, 0, nRead);
        getStorageSystem().setContent(buffer.toByteArray());
    }


    @Override public byte[] getEncodedBytes() throws StorageSystemException {
        return getStorageSystem().getContent();
    }

    @Override public void readFromEncodedBytes(byte[] encoded_bytes) {
        getStorageSystem().setContent(encoded_bytes);
    }


    /**
     * Accessor for storage system
     * @return storage system
     */
    public TransparentBytesStorage getStorageSystem() {
        return (TransparentBytesStorage) getRawStorage();
    }



    /**
     * Obtains the "next" decoded part as a limited stream.
     * Not thread safe and when another operation is executed before the stream is completely read, it may fail.
     *
     * @param pos position to read from.
     *            Has to be valid (ensure that no invalidating operation was executed on the internal storage with another position value)
     *            Position will be internally altered to point to the "next" value
     * @return "next" decoded part as a limited stream
     * @throws StorageSystemException on any, possibly remote, error
     */
    public InputStream li_decode_asStream(LIPosition pos) throws StorageSystemException {
        long[] startIndex_endIndex_ofNextLIArr = get_next_li_bounds(pos, getStorageSystem());
        if(startIndex_endIndex_ofNextLIArr.length==2) {
            InputStream decoded = getStorageSystem().substream(startIndex_endIndex_ofNextLIArr[0], startIndex_endIndex_ofNextLIArr[1]);
            pos.pointer = startIndex_endIndex_ofNextLIArr[1];
            return decoded;
        } else {
            return null;
        }
    }

    /**
     * Encodes and appends content from stream
     *
     * Will read bytes from stream until content_length is reached.
     *   If the stream ends before that any number of exception will be thrown.
     *   So whatever you do, don't let that happen
     *
     * The stream will BE CLOSED after everything of value(and content_length) has been read.
     *
     * @param content where to read the content from
     * @param content_length GUARANTEED eventual length of content and maximum_bytes_to_read_from_content
     * @throws RuntimeException any kind of RuntimeException if the stream does not deliver enough data or something else fails with it..
     */
    public void encode(InputStream content, long content_length) throws StorageSystemException {
        getStorageSystem().append(getLengthIndicatorFor(content_length)).append(content, content_length);
    }

    //ACTUAL LIBAE FUNCTIONALITY


    @Override protected byte[] getLengthIndicatorFor(byte[] arr) {
        return getLengthIndicatorFor(arr.length);
    }
    private byte[] getLengthIndicatorFor(long length) {
        return generateLI(length);
    }

    @Override protected long[] get_next_li_bounds(LIPosition start_pos, TransparentStorage<byte[]> contentBuilder) throws StorageSystemException {
        long i = start_pos.pointer;
        long content_size = contentBuilder.contentSize();
        if(i+1>content_size)
            return null;
        byte[] cache = contentBuilder.sub(i, i+9); //cache maximum number of required bytes. (to minimize possibly slow sub calls)

        byte leading_li = cache[0];

//        byte[] lengthIndicator = Arrays.copyOfRange(cache, 1, 1 + leading_li);
        long lengthIndicator_asInt = getIntFromByteArray(cache, 1, leading_li);
        if(lengthIndicator_asInt==-1 || i+lengthIndicator_asInt>contentBuilder.contentSize())
            return null;
        i+=leading_li + 1; //to skip the li information.
        return new long[]{i, i + lengthIndicator_asInt};
    }


    public static byte getLeadingLIFor(long length) {
        return (byte) Math.max(0, Math.ceil(Math.floor((Math.log(length)/Math.log(2)) + 1) / 8));
    }
    public static byte[] generateLI(long length) {
        byte li_byte_count = getLeadingLIFor(length);
        byte[] li_bytes = new byte[li_byte_count + 1];
        li_bytes[0] = li_byte_count;//cast possible because it cannot be more than 8 anyways.
        for(int n=1;n<li_bytes.length;n++)
            li_bytes[n] = BitHelper.getByte(length, (li_bytes.length-1)-n);
        return li_bytes;

        //one more array allocation, more declarative:
//        byte[] li_bytes = getMinimalByteArrayFromInt(length); //cannot be more than 8 in size.
//        byte leading_li = (byte) li_bytes.length; //cast possible because it cannot be more than 8 anyways.
//        byte[] li_bytes_with_leading_li = new byte[li_bytes.length+1];
//        li_bytes_with_leading_li[0] = leading_li;
//        System.arraycopy(li_bytes, 0, li_bytes_with_leading_li, 1, li_bytes.length);
//        return li_bytes_with_leading_li;
    }

    //SECONDARY LIBAE FUNCTIONALITY
    public static long getIntFromByteArray(byte[] bytearr, int from, int len) {
        if(len < 8) {
            return BitHelper.getUIntFromNBytes(bytearr, from, len); //if smaller than 8 bytes, can only be positive
        } else {
            return BitHelper.getIntFromNBytes(bytearr, from, len);
        }
    }

//    public static long getIntFromByteArrayOld(byte[] orig, int from, int len) {
//        byte[] bytearr = Arrays.copyOfRange(orig, from, from + len);
//
//        if(bytearr.length == 8) {
//            return ByteBuffer.wrap(bytearr).getLong();// big-endian by default
//        } else if(bytearr.length < 8) {
//            byte[] more_bytes = new byte[8];
//            System.arraycopy(bytearr, 0, more_bytes, more_bytes.length-bytearr.length, bytearr.length);
//            return ByteBuffer.wrap(more_bytes).getLong();
//        } else {
//            byte[] less_bytes = new byte[8];
//            System.arraycopy(bytearr, bytearr.length-less_bytes.length, less_bytes, 0, less_bytes.length);
//            return ByteBuffer.wrap(less_bytes).getLong();
//        }
//    }
}