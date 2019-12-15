package jokrey.utilities.encoder.as_union.li.string;

import jokrey.utilities.encoder.as_union.li.LIPosition;
import jokrey.utilities.transparent_storage.StorageSystemException;
import jokrey.utilities.transparent_storage.TransparentStorage;

import java.util.ArrayList;

/**
 * PURE - NOT WORKING VERSION - Display concept
 *
 * OLD LISE FUNCTIONALITY - cooler,
 *    uses nested li indicators. i.e. the first char indicates how many chars the actual indicator has, unless it has more than 9, then the next one does and so on..
 *      sadly it  does not work when trying to encode certain integers - does not work in ALL situations (demonstrated below)
 *      even more sad: once one fixed it by adding a "not-number-character" to the end of li and ignoring that character on decode (which fixed the issues) -
 *                   at that point we don't need the li at all anymore...
 *
 * @author jokrey
 * @deprecated
 */
public class LIseLegacy_Pure extends LIse {
    public LIseLegacy_Pure() {}
    public LIseLegacy_Pure(String encoded) {super(encoded);}







    ///PURE VERSION (does not always work)
    @Override protected String getLengthIndicatorFor(String str) {
        ArrayList<String> lengthIndicators = new ArrayList<>();
        lengthIndicators.add(String.valueOf(str.length()));
        while (lengthIndicators.get(0).length() != 1)
            lengthIndicators.add(0, String.valueOf(lengthIndicators.get(0).length()));
        return LIse.toString(lengthIndicators, "");
    }
    @Override protected long[] get_next_li_bounds(LIPosition start_pos, TransparentStorage<String> current) {
        long i=start_pos.pointer;
        if(i+1>current.contentSize())return null;

        String indicatedSubstring = current.sub(i, i+=1); //we parse the initial first char. It is ALWAYS a length indicator.
        int indicatedSubstring_parsed = parseInt(indicatedSubstring, -1);
        int lastIndicatedSubstring_length; //last we parsed nothing, so length was 0

        if(indicatedSubstring_parsed==-1) //First char has to be a length indicator.
            throw new StorageSystemException("The string appears to have been illegally altered");

        int lastIndicatedSubstring_parse; //last we parsed nothing, so 0
        do {
            lastIndicatedSubstring_length = indicatedSubstring.length();
            lastIndicatedSubstring_parse = indicatedSubstring_parsed;

            indicatedSubstring = current.sub(i, (i+indicatedSubstring_parsed));
            i+=lastIndicatedSubstring_parse;
            indicatedSubstring_parsed = parseInt(indicatedSubstring, -1);

        } while(indicatedSubstring_parsed!=-1 && // when the current substring cannot be parsed to a number, then it has to be the desired content
                i + indicatedSubstring_parsed <= current.contentSize() && // if the next substring call would throw an exception,
                //     then we know our current number is too high to still be an indicator and is therefore the content
                indicatedSubstring.length() > lastIndicatedSubstring_length); //any li has to gain in length, which implies a gain in parsed number by a factor of min 10.

        return new long[]{i-lastIndicatedSubstring_parse, i};
    }




    /**
     * guaranteed to return a positive number
     *
     * COPY FROM: {@link Integer#parseInt(String, int)}
     *            But uses return, instead of throw as flow control - which is better, because less weird.
     *
     * @param s string assumed to be an integer of radix 10
     * @param fallback integer to be returned if provided string s is not an integer
     * @return s as int or fallback
     */
    protected static int parseInt( final String s, int fallback ) {
        if (s == null || s.isEmpty())
            return fallback;

        int sign = -1;
        final int len = s.length();
        final char ch = s.charAt(0);
        int num  = -(ch - '0');
        if (num > 0 || num < -9)  //malformed
            return fallback;

        final int max = -Integer.MAX_VALUE;
        final int multmax = max / 10;
        int i = 1;
        while (i < len) {
            int d = s.charAt(i++) - '0';
            if (d < 0 || d > 9)  //malformed
                return fallback;
            if (num < multmax)  //overflow
                return fallback;
            num *= 10;
            if (num < (max+d))  //overflow
                return fallback;
            num -= d;
        }

        return sign * num;
    }

}
