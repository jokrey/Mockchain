package jokrey.utilities.debug_analysis_helper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Utilises CallCountMarker and TimeDiffMarker
 */
public class BoxPlotDataGatherer {
    private static final HashMap<String, ArrayList<Long>> stored_calls = new HashMap<>();
    public static void mark_call_start(String call_name) {
        TimeDiffMarker.setMark(call_name);
    }
    public static void mark_call_end(String call_name) {
        long new_dif = TimeDiffMarker.getDiffFor_in_nano(call_name); //first to minimize overhead.

        stored_calls.computeIfAbsent(call_name, k -> new ArrayList<>()).add(new_dif);
    }
    public static void print_all() {
        print_all(false);
    }
    public static void print_all(boolean removeFirst) {
        System.out.println("=== Printing ------- Calls ===");
        for(Map.Entry<String, ArrayList<Long>> entry:stored_calls.entrySet()) {
            StringBuilder build = new StringBuilder();
            for(Long l:entry.getValue()) {
                if(removeFirst) {
                    removeFirst = false;
                    continue;
                }
                build.append(String.format("%.7f", (l / 1e9))).append(" \\\\ ");
            }
            System.out.println(entry.getKey()+"("+entry.getValue().size()+")\n"+build.toString());
        }
        System.out.println("=== End End EndEnd End End ===");
    }
}


