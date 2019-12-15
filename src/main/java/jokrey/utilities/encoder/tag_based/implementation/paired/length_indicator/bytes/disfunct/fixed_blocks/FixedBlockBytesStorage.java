package jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.bytes.disfunct.fixed_blocks;

import jokrey.utilities.bitsandbytes.BitHelper;
import jokrey.utilities.transparent_storage.StorageSystemException;
import jokrey.utilities.transparent_storage.bytes.TransparentBytesStorage;
import jokrey.utilities.transparent_storage.bytes.wrapper.SubBytesStorage;

import java.io.InputStream;

/**
 * TODO: use unsigned
 *
 * Idea: the header contains information on how many bytes are in each block (blockLengthAt).
 *    blockLengthAt_size: ceil(ld(blockSize))
 *    headerSize = maxBlocks * blockLengthAt_size:
 *    blockLengthAt is stored at the header bytes sub(blockID / blockLengthAt_size, (blockID+1) / blockLengthAt_size).
 *
 * So to obtain a block, just do:
 *       int blockLength = BitHelper.getByte(sub(blockID / blockLengthAt_size, (blockID+1) / blockLengthAt_size))
 *       int blockIndexInData = blockID * blockSize;
 *       Arr block = sub(blockIndexInData, blockIndexInData + blockLength);
 *
 * Deleting:
 *       set block length to 0, between start and end, except for block 0 - setBlockLengthAt(start - blockIndexInData).
 *       Also in the last block, the data between end and blockEnd has to be copied to blockStart
 *
 * Append:
 *       append starts at the very latest, empty block and writes there
 *       It could also write at the end of the last non empty block, but it can be assumed that multiple append calls refer to different dataset, that are changed together.
 *       Note that this drastically changed the behaviour of chained append calls. There is an option to disable this behaviour.
 *
 * @author jokrey
 */
public class FixedBlockBytesStorage implements TransparentBytesStorage {
    private final TransparentBytesStorage underlying;
    private final SubBytesStorage header;
    private final SubBytesStorage data;

    public FixedBlockBytesStorage(TransparentBytesStorage storage, int max_blocks) {
        this(storage, max_blocks, 1024);
    }
    public FixedBlockBytesStorage(TransparentBytesStorage storage, int max_blocks, int block_size) {
        this.underlying = storage;
        initHeaderFacts(max_blocks, block_size);
        SubBytesStorage[] split = storage.split(getHeaderLength());
        this.header = split[0];
        this.data = split[1];
    }



    @Override public TransparentBytesStorage set(long start, InputStream content, long content_length) throws StorageSystemException {
        setBlockRangeUsed(start, start+content_length);
        long index = (start/blockSize) * blockSize;
        data.set(index, content, content_length);
        return this;
    }

    @Override public TransparentBytesStorage set(long start, byte[] part, int off, int off_end) throws StorageSystemException {
        setBlockRangeUsed(start, start + (off_end -off));
        data.set(start, part, off, off_end);
        return this;
    }

    @Override public TransparentBytesStorage set(long start, byte[] part, int off) throws StorageSystemException {
        setBlockRangeUsed(start, start + (part.length-off));
        data.set(start, part, off);
        return this;
    }

    @Override public TransparentBytesStorage set(long start, byte[] part) throws StorageSystemException {
        setBlockRangeUsed(start, start + part.length);
        data.set(start, part);
        return this;
    }

    @Override public TransparentBytesStorage delete(long start, long end) throws StorageSystemException {
        setBlockRangeUnused(start, end);
//        data.delete(start, end);
        return this;
    }

    @Override public void clear() {
        setBlocksAllClear();
    }

    @Override public long contentSize() {
        return getRealLength();
    }


    //just relay, checking for validity is too costly for read
    @Override public byte[] sub(long start, long end) {
        return data.sub(start, end);
    }
    @Override public InputStream substream(long start, long end) throws StorageSystemException {
        return data.substream(start, end);
    }




    @Override public void close() throws Exception {
        underlying.close();
    }

    @Override public InputStream stream() {
//        throw new UnsupportedOperationException();
        return data.stream();
    }
    @Override public void setContent(byte[] content) {
//        throw new UnsupportedOperationException();
        data.setContent(content);
    }
    @Override public byte[] getContent() {
        return data.getContent();
//        byte[] content = new byte[(int) data.contentSize()];
//        int index = 0;
//        int blockCount = getLargestUsedBlock();
//        for(int i=0;i<blockCount;i++) {
//            int blockSizeAt = getBlockSize(i);
//            System.arraycopy(data.sub(i*blockSize, i*blockSize + blockSizeAt), 0, content, index, blockSizeAt);
//            index+=blockSizeAt;
//        }
//        return Arrays.copyOfRange(content, 0, index);
    }








//    //HEADER LOGIC:
    private int getHeaderLength() {
        return maxBlocks * blockLengthAt_size;
    }
    private int blockSize;
    private int blockLengthAt_size;
    private int maxBlocks;

//    private int realLengthCached = 0;
//    private int largestUsedBlockCached = 0;
    private void initHeaderFacts(int maxBlocks, int blockSize) {
        this.blockSize =blockSize;
        blockLengthAt_size = (int) Math.ceil(ld(blockSize));
        this.maxBlocks = maxBlocks;
//        realLengthCached = getRealLength();
//        largestUsedBlockCached = getLargestUsedBlock();
    }

    public int getBlockSize() {
        return blockSize;
    }

    private int getBlockSize(int blockID) {
        return (int) BitHelper.getIntFrom(header.sub(blockID*blockLengthAt_size, (blockID+1)*blockLengthAt_size));
    }
    private void setBlockSize(int blockID, int newSize) {
        header.set(blockID*blockLengthAt_size, BitHelper.getInNBytes(newSize, blockLengthAt_size));
    }
    private int getBlockIndexInData(int blockID) {
        return blockID*blockSize;
    }
    public boolean isBlockUsed(int blockID) {
        return getBlockSize(blockID)>0;
    }
    public long blockStartIndex(long raw_index) {
        return (raw_index/blockSize)*blockSize; //integer division flooring
    }
    public long nextHigherBlockStartIndex(long raw_index) {
        if(raw_index % blockSize != 0)
            return (raw_index/blockSize + 1)*blockSize; //integer division flooring
        else
            return raw_index;
    }
    private void setBlockRangeUsed(long start, long end) {
        if(start % blockSize != 0) throw new IllegalStateException();
        long range = end - start;
        int firstBlockID = (int) (start/blockSize);
//        long numBlocks = range/blockSize;
        int overlapLastBlock = (int) (range%blockSize);
        int lastBlockID = (int) (end / blockSize);

        for(int i=firstBlockID;i<lastBlockID;i++) {
            setBlockSize(i, blockSize);
        }
        if(overlapLastBlock != 0)
            setBlockSize(lastBlockID, overlapLastBlock);
    }
    private void setBlockRangeUnused(long start, long end) {
        if(start % blockSize != 0) throw new IllegalStateException();
        long range = end - start;
        int firstBlockID = (int) (start/blockSize);
//        long numBlocks = range/blockSize;
        int overlapLastBlock = (int) (range%blockSize);
        int lastBlockID = (int) (end / blockSize);

        for(int i=firstBlockID;i<lastBlockID;i++) {
            setBlockSize(i, 0);
        }
//        if(getBlockSize(lastBlockID) == overlapLastBlock)
            setBlockSize(lastBlockID, 0);
//        else
//            setBlockSize(lastBlockID, 0);
    }
    private void setBlocksAllClear() {
        int stepSize = 1024;
        byte[] empty = new byte[1024];
        for(int i=0;i<getHeaderLength();i+=stepSize) {
            System.out.println(getHeaderLength());
            System.out.println(i);
            System.out.println(i+stepSize);
            System.out.println();
            if(i + stepSize >= getHeaderLength())
                header.set(i, new byte[(i+stepSize) - getHeaderLength()]);
            else
                header.set(i, empty);
        }
    }


    private int getLargestUsedBlock() {
        int stepSize = 1024;
        for(int i=getHeaderLength();i>=0;i-=stepSize) {
            if(i - stepSize < 0)
                return containsAnythingButZeroes(header.sub(0, i))?1:0;
            if(containsAnythingButZeroes(header.sub(i-stepSize, i)))
                return i/blockSize;
        }
        return 0;
    }
    private int getRealLength() {
        int counter = 0;
        int stepSize = 1024;
        for(int i=getHeaderLength();i>=0;i-=stepSize) {
            if(i - stepSize < 0)
                counter += sum(header.sub(0, i));
            else
                counter += sum(header.sub(i-stepSize, i));
        }
        return counter;
    }




    public void trimToSize() {
        delete((getLargestUsedBlock() + 1)*blockSize, underlying.contentSize());
    }

    //HELPER:
    private static double ld(int number) {
        return Math.log(number)/Math.log(2);
    }
    private static boolean containsAnythingButZeroes(byte[] arr) {
        for(int i=0;i<arr.length;i++)
            if(arr[i] != 0)
                return true;
        return false;
    }
    private static int sum(byte[] arr) {
        int counter = 0;
        for(byte b:arr) counter+=b;
        return counter;
    }
}
