package jokrey.utilities.encoder.as_union.li;

import jokrey.utilities.encoder.as_union.Position;
import jokrey.utilities.transparent_storage.TransparentStorage;

/**
 * @author jokrey
 */
public class LIPosition extends Position {
    public long pointer;
    public LIPosition(long pointer) {
        this.pointer = pointer;
    }
    @Override public boolean hasNext(TransparentStorage storage) {
        return pointer < storage.contentSize();
    }

    @Override public String toString() {
        return "LIPosition{pointer=" + pointer + '}';
    }
}
