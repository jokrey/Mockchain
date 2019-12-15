package jokrey.utilities.encoder.as_union.lb.bytes;

import com.sun.xml.internal.messaging.saaj.util.ByteOutputStream;
import jokrey.utilities.bitsandbytes.BitHelper;
import jokrey.utilities.encoder.as_union.AsUnionEncoder;
import jokrey.utilities.transparent_storage.bytes.TransparentBytesStorage;

import java.util.Arrays;

/**
 * Idea:
 *    There are blocks of size 1024 (max is 2^16).
 *        1020 bytes are for data,
 *            the first 4 bytes[0:3] are a pointer to the next start block (big-endian, signed)
 *              ! A  0 (or 0x00000000) as a pointer here indicates end-of-all
 *                A  negative value here indicates end of sequence + (-pointerToStartOfNextBlock)
 *                    On end of sequence it is possible that not all bytes in the last block where used.
 *                    So this last block is special, in it we can only store 1018 bytes as data:
 *                   -> The next 2 bytes are used to indicate the length of this block (interpreted unsigned)
 *              !       A 0 (or 0x0000= here indicates that the block is deleted/unused/invalid
 *            The next block(start index) can be efficiently found using: pointer_value * 1024
 *        This entails that there is a maximum number of (Integer.MAX_VALUE) blocks allocated at any time.
 *            With block size of 1020 * Integer.MAX*2 + 1 - 2, that is roughly 2,000 Giga Bytes
 *    The number of currently allocated blocks is known.
 *        underlying size is always (size % 1024 == 0)
 *        Therefore number of blocks is:  =  size / 1024
 *      the amount of stored data is not immediately available - it would have to be counted
 *    Iteration:
 *        Block 0, is always the start block.
 *        while(next_start_block_pointer != 0 && counter != index)
 *            Link jump to next start block
 *    Decoding:
 *        When the start index of the block was found, the decoding is simple
 *        while(next_start_block_pointer > 0)
 *            addToDecoded(currentBlock)
 *        addToDecoded(currentBlock[0:liLastBlock])
 *    Deleting:
 *        pretty simple:
 *           while(next_start_block_pointer > 0)
 *               writeNextBlockPointer(-next_start_block_pointer)
 *               writeBlockLength(0)
 *    Appending(Encoding):
 *        1 - QuickAndDIRTY-Version - 1: PROBLEM: If exclusively used it HEAVILY segments the data, and it reallocates a ton of data
 *          Append blocks in sequence to the end of the data structure.
 *        2 - Simple-Version: PROBLEM: In O(n)
 *          Jump block by block until an empty block is found
 *          write block(though obviously wait until writing jump-to information)
 *          Continue jumping.
 *          on end of file, just keep appending block.
 *
 *     TODO CURRENTLY MISSING 3 - Smart(?)-Version: PROBLEM: Does this regularly work(?)
 *          0 - Jump block by block until an empty block is found, or eof
 *           1 - on eof -> just append blocks
 *           2 - on empty block -> fill block(but wait with writing until jump-to is confirmed)
 *              jump to the block that is stored as the next_block_pointer (its negative, but indicates the previously stored next block value - see deletion)
 *              if that block is also empty continue with 2, otherwise with 0
 *
 *
 * There are 4 types of block:
 *    0Block - header data to speed up certain important operations
 *        reserved: completely
 *        identified by: index (==0)
 *        only exists once - it's one mostly empty block containing header information
 *            the first 4 bytes are the address of the first block of the first sequence
 *            the next  4 bytes are the address of the first block of the last sequence
 *            the next  4 bytes are the address of the last block of the last sequence
 *    FBlock
 *        reserved: first 10 bytes
 *        identified by: pointed to by MBlock or 0Block
 *        first 4 bytes are a pointer to the next block in the same sequence
 *           i32 - positive or negative, by MBlock logic
 *        next 4 bytes are a pointer to the last block in the previous sequence
 *           i32 - should always be positive
 *        next 2 bytes are the valid bytes in the last MBlock of the sequence.
 *           u16 - always positive
 *    MBlock
 *        reserved: first 4 bytes
 *        identified by: linked to by FBlock
 *        first 4 bytes are interpreted(int32) as a pointer to either:
 *            the next block in the sequence (which can either be an MBlock or an FBlock) - when int32 is positive
 *            the next sequence's first block (which is an FBlock) - when int32 is negative (and != Integer.MIN_VALUE, could not really be that anyways)
 *    UnusedBlock
 *        reserved: first 8 bytes
 *        identified by: first 4 bytes interpreted as int32 are exactly Integer.MIN_VALUE
 *        next 4 bytes are the previous next block address (an address likely to be unused also).
 *
 *
 *
 * The system has to only hold three times BLOCK_SIZE of memory at any time during the algorithm.
 *     In addition to user-data, surrounding stack data, etc.
 *
 *
 * BUG::
 *
 *
 * @author jokrey
 */
public class LBLIbae implements AsUnionEncoder<byte[], BlockPosition> {
    private static final int UNUSED_MARKER = Integer.MIN_VALUE;

    public static final int BLOCK_SIZE = 1024;
    private static final int BLOCK_POINTER_SIZE = 4;
    private static final int BLOCK_LENGTH_INDICATOR_SIZE = (int) (Math.ceil( (Math.log(BLOCK_SIZE) / Math.log(2) ) / 8.0 ) );
    {
        System.out.println("BLOCK_SIZE = " + BLOCK_SIZE);
        System.out.println("BLOCK_POINTER_SIZE = " + BLOCK_POINTER_SIZE);
        System.out.println("BLOCK_LENGTH_INDICATOR_SIZE = " + BLOCK_LENGTH_INDICATOR_SIZE);
    }

    final TransparentBytesStorage storage;
    public LBLIbae(TransparentBytesStorage storage) {
        this.storage = storage;
    }

    public LBLIbae encode(byte[] data) {
        int blockCount = blockCount();
        byte[] block0 = loadBlock0();
        int firstBlockOfFirstSequence = getFirstBlockOfFirstSequence(block0);
        if(blockCount<=0) {
            byte[] gen = new byte[BLOCK_SIZE];
            writeFirstBlockOfFirstSequence(gen, 1);
            storeBlock0(gen);
            block0=gen;
            blockCount = 1;
        }
        int previousLastBlockOfLastSequence = getLastBlockOfLastSequence(block0);


        int currentBlock = linearSearchNextEmptyBlockId(1, blockCount);
        int firstBlockId = currentBlock;
        int dataWritten = 0;
        while (true) {
            int nextBlock;
            boolean isFirstBlock = dataWritten == 0;
            boolean isLastBlock = dataWritten + (BLOCK_SIZE-(isFirstBlock?getFBlockHeaderSize():getMBlockHeaderSize())) >= data.length;
            System.out.println("1 - firstBlock="+isFirstBlock+", lastBlock="+isLastBlock+", currentBlock="+currentBlock);
            debugPrintAllBlocks();
            if (isFirstBlock) { //first block
                if(isLastBlock) {
                    writeFBlock(currentBlock, data, previousLastBlockOfLastSequence, 0);
                    break;
                } else {
                    nextBlock = linearSearchNextEmptyBlockId(currentBlock + 1, blockCount);
                    dataWritten = writeFBlock(currentBlock, data, previousLastBlockOfLastSequence, nextBlock);
                }
            } else if(isLastBlock) { //last block
                writeMBlock(currentBlock, data, dataWritten, 0);
                break;
            } else {
                nextBlock = linearSearchNextEmptyBlockId(currentBlock + 1, blockCount);
                dataWritten = writeMBlock(currentBlock, data, dataWritten, nextBlock);
            }
            currentBlock = nextBlock;
            blockCount = Math.max(blockCount, nextBlock);
        }
        System.out.println("2");
        debugPrintAllBlocks();

        writeLastBlockOfLastSequence(block0, currentBlock);
        if(firstBlockOfFirstSequence == 0) {
            writeFirstBlockOfFirstSequence(block0, firstBlockId);
        }

        if(previousLastBlockOfLastSequence != 0) {
            storage.set(getRawBlockStart(previousLastBlockOfLastSequence), BitHelper.getBytes(-firstBlockId));
        }

        storeBlock0(block0);

        System.out.println("3");
        debugPrintAllBlocks();
        return this;
    }

    public byte[] decode(BlockPosition pos) {
        ByteOutputStream out = new ByteOutputStream();
        if(pos.pointer < 0 || !pos.hasNext(storage))
            return null;
        if(pos.pointer==0) {
            byte[] block0 = loadBlock0();
            pos.pointer = getFirstBlockOfFirstSequence(block0);
        }
        byte[] block = loadBlock(pos.pointer);
        int blockLength = getBlockLength(block);
        pos.pointer= getNextPointer(block);
        if(pos.pointer <= 0) {
            out.write(block, getFBlockHeaderSize(), blockLength);

            validatePointerIsInNextSequence(pos, blockCount());
            return out.toByteArray();
        } else {
            out.write(block, getFBlockHeaderSize(), BLOCK_SIZE-getFBlockHeaderSize());
        }
        while(true) {
            block = loadBlock(pos.pointer);
            pos.pointer = getNextPointer(block);
            if(pos.pointer <= 0) {
                out.write(block, getMBlockHeaderSize(), blockLength);

                validatePointerIsInNextSequence(pos, blockCount());
                return out.toByteArray();
            } else {
                out.write(block, getMBlockHeaderSize(), block.length-getMBlockHeaderSize());
            }
        }
    }

    //SAME CODE AS DECODE; BUT ONLY COUNTS THE SKIPPED BYTES
    @Override public long skipEntry(BlockPosition pos) {
        long bytesSkipped = 0;
        if(pos.pointer < 0 || !pos.hasNext(storage))
            return -1;
        if(pos.pointer==0) {
            byte[] block0 = loadBlock0();
            pos.pointer = getFirstBlockOfFirstSequence(block0);
        }
        byte[] block = loadBlock(pos.pointer);
        int blockLength = getBlockLength(block);
        pos.pointer= getNextPointer(block);
        if(pos.pointer <= 0) {
            bytesSkipped += blockLength;

            validatePointerIsInNextSequence(pos, blockCount());
            return bytesSkipped;
        } else {
            bytesSkipped += BLOCK_SIZE-getFBlockHeaderSize();
        }
        while(true) {
            block = loadBlock(pos.pointer);
            pos.pointer = getNextPointer(block);
            if(pos.pointer <= 0) {
                bytesSkipped += blockLength;

                validatePointerIsInNextSequence(pos, blockCount());
                return bytesSkipped;
            } else {
                bytesSkipped += block.length-getMBlockHeaderSize();
            }
        }
    }



    @Override public byte[] deleteEntry(BlockPosition pos) {
        ByteOutputStream out = new ByteOutputStream();
        if(pos.pointer < 0 || !pos.hasNext(storage))
            return null;
        int virtualPointer = pos.pointer;
        byte[] block0 = loadBlock0();
        if(virtualPointer==0) {
            virtualPointer = getFirstBlockOfFirstSequence(block0);
        }
        int startBlock = virtualPointer;

        byte[] block = loadBlock(virtualPointer);
        int previousLastBlock = getPreviousPointer(block); //get the block who's pointer was previously pointing to this block/sequence
        int blockLength = getBlockLength(block);
        setUnused(virtualPointer);
        virtualPointer = getNextPointer(block);

        //FBlock
        if(virtualPointer <= 0) {
            if(virtualPointer == UNUSED_MARKER)
                throw new IllegalStateException("Blocks at requested position has been deleted or does not exist, consider calling reset.");
            out.write(block, getFBlockHeaderSize(), blockLength);

            if(previousLastBlock != 0)
                setNextPointer(previousLastBlock, virtualPointer); //relink previous block to next block(essentially skipping the deleted block.
                                                                   //important: virtualPointer is negative here, so that the previous last block is still a 'last' block

            virtualPointer = -virtualPointer; //important, pointer is required to have a valid position after
            if(virtualPointer!=0)
                setPreviousPointer(virtualPointer, previousLastBlock); //relink previousPointer of nextBlock(pointing to this block, to out previous)
            updateBlock0AfterSequenceDeletion(block0, startBlock, startBlock, virtualPointer, previousLastBlock);

            pos.pointer = virtualPointer; //valid, hopefully not deleted position after
            validatePointerIsInNextSequence(pos, blockCount());
            return out.toByteArray();
        } else {
            out.write(block, getFBlockHeaderSize(), BLOCK_SIZE-getFBlockHeaderSize());
        }

        //MBlocks
        while(true) {
            block = loadBlock(virtualPointer);
            setUnused(virtualPointer);
            int previousVirtualPointer = virtualPointer;
            virtualPointer = getNextPointer(block);
            if(virtualPointer <= 0) {
                out.write(block, getMBlockHeaderSize(), blockLength);
                if(previousLastBlock != 0)
                    setNextPointer(previousLastBlock, virtualPointer); //relink previous block to next block(essentially skipping the deleted block.
                                                                       //important: virtualPointer is negative here, so that the previous last block is still a 'last' block

                virtualPointer = -virtualPointer; //important, pointer is required to have a valid position after
                if(virtualPointer!=0)
                    setPreviousPointer(virtualPointer, previousLastBlock); //relink previousPointer of nextBlock(pointing to this block, to out previous)
                updateBlock0AfterSequenceDeletion(block0, startBlock, previousVirtualPointer, virtualPointer, previousLastBlock);

                pos.pointer = virtualPointer; //valid, hopefully not deleted position after
                validatePointerIsInNextSequence(pos, blockCount());
                return out.toByteArray();
            } else {
                out.write(block, getMBlockHeaderSize(), block.length-getMBlockHeaderSize());
            }
        }
    }

    @Override public long delete(BlockPosition pos) {
        long bytesDeleted = 0;
        if(pos.pointer < 0 || !pos.hasNext(storage))
            return -1;
        int virtualPointer = pos.pointer;
        byte[] block0 = loadBlock0();
        if(virtualPointer==0) {
            virtualPointer = getFirstBlockOfFirstSequence(block0);
        }
        int startBlock = virtualPointer;

        byte[] block = loadBlock(virtualPointer);
        int previousLastBlock = getPreviousPointer(block); //get the block who's pointer was previously pointing to this block/sequence
        int blockLength = getBlockLength(block);
        setUnused(virtualPointer);
        virtualPointer = getNextPointer(block);

        //FBlock
        if(virtualPointer <= 0) {
            if(virtualPointer == UNUSED_MARKER)
                throw new IllegalStateException("Blocks at requested position has been deleted or does not exist, consider calling reset.");
            bytesDeleted += blockLength;

            if(previousLastBlock != 0)
                setNextPointer(previousLastBlock, virtualPointer); //relink previous block to next block(essentially skipping the deleted block.
                                                                   // important: virtualPointer is negative here, so that the previous last block is still a 'last' block

            virtualPointer = -virtualPointer; //important, pointer is required to have a valid position after
            if(virtualPointer!=0)
                setPreviousPointer(virtualPointer, previousLastBlock); //relink previousPointer of nextBlock(pointing to this block, to out previous)
            updateBlock0AfterSequenceDeletion(block0, startBlock, startBlock, virtualPointer, previousLastBlock);

            pos.pointer = virtualPointer; //valid, hopefully not deleted position after
            validatePointerIsInNextSequence(pos, blockCount());
            return bytesDeleted;
        } else {
            bytesDeleted += BLOCK_SIZE-getFBlockHeaderSize();
        }

        //MBlocks
        while(true) {
            block = loadBlock(virtualPointer);
            setUnused(virtualPointer);
            int previousVirtualPointer = virtualPointer;
            virtualPointer = getNextPointer(block);
            if(virtualPointer <= 0) {
                bytesDeleted += blockLength;

                if(previousLastBlock != 0)
                    setNextPointer(previousLastBlock, virtualPointer); //relink previous block to next block(essentially skipping the deleted block.
                                                                       // important: virtualPointer is negative here, so that the previous last block is still a 'last' block

                virtualPointer = -virtualPointer; //important, pointer is required to have a valid position after
                if(virtualPointer!=0)
                    setPreviousPointer(virtualPointer, previousLastBlock); //relink previousPointer of nextBlock(pointing to this block, to out previous)
                updateBlock0AfterSequenceDeletion(block0, startBlock, previousVirtualPointer, virtualPointer, previousLastBlock);

                pos.pointer = virtualPointer; //valid, hopefully not deleted position after
                validatePointerIsInNextSequence(pos, blockCount());
                return bytesDeleted;
            } else {
                bytesDeleted += BLOCK_SIZE-getMBlockHeaderSize();
            }
        }
    }

    public BlockPosition forIndex(int index) {
        byte[] block0 = loadBlock0();
        int blockCount = blockCount();
        if(index>blockCount) throw new IndexOutOfBoundsException();
        int counter = 0;

        int currentBlockId = getFirstBlockOfFirstSequence(block0);
        while(currentBlockId != 0 && index != counter) {
            currentBlockId = loadNextPointer(currentBlockId);
            if(currentBlockId < 0) {
                currentBlockId = -currentBlockId;
                counter++;
            }
        }
        return new BlockPosition(currentBlockId);
    }





    private void validatePointerIsInNextSequence(BlockPosition pos, int blockCount) {
        if(pos.pointer < 0)
            pos.pointer = -pos.pointer;
        else if(pos.pointer == 0) {
            pos.pointer = linearSearchNextEmptyBlockId(1, blockCount);
        }
    }
    private void updateBlock0AfterSequenceDeletion(byte[] block0, int deletedSequenceStartBlockId, int deletedSequenceLastBlockId,
                                                   int deletedSequenceLastBlock_NextPointer, int deletedSequence_previousSequenceLastBlock) {
        int previousFirstBlockOfFirstSequence = getFirstBlockOfFirstSequence(block0);
        int previousLastBlockOfLastSequence = getLastBlockOfLastSequence(block0);

        boolean changed = false;
        if(deletedSequenceStartBlockId == previousFirstBlockOfFirstSequence) { // deleted sequence was first
            writeFirstBlockOfFirstSequence(block0, deletedSequenceLastBlock_NextPointer);
            changed=true;
        }
        if(deletedSequenceLastBlockId == previousLastBlockOfLastSequence) { //deleted sequence was last
            writeLastBlockOfLastSequence(block0, deletedSequence_previousSequenceLastBlock);
            changed=true;
        }
        if(changed)
            storeBlock0(block0);
    }




    @Override public BlockPosition reset() {
        return new BlockPosition(0);
    }
    @Override public TransparentBytesStorage getRawStorage() {
        return storage;
    }




    private int blockCount() {
        long storageSize = storage.contentSize();
        if(storageSize % BLOCK_SIZE != 0) throw new IllegalStateException(String.valueOf(storageSize % BLOCK_SIZE));
        return (int) (storageSize / BLOCK_SIZE);
    }
    private byte[] loadBlock(int blockId) {
        return storage.sub(getRawBlockStart(blockId), getRawBlockEnd(blockId) );
    }
    private int linearSearchNextEmptyBlockId(int searchStart, int blockCount) {
        for(int i=searchStart;i<blockCount;i++)
            if(isUnused(i))
                return i;
        return Math.max(1, Math.max(searchStart, blockCount));
        //TODO: BETTER SEARCH; WHEN UNUSED WAS FOUND
    }
    private int getRawBlockStart(int blockNumber) {
        return blockNumber * BLOCK_SIZE;
    }
    private int getRawBlockEnd(int blockNumber) {
        return (blockNumber + 1) * BLOCK_SIZE;
    }
    private int loadNextPointer(int blockNumber) {
        return BitHelper.getInt32From(storage.sub(getRawBlockStart(blockNumber), getRawBlockStart(blockNumber)+4), 0);
    }
    private void setNextPointer(int blockNumber, int newNextPointer) {
        storage.set(getRawBlockStart(blockNumber), BitHelper.getBytes(newNextPointer));
    }
    private void setPreviousPointer(int blockNumber, int newPreviousLastBlockPointer) {
        storage.set(getRawBlockStart(blockNumber)+4, BitHelper.getBytes(newPreviousLastBlockPointer));
    }
    private int getNextPointer(byte[] block) {
        return BitHelper.getInt32From(block, 0);
    }
    private int getPreviousPointer(byte[] block) {
        return BitHelper.getInt32From(block, 4);
    }
    private int getBlockLength(byte[] block) {
        return (int) BitHelper.getIntFromNBytes(block, 8, BLOCK_LENGTH_INDICATOR_SIZE);
    }
    private void overwritePreviousPointer(byte[] block, int newPointer) {
        BitHelper.writeInt32(block, 4, newPointer);
    }
    private void overwriteNextPointer(byte[] block, int newPointer) {
        BitHelper.writeInt32(block, 0, newPointer);
    }
    private void overwriteBlockLength(byte[] block, int newLength) {
        BitHelper.writeInNBytes(block, 8, newLength, BLOCK_LENGTH_INDICATOR_SIZE);
    }


    //0-Block functionality::
    private int getFirstBlockOfFirstSequence(byte[] block0) {
        return block0.length==0?0: BitHelper.getInt32From(block0, 0);
    }
    private int getLastBlockOfLastSequence(byte[] block0) {
        return BitHelper.getInt32From(block0, 4);
    }
    private void writeFirstBlockOfFirstSequence(byte[] block0, int blockId) {
        BitHelper.writeInt32(block0, 0, blockId);
    }
    private void writeLastBlockOfLastSequence(byte[] block0, int blockId) {
        BitHelper.writeInt32(block0, 4, blockId);
    }
    private byte[] loadBlock0() {
        return storage.sub(0, BLOCK_SIZE);
    }
    private void storeBlock0(byte[] block0) {
        storage.set(0, block0);
    }


    //FBlock functionality::
    private int writeFBlock(int blockNumber, byte[] data, int previousBlockAddress, int nextBlockAddress) {
        byte[] header = createFBlockHeader(data.length, previousBlockAddress, nextBlockAddress);
        storage.set(getRawBlockStart(blockNumber), header);
        storage.set(getRawBlockStart(blockNumber) + header.length, data, 0, BLOCK_SIZE - header.length);
        return BLOCK_SIZE - header.length;
    }
    private static int getFBlockHeaderSize() {
        return BLOCK_POINTER_SIZE*2 + BLOCK_LENGTH_INDICATOR_SIZE;
    }
    private byte[] createFBlockHeader(int totalDataLength, int previousBlockAddress, int nextBlockAddress) {
        byte[] header_LBlock = new byte[getFBlockHeaderSize()];
        int bytesInLastBlock;
        if(totalDataLength <= BLOCK_SIZE - getFBlockHeaderSize()) { //fits into first block
            bytesInLastBlock = totalDataLength;
        } else {
            bytesInLastBlock = totalDataLength;
            bytesInLastBlock-=(BLOCK_SIZE - getFBlockHeaderSize());
            bytesInLastBlock %= (BLOCK_SIZE - getMBlockHeaderSize());
            if(bytesInLastBlock == 0)
                bytesInLastBlock = (BLOCK_SIZE - 4);
        }

        overwriteNextPointer(header_LBlock, nextBlockAddress);
        overwritePreviousPointer(header_LBlock, previousBlockAddress);
        overwriteBlockLength(header_LBlock, bytesInLastBlock);
        return header_LBlock;
    }


    //MBlock functionality::
    private int writeMBlock(int blockNumber, byte[] data, int dataPartStart, int nextBlockAddress) {
        byte[] header = createMBlockHeader(nextBlockAddress);
        storage.set(getRawBlockStart(blockNumber), header);
        storage.set(getRawBlockStart(blockNumber)+header.length, data, dataPartStart, dataPartStart + BLOCK_SIZE - header.length);
        return dataPartStart + BLOCK_SIZE - header.length;
    }
    private static int getMBlockHeaderSize() {
        return BLOCK_POINTER_SIZE;
    }
    private byte[] createMBlockHeader(int nextBlockAddress) {
        byte[] header_MBlock = new byte[getMBlockHeaderSize()];
        overwriteNextPointer(header_MBlock, nextBlockAddress);
        return header_MBlock;
    }


    //UnusedBlock functionality::
    private boolean isUnused(int blockId) {
        int rawBlockBoundsStart = getRawBlockStart(blockId);
        byte[] block = storage.sub(rawBlockBoundsStart, rawBlockBoundsStart+4);
        int nextPointer = getNextPointer(block);
        return nextPointer==UNUSED_MARKER;
    }
    private void setUnused(int blockId) {
        int rawBlockBoundsStart = getRawBlockStart(blockId);
        storage.set(rawBlockBoundsStart, BitHelper.getBytes(UNUSED_MARKER));
        //TODO: fulfill contract
    }






    public void debugPrintAllBlocks() {
        System.out.println("allBlocks:");
        byte[] block0 = loadBlock0();
        debugPrintlnBlock0(block0);
        for(int i=1;i<blockCount();i++) {
            byte[] blocki = loadBlock(i);
            int nxtP = getNextPointer(blocki);
            if(nxtP == UNUSED_MARKER)
                System.out.println("[UNUSED("+i+")]");
            else{
                System.out.println("[ifF-BLOCK(" + i + "): nxtP: " + nxtP + ", prvP: " + getPreviousPointer(blocki) + ", len: " + getBlockLength(blocki) + ", dat: " + Arrays.toString(Arrays.copyOfRange(blocki, getFBlockHeaderSize(), blocki.length)) + "]");
                System.out.println("[ifM-BLOCK(" + i + "): nxtP: " + nxtP + ", dat: " + Arrays.toString(Arrays.copyOfRange(blocki, getMBlockHeaderSize(), blocki.length)) + "]");
            }
        }
    }

    private void debugPrintlnBlock0(byte[] block0) {
        if(block0.length==BLOCK_SIZE)
            System.out.println("[BLOCK0: fofP: "+getFirstBlockOfFirstSequence(block0)+", lolP: "+getLastBlockOfLastSequence(block0)+"]");
        else
            System.out.println("[BLOCK0: EMPTY]");
    }
}