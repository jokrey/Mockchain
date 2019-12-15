package jokrey.utilities.simple.data_structure.pairs;

import java.util.Arrays;

/**
 * Generic pair, where equals and hashCode are only dependent on the key/left/first value.
 * Immutable when and only when R and L are immutable.
 */
public class ArrayKeyPair<L,R> {
    public final L[] l;
    public final R r;

    public ArrayKeyPair(L[] left, R right) {
        r = right;
        l = left;
    }

    public L[] getLeft() {
        return l;
    }
    public R getRight() {
        return r;
    }

    public L[] getFirst() {
        return l;
    }
    public R getSecond() {
        return r;
    }

    public L[] getKey() {
        return l;
    }
    public R getValue() {
        return r;
    }

    @Override public int hashCode() { return Arrays.hashCode(l); }
    @Override public boolean equals(Object obj) {
        if (obj instanceof ArrayKeyPair) {
            ArrayKeyPair o = (ArrayKeyPair) obj;
            return Arrays.equals(l, o.l);
        }
        return false;
    }
    @Override public String toString() {
        return "[Pair: l=\""+ Arrays.toString(l) +"\", r=\""+r+"\"]";
    }
}