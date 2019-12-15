package jokrey.utilities.debug_analysis_helper;

import java.util.*;

/**
 * Utilises CallCountMarker and TimeDiffMarker
 */
public class AverageCallTimeMarker {
    private static final HashMap<String, Long> stored_call_average = new HashMap<>();
    private static final HashMap<String, Long> stored_call_min = new HashMap<>();
    private static final HashMap<String, Long> stored_call_max = new HashMap<>();
    public static void mark_call_start(String call_name) {
        TimeDiffMarker.setMark(call_name);
    }
    public static void mark_call_end(String call_name) {
        long new_dif = TimeDiffMarker.getDiffFor_in_nano(call_name); //first to minimize overhead.

        CallCountMarker.mark_call(call_name);

        stored_call_average.merge(call_name, new_dif, (a, b) -> (a + b) / 2);
        stored_call_max.merge(call_name, new_dif, Math::max);
        stored_call_min.merge(call_name, new_dif, Math::min);
    }
    public static void print(String call_name) {
        print(new Call_Count_Average_Min_Max(call_name, CallCountMarker.get_count(call_name), stored_call_average.get(call_name), stored_call_max.get(call_name), stored_call_min.get(call_name)));
    }
    public static void print_all() {
        print_all(get_all(), null);
    }
    public static void print_all(String first_line_print) {
        print_all(get_all(), first_line_print);
    }
    public static void print_all(Call_Count_Average_Min_Max[] all, String first_line_print) {
        System.out.println("=== Printing ------- Calls ===");
        if(first_line_print!=null)System.out.println(first_line_print);
        for(Call_Count_Average_Min_Max e:all) {
            print(e);
        }
        System.out.println("=== End End EndEnd End End ===");
    }
    public static Call_Count_Average_Min_Max[] combine_all(Call_Count_Average_Min_Max[]... all) {
        ArrayList<Call_Count_Average_Min_Max> combined = new ArrayList<>();
        for(Call_Count_Average_Min_Max[] a:all)
            Collections.addAll(combined, a);

        LinkedList<Call_Count_Average_Min_Max> removed = new LinkedList<>();
        for(int i=0;i<combined.size();i++) {
            Call_Count_Average_Min_Max l = combined.get(i);

            for(int i2=0;i2<combined.size();i2++) {
                Call_Count_Average_Min_Max s = combined.get(i2);

                if(i!=i2 && l.name.equals(s.name)) { //can occur multiple times
                    l = new Call_Count_Average_Min_Max(l.name, l.count + s.count, (l.average_in_nano + s.average_in_nano) / 2, Math.max(l.max_in_nano, s.max_in_nano), Math.min(l.min_in_nano, s.min_in_nano));
                    removed.add(s);
                }
            }
            combined.set(i, l);
        }

        for(Call_Count_Average_Min_Max remove:removed)
            combined.remove(remove);

        return combined.toArray(new Call_Count_Average_Min_Max[0]);
    }

    private static void print(Call_Count_Average_Min_Max e) {
        System.out.println(e.toString());
    }

    public static void clear() {
        for(Map.Entry<String, Long> e:stored_call_average.entrySet()) {
            CallCountMarker.remove(e.getKey());
            TimeDiffMarker.remove(e.getKey());
        }
        stored_call_average.clear();
        stored_call_max.clear();
        stored_call_min.clear();
    }

    @SuppressWarnings("unchecked")
    public static Call_Count_Average_Min_Max[] get_all() {
        CallCountMarker.Call_Count[] counts = CallCountMarker.get_all();
        Call_Count_Average_Min_Max[] all = new Call_Count_Average_Min_Max[counts.length];
        for(int i=0;i<all.length;i++)
            all[i] = new Call_Count_Average_Min_Max(counts[i].name, counts[i].count, stored_call_average.get(counts[i].name), stored_call_max.get(counts[i].name), stored_call_min.get(counts[i].name));
        Arrays.sort(all, Comparator.comparing(o -> o.name));
        return all;
    }



    public static class Call_Count_Average_Min_Max {
        public final String name;
        public final long count;
        public final long average_in_nano;
        public final long max_in_nano;
        public final long min_in_nano;
        public Call_Count_Average_Min_Max(String name, long count, long average_in_nano, long max_in_nano, long min_in_nano) {
            this.name = name;
            this.count = count;
            this.average_in_nano = average_in_nano;
            this.max_in_nano = max_in_nano;
            this.min_in_nano = min_in_nano;
        }


        //GENERATED::
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Call_Count_Average_Min_Max that = (Call_Count_Average_Min_Max) o;
            return count == that.count && average_in_nano == that.average_in_nano &&
                    max_in_nano == that.max_in_nano &&
                    min_in_nano == that.min_in_nano &&
                    Objects.equals(name, that.name);
        }
        @Override public int hashCode() {
            return Objects.hash(name, count, average_in_nano, max_in_nano, min_in_nano);
        }
        @Override public String toString() {
            return name + " - " + count + " - av: " + String.format("%.7f",(average_in_nano / 1e9)) + "s - max: " + String.format("%.7f",(max_in_nano / 1e9)) + "s - min: " + String.format("%.7f",(min_in_nano / 1e9)) + "s";
        }
    }
}
