package jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.bytes.tuple;

import jokrey.utilities.encoder.as_union.li.string.LIse;
import jokrey.utilities.encoder.tag_based.TagBasedEncoderBytes;
import jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.bytes.LITagBytesEncoder;
import jokrey.utilities.encoder.tag_based.tuple_tag.TupleTagBasedEncoder;
import jokrey.utilities.encoder.tag_based.tuple_tag.TupleTagBasedEncoderBytes;
import jokrey.utilities.encoder.type_transformer.TypeToFromRawTransformer;
import jokrey.utilities.simple.data_structure.ExtendedIterator;
import jokrey.utilities.simple.data_structure.pairs.Pair;
import jokrey.utilities.transparent_storage.StorageSystemException;
import jokrey.utilities.transparent_storage.bytes.TransparentBytesStorage;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Implementation of a {@link TupleTagBasedEncoderBytes} that employs  {@link LIse} and a single (generic) TagBasedEncoderBytes to store tuple tags literally as tuples in a single {@link TagBasedEncoderBytes}
 *
 * Generally slower than a multi encoder, due to greater n for search, delete and add. Additional does not allow as fine grained locking.
 *
 * @author jokrey
 */
public class TupleTagSingleLITagEncoderBytes implements TupleTagBasedEncoderBytes {
    protected TagBasedEncoderBytes delegation;
    public TupleTagSingleLITagEncoderBytes(TagBasedEncoderBytes delegation) {
        this.delegation = delegation;
    }
    public TupleTagSingleLITagEncoderBytes() {
        this(new LITagBytesEncoder());
    }
    public TupleTagSingleLITagEncoderBytes(TransparentBytesStorage storage) {
        this(new LITagBytesEncoder(storage));
    }

    protected String getSingleTag(String super_tag, String tag) {
        return new LIse(super_tag, tag).getEncodedString();//too slow?
    }
    private boolean isOfSuperTag(String super_tag, String raw_tag) {
        return Objects.equals(super_tag, new LIse(raw_tag).decodeFirst());
    }
    private String subTag(String raw_tag) {
        ExtendedIterator<String> lise = new LIse(raw_tag).iterator();
        lise.skip();
        return lise.next();
    }

    @Override public Pair<Long, InputStream> getEntry_asLIStream(String super_tag, String tag) throws StorageSystemException {
        return delegation.getEntry_asLIStream(getSingleTag(super_tag, tag));
    }
    @Override public TupleTagBasedEncoderBytes addEntry_nocheck(String super_tag, String tag, InputStream content, long content_length) throws StorageSystemException {
        delegation.addEntry_nocheck(getSingleTag(super_tag, tag), content, content_length);
        return this;
    }
    @Override public boolean exists(String super_tag, String tag) {
        return delegation.exists(getSingleTag(super_tag, tag));
    }
    @Override public long length(String super_tag, String tag) {
        return delegation.length(getSingleTag(super_tag, tag));
    }
    @Override public TupleTagBasedEncoderBytes clear() {
        delegation.clear();
        return this;
    }
    @Override public String[] getTags(String super_tag) {
        String[] all_tags = delegation.getTags();
        ArrayList<String> sub_tags = new ArrayList<>();
        for(String raw_tag:all_tags)
            if(isOfSuperTag(super_tag, raw_tag))
                sub_tags.add(subTag(raw_tag));
        return sub_tags.toArray(new String[0]);
    }
    @Override public TupleTagBasedEncoder<byte[]> clear(String super_tag) {
        for(String subTag:getTags(super_tag))
            deleteEntry_noReturn(super_tag, subTag);
        return this;
    }
    @Override public boolean deleteEntry_noReturn(String super_tag, String tag) {
        return delegation.deleteEntry_noReturn(getSingleTag(super_tag, tag));
    }
    @Override public TupleTagBasedEncoder<byte[]> addEntry_nocheck(String super_tag, String tag, byte[] entry) {
        delegation.addEntry_nocheck(getSingleTag(super_tag, tag), entry);
        return this;
    }
    @Override public byte[] getEntry(String super_tag, String tag) {
        return delegation.getEntry(getSingleTag(super_tag, tag));
    }
    @Override public byte[] deleteEntry(String super_tag, String tag) {
        return delegation.deleteEntry(getSingleTag(super_tag, tag));
    }
    @Override public TypeToFromRawTransformer<byte[]> createTypeTransformer() {
        return delegation.getTypeTransformer();
    }
//    @Override public Iterator<TagBasedEncoder.TaggedEntry<byte[]>> iterator(String super_tag) {
//        return new Iterator<TagBasedEncoder.TaggedEntry<byte[]>>() {
//            StringPair last = null;
//            @Override public boolean hasNext() {
//                return false;
//            }
//            @Override public TagBasedEncoder.TaggedEntry<byte[]> next() {
//                return null;
//            }
//            @Override public void remove() {
//                if(last == null)
//                    throw new IllegalStateException("next not called");
//                deleteEntry_noReturn(last.l, last.r);
//            }
//        };
//    }
}
