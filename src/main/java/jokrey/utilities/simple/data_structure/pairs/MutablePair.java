package jokrey.utilities.simple.data_structure.pairs;

import java.util.Objects;

/**
 * Mutable Pair
 * @author jokrey
 */
public class MutablePair<L, R> {
    public L l;
    public R r;

    public MutablePair(L left, R right) {
        l = left;
        r = right;
    }

    public L getLeft() {
        return l;
    }
    public void setLeft(L left) {
        l = left;
    }
    public R getRight() {
        return r;
    }
    public void setRight(R right) {
        r = right;
    }

    public L getKey() {
        return l;
    }
    public void setKey(L key) {
        l = key;
    }
    public R getValue() {
        return r;
    }
    public void setValue(R value) {
        r = value;
    }

    public L getFirst() {
        return l;
    }
    public void setFirst(L first) {
        l = first;
    }
    public R getSecond() {
        return r;
    }
    public void setSecond(R second) {
        r = second;
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
