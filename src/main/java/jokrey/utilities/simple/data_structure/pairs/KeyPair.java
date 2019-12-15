package jokrey.utilities.simple.data_structure.pairs;

import java.util.Objects;

/**
 * Generic pair, where equals and hashCode are only dependent on the key/left/first value.
 * Immutable when and only when R and L are immutable.
 */
public class KeyPair<L,R> {
    public final L l;
    public final R r;

    public KeyPair(L left, R right) {
        r = right;
        l = left;
    }

    public L getLeft() {
        return l;
    }
    public R getRight() {
        return r;
    }

    public L getFirst() {
        return l;
    }
    public R getSecond() {
        return r;
    }

    public L getKey() {
        return l;
    }
    public R getValue() {
        return r;
    }

    @Override public int hashCode() { return l.hashCode(); }
    @Override public boolean equals(Object obj) {
        if (obj instanceof KeyPair) {
            KeyPair o = (KeyPair) obj;
            return Objects.equals(l, o.l);
        }
        return false;
    }
    @Override public String toString() {
        return "[Pair: l=\""+l+"\", r=\""+r+"\"]";
    }
}