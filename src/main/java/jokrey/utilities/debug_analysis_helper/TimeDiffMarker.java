package jokrey.utilities.debug_analysis_helper;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 *
 * @author jokrey
 */
public class TimeDiffMarker {
	public static final Logger LOG = Logger.getLogger("TimeDiffMarker");

    public static final String DEFAULT_ID = "default - !§$%&TTT/()=?*' - default";

	private static final HashMap<String, Long> marks = new HashMap<>();

	public static void println_d() {
        System.out.println(getDiffFor_in_seconds(DEFAULT_ID) + "s");
	}
	public static String getDiffFor_as_string_d() {
		return getDiffFor_as_string(DEFAULT_ID);
	}
	public static void setMark_d() {setMark(DEFAULT_ID);}
	public static void println_setMark() {
        println_d();
        setMark_d();}
	public static void println_d(String s) {
        System.out.println(s+" took: "+getDiffFor_as_string(DEFAULT_ID) + "s");
	}
	public static void println_setMark_d(String s) {
		LOG.info(s+" took: "+getDiffFor_as_string(DEFAULT_ID) + "s");
        setMark(DEFAULT_ID);
	}

	public static void println(int id) {
		System.out.println(getDiffFor_as_string(id+"") + "s");
	}
    public static void setMark(int id) {
	    setMark(id+"");
    }
	public static void println(int id, String s) {
		System.out.println(s + " "+ getDiffFor_as_string(id+"") + "s");
	}
	public static void println_setMark(int id, String s) {
		LOG.info(s + " "+ getDiffFor_as_string(id+"") + "s");
		setMark(id+"");
	}
	public static void println_setMark(int id) {
		LOG.info(getDiffFor_as_string(id+"") + "s");
		setMark(id+"");
	}

	public static void println(String id) {
		LOG.info(id+" took: "+getDiffFor_as_string(id) + "s");
	}
	public static void println_setMark(String id) {
		double dif = getDiffFor_in_seconds(id);
		LOG.info(id+(dif==0?" started":" took: "+String.format("%f",dif) + "s"));
		setMark(id);
	}
	public static void println_clearMark(String id) {
		println(id);
		marks.remove(id);
	}

	public static void clear() {marks.clear();}

	public static void remove(String key) {
		marks.remove(key);
	}

	public static String getDiffFor_as_string(String id) {
        return String.format("%f",(getDiffFor_in_seconds(id)));
    }
    public static double getDiffFor_in_seconds(String id) {
        return getDiffFor_in_nano(id) / 1e9;
    }

    public static void print_all() {
		LOG.info("=== Printing Current Marks ===");
        for(Map.Entry<String, Long> e:marks.entrySet()) {
			LOG.info(e.getKey()+" - "+(System.nanoTime() - e.getValue()));
        }
		LOG.info("=== End End EndEnd End End ===");
    }


	public static void setMark(String id) {
	    marks.put(id, System.nanoTime()); //problem: overhead because nano time checked before search
	}
    public static long getDiffFor_in_nano(String id) {
	    long cur = System.nanoTime(); //ignore time it takes to get last.
        Long last = marks.get(id);
        return last==null ? 0 : (cur - last);
    }
}