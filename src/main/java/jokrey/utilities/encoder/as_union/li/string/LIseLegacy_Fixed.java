package jokrey.utilities.encoder.as_union.li.string;

import jokrey.utilities.encoder.as_union.li.LIPosition;
import jokrey.utilities.transparent_storage.TransparentStorage;

/**
 * OLD - FIXED VERSION - Display concept
 *
 *OLD LISE FUNCTIONALITY - cooler,
 *     uses nested li indicators. i.e. the first char indicates how many chars the actual indicator has, unless it has more than 9, then the next one does and so on..
 *     sadly it  does not work when trying to encode certain integers - does not work in ALL situations (demonstrated below)
 *   even more sad: once one fixed it by adding a "not-number-character" to the end of li and ignoring that character on decode (which fixed the issues) -
 *                     at that point we don't need the li at all anymore...
 * @author jokrey
 * @deprecated
 */
public class LIseLegacy_Fixed extends LIseLegacy_Pure {
    public LIseLegacy_Fixed() {}
    public LIseLegacy_Fixed(String encoded) {super(encoded);}

    @Override protected String getLengthIndicatorFor(String str) {
        char c = LIse.getPseudoHashedCharAsString(str);
        return super.getLengthIndicatorFor(str + c)+c;
    }
    @Override protected long[] get_next_li_bounds(LIPosition start_pos, TransparentStorage<String> current) {
        long[] sup = super.get_next_li_bounds(start_pos, current);
        if(sup == null) return null;
        return new long[] {sup[0]+1, sup[1]};
    }
}
