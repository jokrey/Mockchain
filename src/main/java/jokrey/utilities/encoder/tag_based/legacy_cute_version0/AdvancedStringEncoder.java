package jokrey.utilities.encoder.tag_based.legacy_cute_version0;

import jokrey.utilities.encoder.EncodableAsString;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Allows for a simple data to be encoded into a String.
 * Nesting this allows for infinitly complex storage structures.
 *      Nesting is achieved by letting complex classes encode their raw data using this utility class
 *         (good practise is to implement EncodableAsString interface and provide a constructor that takes an encoded String)
 * Accessing the data can be done over tags. If a Tag doesn't exist, the method(getEntry) will return null.
 * When switching around between versions this has to be caught, but if handled right, the remaining data is not lost.
 * The resulting string can then be stored without much effort.
 *
 * NOTE: This is ENCODING, NOT ENCRYPTING
 *    Though the results of this method may at times be hard to read for a human, should the data be sensitive then encryption is still required.
 */
public class AdvancedStringEncoder {
    private StringBuilder work_in_progress;
    public AdvancedStringEncoder() {
        work_in_progress=new StringBuilder();
    }
    public AdvancedStringEncoder(String workingString) {
        this();
        setWorkingString(workingString);
    }
    public void setWorkingString(String workingString) {
        work_in_progress = new StringBuilder(workingString);
    }
     public String getEncodedString() {
        return work_in_progress.toString();
    }

    public String getEntry(String tag) {
        String workingString = work_in_progress.toString();
        int current_i=0;
        do {
            String[] tag_content_pair = li_decode_multiple(workingString, current_i, 2);
            if(tag_content_pair.length!=2)return null;
            if(tag_content_pair[0].equals(tag))
                return tag_content_pair[1];
            current_i = readto_next_entry(current_i);
        } while(current_i!=0);
        return null;
    }
    public String deleteEntry(String tag) {
        String workingString = work_in_progress.toString();
        int current_i=0;
        do {
            String[] tag_content_pair = li_decode_multiple(workingString, current_i, 2);
            if(tag_content_pair.length!=2)return null;
            if(tag_content_pair[0].equals(tag)) {
                int[] startIndex_endIndex_of_TAG = get_startAndEndIndexOf_NextLIString(current_i, workingString);
                int[] startIndex_endIndex_of_CONTENT = get_startAndEndIndexOf_NextLIString(startIndex_endIndex_of_TAG[1], workingString);
                work_in_progress.delete(current_i, startIndex_endIndex_of_CONTENT[1]);
                return tag_content_pair[1];
            }
            current_i = readto_next_entry(current_i);
        } while(current_i!=0);
        return null;
    }
    public void addEntry(String tag, String entry) {
        deleteEntry(tag); //just in case it already exists

        work_in_progress.append(li_encode_single(tag)).append(li_encode_single(entry));
    }
    public void addEntry_nocheck(String tag, String entry) {
//        deleteEntry(tag); //just in case it already exists

        work_in_progress.append(li_encode_single(tag)).append(li_encode_single(entry));
    }
    private int readto_next_entry(int current_i) {
        String workingString = work_in_progress.toString();
        current_i = get_startAndEndIndexOf_NextLIString(current_i, workingString)[1];//skip tag
        current_i = get_startAndEndIndexOf_NextLIString(current_i, workingString)[1];//skip content
        if(current_i>=work_in_progress.length())
            current_i=0;
        return current_i;
    }



    //boolean shorts
    public void addEntry(String tag, boolean b) {
        addEntry(tag, b?"t":"f");
    }
    public boolean getEntry_boolean(String tag) {
        String entry = getEntry(tag);
        return entry!=null && entry.equals("t");
    }
    public boolean deleteEntry_boolean(String tag) {
        String entry = deleteEntry(tag);
        return entry!=null && entry.equals("t");
    }

    //int shorts
    public void addEntry(String tag, int b) {
        addEntry(tag, b+"");
    }
    public int getEntry_int(String tag) {
        String entry = getEntry(tag);
        return Integer.parseInt(entry);
    }
    public int deleteEntry_int(String tag) {
        String entry = deleteEntry(tag);
        return Integer.parseInt(entry);
    }

    //long shorts
    public void addEntry(String tag, long b) {
        addEntry(tag, b+"");
    }
    public long getEntry_long(String tag) {
        String entry = getEntry(tag);
        return Long.parseLong(entry);
    }
    public long deleteEntry_long(String tag) {
        String entry = deleteEntry(tag);
        return Long.parseLong(entry);
    }

    //EncodableAsString
    public void addEntry(String tag, EncodableAsString encodableObject) {
        addEntry(tag, encodableObject.getEncodedString());
    }
    public <T extends EncodableAsString> T getEntry_encodable(String tag, T dummy) {
        String entry = getEntry(tag);
        dummy.readFromEncodedString(entry);
        return dummy;
    }

    public  void addEntry(String tag, String[] arr) {
        for(int i=0;i<arr.length;i++)
            addEntry(tag+"_stridx_"+i, arr[i]);
    }
    public String[] getEntry_strarr(String tag) {
        ArrayList<String> list=new ArrayList<>();
        try {
            int i=0;
            while(true) {
                list.add(getEntry(tag+"_stridx_"+i));
                i++;
            }
        } catch(Exception e){}//thrown by getEntry_int when no more available
        String[] arr=new String[list.size()];
        for(int i=0;i<arr.length;i++)arr[i]=list.get(i);
        return arr;
    }

    public  void addEntry(String tag, int[] arr) {
        for(int i=0;i<arr.length;i++)
            addEntry(tag+"_intidx_"+i, arr[i]);
    }
    public int[] getEntry_intarr(String tag) {
        ArrayList<Integer> list=new ArrayList<>();
        try {
            int i=0;
            while(true) {
                list.add(getEntry_int(tag+"_intidx_"+i));
                i++;
            }
        } catch(Exception e){}//thrown by getEntry_int when no more available
        int[] arr=new int[list.size()];
        for(int i=0;i<arr.length;i++)arr[i]=list.get(i);
        return arr;
    }




    //LI-Encoding
    //Length-Indicator based encoding
    public static String li_encode_multiple(String... strs) {
        String toReturn = "";
        for(String str:strs) {
            toReturn+=li_encode_single(str);
        }
        return toReturn;
    }
    public static String[] li_decode_multiple(String encoded_str) {
        return li_decode_multiple(encoded_str, 0, -1);
    }
    public static String[] li_decode_multiple(String encoded_str, int start_index, int limit) {
        ArrayList<String> toReturn = new ArrayList<>();

        int[] startIndex_endIndex_ofNextLIString = {0, start_index};
        while(startIndex_endIndex_ofNextLIString.length==2) {
            if(limit>-1 && toReturn.size()>=limit)//-1 because after while one more is added added
                break;
            startIndex_endIndex_ofNextLIString = get_startAndEndIndexOf_NextLIString(startIndex_endIndex_ofNextLIString[1], encoded_str);
            if(startIndex_endIndex_ofNextLIString.length==2)
                toReturn.add(encoded_str.substring(startIndex_endIndex_ofNextLIString[0], startIndex_endIndex_ofNextLIString[1]));
        }

        return toReturn.toArray(new String[toReturn.size()]);
    }
    public static String li_encode_single(String str) {
        return getLengthIndicatorFor(str)+str;
    }
    public static String li_decode_single(String encoded_str) {
        int[] startIndex_endIndex_ofNextLIString = get_startAndEndIndexOf_NextLIString(0, encoded_str);
        return startIndex_endIndex_ofNextLIString.length==2?
                encoded_str.substring(startIndex_endIndex_ofNextLIString[0], startIndex_endIndex_ofNextLIString[0]+startIndex_endIndex_ofNextLIString[1])
                :
                null;
    }
    public static String getLengthIndicatorFor(String str) {
        ArrayList<String> lengthInidcators = new ArrayList<>();
        lengthInidcators.add((str.length() + 1)+""); //Attention: The +1 is necessary because we are adding a pseudo random char to the beginning of the splitted char to hinder a bug, if somechooses to only save a medium sized int. (It would be interpreted as a lengthIndicator)
        while(lengthInidcators.get(0).length()!=1)
            lengthInidcators.add(0, lengthInidcators.get(0).length()+"");
        return toString(lengthInidcators, "")+getPseudoRandomHashedCharAsString(str);
    }
    public static int[] get_startAndEndIndexOf_NextLIString(int start_index, String str) {
        int i=start_index;
        if(i+1>str.length())return new int[]{};
        String lengthIndicator = str.substring(i, (i+=1));
        while(true) {
            int lengthIndicator_asInt = getInt(lengthIndicator, -1);
            if(lengthIndicator_asInt==-1 || i+lengthIndicator_asInt>str.length())return new int[]{};
            String eitherDataOrIndicator = str.substring(i, i + lengthIndicator_asInt);
            int ifitwasAnIndicator = getInt(eitherDataOrIndicator,-1);
            if(ifitwasAnIndicator > lengthIndicator_asInt && i+ifitwasAnIndicator <= str.length()) {
                i+=lengthIndicator_asInt;
                lengthIndicator=eitherDataOrIndicator;//assume to be an indicator
            } else {
                if(lengthIndicator_asInt==-1)
                    return new int[] {};
                else {
                    return new int[]{i + 1, i + lengthIndicator_asInt};//i+1 for the pseudo random hash char
                }
            }
        }
    }









    //HELPER
    public static int getInt(String s, int i) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return i;
        }
    }
    public static String toString(List o_c, String splitStr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i != o_c.size();i++)
            sb.append(o_c.get(i).toString()).append(i == o_c.size() - 1 ? "" : splitStr);
        return sb.toString();
    }
    public static String getPseudoRandomHashedCharAsString(String origString) {
        String possibleChars = "abcdefghijklmnopqrstuvwxyz!?()[]{}=#";
        int additionHashSaltThingy = 0;
        for(byte b:origString.getBytes(StandardCharsets.UTF_8))
            additionHashSaltThingy += (b & 0xFF);
        return possibleChars.charAt((origString.length()+additionHashSaltThingy) % possibleChars.length())+"";
    }
    public static boolean isBetween(double toCheck, double minor, double major) {
        return minor<toCheck && toCheck < major;
    }
}
