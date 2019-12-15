package jokrey.utilities.encoder.as_union.li.string;

import jokrey.utilities.encoder.EncodableAsString;
import jokrey.utilities.encoder.as_union.li.LIPosition;
import jokrey.utilities.encoder.as_union.li.LIe;
import jokrey.utilities.transparent_storage.TransparentStorage;
import jokrey.utilities.transparent_storage.string.non_persistent.StringStorageSystem;

import java.util.List;

/**
 * Can encode and decode multiple strings into a single one.
 *
 * NOT Thread safe.
 *    For a thread safe version use a synchronized wrapper(preferably using delegation, not inheritance) - (usage unlikely -> not supplied).
 *
 * @author jokrey
 */
public class LIse extends LIe<String> implements EncodableAsString {
    public LIse() {
        super(new StringStorageSystem());
    }
    public LIse(String encoded) {
        super(new StringStorageSystem(), encoded);
    }
    public LIse(String... initial_values) {
        super(new StringStorageSystem(), initial_values);
    }




    @Override protected String getLengthIndicatorFor(String val) {
        return val.length() + String.valueOf(getPseudoHashedCharAsString(val));
    }

    @Override protected long[] get_next_li_bounds(LIPosition start_pos, TransparentStorage<String> current) {
        String number_characters = "0123456789"; //"-" or "+" are impossible
        for(long offset = start_pos.pointer; offset < current.contentSize(); offset++) {
            if(!number_characters.contains(String.valueOf(current.sub(offset, offset+1)))) {
                String li_subSF = current.sub(start_pos.pointer, offset);
                int li = Integer.parseInt(li_subSF); //cannot fail, unless SF is invalid
                offset++; //offset +1, because in getLengthIndicatorFor we add a pseudo hash char
                return new long[] {offset, offset + li};
            }
        }
        return null;
    }


    //standard::

    @Override public String toString() {
        return "[LIse: \""+getEncodedString()+"\"]";
    }
    @Override public String getEncodedString() {
        return getEncoded();
    }
    @Override public void readFromEncodedString(String encoded_string) {
        readFromEncoded(encoded_string);
    }


    //HELPER
    static char getPseudoHashedCharAsString(String origString) {
        String possibleChars = "abcdefghijklmnopqrstuvwxyz!?()[]{}=#";
        //the following is too slow and has subsequently been removed for being not a good idea in the first place
//        int additionHashSaltThingy = 0;
//        for(byte b:origString.getBytes(StandardCharsets.UTF_8))
//            additionHashSaltThingy += (b & 0xFF);
        int l = origString.length();
        int hash = l>0?l+origString.charAt(0) : l^2;
        return possibleChars.charAt(hash % possibleChars.length());
    }
    protected static String toString(List o_c, String splitStr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i != o_c.size();i++)
            sb.append(o_c.get(i).toString()).append(i == o_c.size() - 1 ? "" : splitStr);
        return sb.toString();
    }
}