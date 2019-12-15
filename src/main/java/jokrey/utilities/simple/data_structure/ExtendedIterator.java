package jokrey.utilities.simple.data_structure;

import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Iterator that additionally allows a more efficient skip function, a next function supporting array creation and
 * @author jokrey
 */
public interface ExtendedIterator<E> extends Iterator<E> {
    /**
     * Skips the next element in the iteration.
     * By default just calls next, disregarding the element.
     * But a sub iterator might do this more efficiently
     *
     * @throws NoSuchElementException if the iteration has no more elements
     */
    default void skip() {
        next();
    }

    /**
     * Calls {@link #next()} as often as limit or until {@link #hasNext()} returns false.
     * If limit is < 0 this method will call {@link #next()} as long as {@link #hasNext()} returns true.
     *
     * The results of {@link #next()} will be added to an array and returned.
     *
     * @param limit limitation for the next calls
     * @return array of at most length 'limit' (as long as 'limit' > 0) - array will always at least be length 1 (a length 0 array would instead throw a NoSuchElementException)
     * @throws NoSuchElementException if no element is available
     */
    default E[] next(int limit) {
        ArrayList<E> list = new ArrayList<>();
        E value = null;
        while((limit<0 || limit>0) && hasNext()) {
            value=next();
            list.add(value);
            limit--;
        }
        if(value==null)
            throw new NoSuchElementException("No 'next' elements available.");
        return createArrayFrom(list, value);
    }

    /**
     * Should work without previous calls to {@link #hasNext()}. If {@link #hasNext()} returns false, this method should return null.
     * Default version works, but it uses exceptions as flow control.
     * Should be overridden for a quicker version
     * @return either the next value or null if there is none
     * @see #next()
     */
    default E next_or_null() {
        try {
            return next();
        } catch(NoSuchElementException e) {
            return null;
        }
    }



    //HELPER FOR GENERIC ARRAY CREATION
    static Class getTypeClassFor(Class a_generic_sub_class) {
        return (Class)
                ((ParameterizedType) a_generic_sub_class.getGenericSuperclass())
                        .getActualTypeArguments()[0];
    }
    @SuppressWarnings("unchecked")
    static <E> E[] createArrayFrom(ArrayList<E> list, E non_null_value) {
        return createArrayFrom(list, non_null_value.getClass());
    }
    @SuppressWarnings("unchecked")
    static <E> E[] createArrayFrom(ArrayList<E> list, Class clazz) {
        E[] arr = (E[]) Array.newInstance(clazz, list.size());
        for(int i=0;i<arr.length;i++)
            arr[i] = list.get(i);
        return arr;
    }
}
