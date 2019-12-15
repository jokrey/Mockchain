package jokrey.utilities.encoder.as_union.lb.bytes;

import jokrey.utilities.encoder.as_union.Position;
import jokrey.utilities.transparent_storage.TransparentStorage;

import static jokrey.utilities.encoder.as_union.lb.bytes.LBLIbae.BLOCK_SIZE;

/**
 * @author jokrey
 */
public class BlockPosition extends Position {
    public int pointer;
    public BlockPosition(int pointer) {
        this.pointer = pointer;
    }

    @Override public boolean hasNext(TransparentStorage storage) {
        return pointer*BLOCK_SIZE < storage.contentSize();
    }

    @Override public String toString() {
        return "BlockPosition{" +
                "pointer=" + pointer +
                '}';
    }


}