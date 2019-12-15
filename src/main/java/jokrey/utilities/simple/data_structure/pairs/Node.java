package jokrey.utilities.simple.data_structure.pairs;

/**
 * @author jokrey
 */
public class Node<E> {
    public final E val;
    public Node<E> next;
    public Node(E val, Node<E> next) {
        this.val = val;
        this.next = next;
    }

    public static String recursivePrint(Object val, Object next) {
        String val_str = (val==null?"":val.toString());
        String next_str = (next==null?"":next.toString()); //recursive call, if toString() calls this method with new elements - therefore this method stops as
        String split_str = (val_str.isEmpty() && next_str.isEmpty()? "null" : val_str.isEmpty() || next_str.isEmpty() ? "": " -> ");
        return val_str+split_str+next_str;
    }

    @Override public String toString() {
        return recursivePrint(val, next);
    }
}