package jokrey.utilities.debug_analysis_helper;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Marks calls to a something identified by string.
 *
 * @author jokrey
 */
public class CallCountMarker {
    private static final HashMap<String, Long> stored_calls = new HashMap<>();
    public static long mark_call(String call_name) {
        Long old_count = stored_calls.get(call_name);
        long new_count = old_count==null?1:old_count+1;
        stored_calls.put(call_name, new_count);
        return new_count;
    }
    public static void mark_call_print(String call_name) {
        long new_count = mark_call(call_name);
        System.out.println(call_name+": "+new_count);
    }
    public static long print(String call_name) {
        return get_count(call_name);
    }
    public static long get_count(String call_name) {
        Long count = stored_calls.get(call_name);
        return count == null ? -1 : count;
    }
    public static void print_all() {
        System.out.println("=== Printing Counted Calls ===");
        for(Map.Entry<String, Long> e:stored_calls.entrySet()) {
            System.out.println(e.getKey() + " - "+e.getValue());
        }
        System.out.println("=== End End EndEnd End End ===");
    }

    @SuppressWarnings("unchecked")
    public static Call_Count[] get_all() {
        Map.Entry<String, Long>[] entries = stored_calls.entrySet().toArray(new Map.Entry[0]);
        Call_Count[] all = new Call_Count[entries.length];
        for(int i=0;i<all.length;i++)
            all[i] = new Call_Count(entries[i].getKey(), entries[i].getValue());
        return all;
    }

    public static void clear() {
        stored_calls.clear();
    }

    public static void remove(String key) {
        stored_calls.remove(key);
    }


    public static class Call_Count {
        public final String name;
        public final long count;
        public Call_Count(String name, long count) {
            this.name = name;
            this.count = count;
        }


        //GENERATED::
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Call_Count that = (Call_Count) o;
            return count == that.count &&
                    Objects.equals(name, that.name);
        }
        @Override public int hashCode() {
            return Objects.hash(name, count);
        }
        @Override public String toString() {
            return "Call_Count{" + "name='" + name + '\'' + ", count=" + count + '}';
        }
    }
}
