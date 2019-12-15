package jokrey.utilities.encoder.helper;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Minor functionality addition to a concurrent multi map using CopyOnWriteArrayList
 * @author jokrey
 */
public class ConcurrentMultiMap<K, E> extends ConcurrentHashMap<K, CopyOnWriteArrayList<E>> {
    public void putEntry(K key, E entry) {
        computeIfAbsent(key, s -> new CopyOnWriteArrayList<>()).add(entry);

        //OLD: (maybe also works, but the above is much cleaner)
//        List<AuthenticatedRemoteObserverConnection> list = observers.get(user_name);
//        if(list==null) {
//            List<AuthenticatedRemoteObserverConnection> newList = new CopyOnWriteArrayList<>(); //has to be a concurrent list..
//            List<AuthenticatedRemoteObserverConnection> previousList = observers.putIfAbsent(user_name, newList);
//            if(previousList != null) //can happen if between observers.get and observers.putIfAbsent another thread had reached putIfAbsent
//                list = previousList;
//            else
//                list = newList;
//        }
//        //list is now guaranteed to exist
//        list.add(observer);

    }
    public void removeEntry(K key, E entry) {
        compute(key, (k, list) -> {
            if(list!=null) {
                list.remove(entry);
                return list.isEmpty() ? null : list;
            } else
                return null;
        });
    }
}
