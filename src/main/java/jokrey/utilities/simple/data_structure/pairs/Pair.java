package jokrey.utilities.simple.data_structure.pairs;

import java.util.Objects;

/**
 * Generic pair.
 * Immutable when and only when R and L are immutable.
 */
public class Pair<L,R> {
    public final R r;
    public final L l;

    public Pair(L left, R right) {
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

    @Override public int hashCode() { return (l.hashCode()*13) ^ r.hashCode(); }
    @Override public boolean equals(Object obj) {
        if (obj instanceof Pair) {
            Pair o = (Pair) obj;
            return Objects.equals(l, o.l) && Objects.equals(r, o.r);
        }
        return false;
    }
    @Override public String toString() {
        return "[Pair: l=\""+l+"\", r=\""+r+"\"]";
    }
}