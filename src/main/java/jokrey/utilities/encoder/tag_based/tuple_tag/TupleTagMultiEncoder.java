package jokrey.utilities.encoder.tag_based.tuple_tag;

import jokrey.utilities.encoder.tag_based.TagBasedEncoder;
import jokrey.utilities.simple.data_structure.pairs.Pair;
import jokrey.utilities.transparent_storage.StorageSystemException;

import java.util.Map;

/**
 * A tuple tag encoder that employs multiple {@link TagBasedEncoder} for storing tags.
 * It does this by assigning a different TagBasedEncoder to each super_tag.
 *
 * Implementations may remove encoders for a time to save memory or allow additional thread safety guarantees.
 *
 * Not thread safe.
 * Note: Using the {@link SynchronizingTupleTagBasedEncoder} to obtain a thread safe version of this encoder works, but is discouraged.
 *       This is due to the fact that encoders may allow more fine grained locking. (i.e. locking a single super_tag instead of the entire tuple tag encoder).
 *
 * @author jokrey
 */
public abstract class TupleTagMultiEncoder<SF> implements TupleTagBasedEncoder<SF> {
    protected final Map<String, Pair<TagBasedEncoder<SF>, Long>> encoders = createMap();
    public TagBasedEncoder<SF> getSubEncoder(String super_tag) {
        return encoders.computeIfAbsent(super_tag, s -> { //atomic if createMap returns concurrent map
            TagBasedEncoder<SF> new_encoder = getNewEncoderFor(super_tag);
            if (new_encoder == null)
                throw new StorageSystemException("Cannot generate an encoder for super_tag: \"" + super_tag + "\"");  //rethrown to caller
            return new Pair<>(new_encoder, System.nanoTime());
        }).l;
    }

    public abstract TagBasedEncoder<SF> getNewEncoderFor(String super_tag);
    public abstract Map<String, Pair<TagBasedEncoder<SF>, Long>> createMap();


    @Override public boolean exists(String super_tag, String tag) {
        TagBasedEncoder<SF> sub = getSubEncoder(super_tag);
        return sub.exists(tag);
    }
    @Override public long length(String super_tag, String tag) {
        TagBasedEncoder<SF> sub = getSubEncoder(super_tag);
        return sub.length(tag);
    }
    @Override public String[] getTags(String super_tag) {
        TagBasedEncoder<SF> sub = getSubEncoder(super_tag);
        return sub.getTags();
    }
    @Override public TupleTagMultiEncoder<SF> clear(String super_tag) {
        TagBasedEncoder<SF> sub = getSubEncoder(super_tag);
        sub.clear();
        return this;
    }
    @Override public boolean deleteEntry_noReturn(String super_tag, String tag) {
        TagBasedEncoder<SF> sub = getSubEncoder(super_tag);
        return sub.deleteEntry_noReturn(tag);
    }
    @Override public TupleTagMultiEncoder<SF> addEntry_nocheck(String super_tag, String tag, SF entry) {
        TagBasedEncoder<SF> sub = getSubEncoder(super_tag);
        sub.addEntry_nocheck(tag, entry);
        return this;
    }
    @Override public SF getEntry(String super_tag, String tag) {
        TagBasedEncoder<SF> sub = getSubEncoder(super_tag);
        return sub.getEntry(tag);
    }
    @Override public SF deleteEntry(String super_tag, String tag) {
        TagBasedEncoder<SF> sub = getSubEncoder(super_tag);
        return sub.deleteEntry(tag);
    }
}
