package jokrey.utilities.encoder.tag_based.serialization.beanish;

import jokrey.utilities.encoder.tag_based.TagBasedEncoder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static jokrey.utilities.encoder.helper.ReflectionHelper.getWrap;

/**
 * Allows Bean's or Bean(ish) java objects to be serialized using TagBasedEncoder's.
 * For what is required of the objects please consult the {@link #serialize(TagBasedEncoder, Object)} and {@link #deserialize(TagBasedEncoder, Class)} methods documentation.
 *
 * @see TagBasedEncoder
 * @author jokrey
 */
public class ObjectEncoderBeanish {
    /**
     * Method will encode an Object into the provided encoder.
     *
     * If provided class c is a java bean, and has getter and setter for every "important" field
     *       (getter and setter found by {@link #findSuitableGetterSetterPairsForBean(Class)})
     *      and Class c has a no-arg constructor.
     *
     * Don't know about java beans? These are the minimum required constraints in this case:
     *    1. Every "important" field (i.e. a fields that influences the equals and hashCode methods) has to have a setter and a getter (as found by {@link #findSuitableGetterSetterPairsForBean(Class)}).
     *    (2. A no-arg constructor. - for deserialization only, but I assume that's the eventual goal here
     *
     * The getter have to return Types that are encodable using the chose encoder. If not an Exception will be thrown.
     *
     * @param encoder preferably empty (though it should also work if it isn't)
     * @param o java bean object
     * @return param encoder  (why not, and might create nicer call chains)
     * throws IllegalArgumentException if any of the constraints above are not followed
     */
    public static TagBasedEncoder serialize(TagBasedEncoder encoder, Object o) {
        Class c = o.getClass();
        List<Method[]> getter_setter_pairs = findSuitableGetterSetterPairsForBean(c);

        for(Method[] get_set:getter_setter_pairs) {
            Method getter = get_set[0];
            String name = getter.getName().substring(3);//to remove prefix "get"
            try {
                Object result = getter.invoke(o);
                encoder.addEntryT(name, result);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalArgumentException("Object could not be serialized, because a getter could not be called - "+e.getMessage());
            }
        }

        return encoder;
    }

    /**
     * Method will recreate an Object of type T from the provided encoder.
     *
     * If provided class c is a java bean, and the provided encoder contains all bean fields (which is guaranteed if encoder was used in {@link #serialize(TagBasedEncoder, Object)})
     *       (as found by {@link #findSuitableGetterSetterPairsForBean(Class)})
     *      and Class c has a no-arg constructor.
     *
     * Don't know about java beans? These are the minimum required constraints in this case:
     *    1. Every "important" field (i.e. a fields that influences the equals and hashCode methods) has to have a setter and a getter (as found by {@link #findSuitableGetterSetterPairsForBean(Class)}).
     *    2. A no-arg constructor.
     *
     * @param encoder preferably previously encoded using {@link #serialize(TagBasedEncoder, Object)}
     * @param c java bean class
     * @return recreated object
     * throws IllegalArgumentException if any of the constraints above are not followed
     */
    public static <T> T deserialize(TagBasedEncoder encoder, Class<T> c) {
        T inst;
        try {
            inst = c.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalArgumentException("Object could not be deserialized, because new object of Class c("+c+") " +
                    "could not be instantiated - missing no-args constructor? - "+e.getMessage());
        }

        List<Method[]> getter_setter_pairs = findSuitableGetterSetterPairsForBean(c);
        for(Method[] get_set:getter_setter_pairs) {
            Method setter = get_set[1];
            String name = setter.getName().substring(3);//to remove prefix "set"
            String type_class_name = getWrap(setter.getParameterTypes()[0]).getName();
            try {
                Class<?> type_class = Class.forName(type_class_name);

                setter.invoke(inst, encoder.getEntryT(name, type_class));
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException("Object could not be deserialized, because the type for ("+name+") was not recognised within this system - "+e.getMessage());
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalArgumentException("Object could not be serialized, because a setter could not be called - "+e.getMessage());
            }
        }
        return inst;
    }

    /**
     * Finds single field getter and setter in the specified class.
     *     methods have to start with "get" and "set" respectively.
     *     methods have to hold: void set(>type<), >type< get()
     *
     * @param c a java bean spec following data class
     * @return list of getter setter pairs. Arrays always size 2, [0] = getter, [1] = setter.
     */
    private static List<Method[]> findSuitableGetterSetterPairsForBean(Class c) {
        Method[] ms = c.getMethods();
        List<Method[]> getter_setter_pairs = new ArrayList<>();
        for(Method pset:ms) {
            //start with set because it is more unlikely that a lonely getter exists_in_cache.
            if(pset.getName().startsWith("set") && pset.getParameterCount() == 1 && pset.getReturnType().equals(void.class)) {
                for(Method pget:ms) {
                    if(pget.getName().startsWith("get") && pget.getParameterCount() == 0 &&
                            pset.getName().substring(3).equals(pget.getName().substring(3)) &&
                            pset.getParameterTypes()[0].equals(pget.getReturnType())) {
                        getter_setter_pairs.add(new Method[]{pget, pset});
                    }
                }
            }
        }
        return getter_setter_pairs;
    }
}
