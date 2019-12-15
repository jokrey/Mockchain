package jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator;

import jokrey.utilities.encoder.as_union.lb.bytes.BlockPosition;
import jokrey.utilities.encoder.as_union.lb.bytes.LBLIbae;
import jokrey.utilities.encoder.tag_based.TagBasedEncoder;
import jokrey.utilities.encoder.tag_based.TagBasedEncoderBytes;
import jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.type.transformer.LITypeToBytesTransformer;
import jokrey.utilities.encoder.type_transformer.TypeToFromRawTransformer;
import jokrey.utilities.simple.data_structure.ExtendedIterator;
import jokrey.utilities.simple.data_structure.pairs.Pair;
import jokrey.utilities.transparent_storage.StorageSystemException;
import jokrey.utilities.transparent_storage.bytes.TransparentBytesStorage;
import jokrey.utilities.transparent_storage.bytes.non_persistent.ByteArrayStorage;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;

public class LBTagEncoder implements TagBasedEncoderBytes {
    private final LBLIbae lb;
    public LBTagEncoder() {
        this(new ByteArrayStorage());
    }
    public LBTagEncoder(byte[] content) {
        this(new ByteArrayStorage(content));
    }
    public LBTagEncoder(TransparentBytesStorage storage) {
        lb = new LBLIbae(storage);
    }


    protected BlockPosition[] search(String tag) {
        if(tag == null) throw new NullPointerException();
        BlockPosition local_position = lb.reset();

        String dec_tag;
        do {
            int old_position = local_position.pointer;
            dec_tag = getTag(lb.decode(local_position));
            if (Objects.equals(dec_tag, tag))
                return new BlockPosition[] {new BlockPosition(old_position), local_position};
            else
                lb.skipEntry(local_position);
        } while (dec_tag != null);
        return null;
    }

    @Override public boolean exists(String tag) {
        return search(tag) != null;
    }

    @Override public long length(String tag) {
        BlockPosition[] search = search(tag);
        if(search == null) return -1;
        else return lb.skipEntry(search[1]);
    }

    @Override public String[] getTags() {
        ArrayList<String> list = new ArrayList<>();
        BlockPosition local_position = lb.reset();

        String dec_tag;
        do {
            dec_tag = getTag(lb.decode(local_position));
            lb.skipEntry(local_position);
            if(dec_tag==null) break;
            list.add(dec_tag);
        } while(true);

        return list.toArray(new String[0]);
    }

    @Override public TagBasedEncoder<byte[]> clear() {
        lb.clear();
        return this;
    }

    @Override public boolean deleteEntry_noReturn(String tag) {
        BlockPosition[] search = search(tag);
        if(search==null)return false;
        lb.delete(search[0]);
        lb.delete(search[1]);
        return true;
    }

    @Override public TagBasedEncoder<byte[]> addEntry_nocheck(String tag, byte[] entry) {
        lb.encode(getTypeTransformer().transform(tag), entry);
        return this;
    }

    @Override public byte[] getEntry(String tag) {
        BlockPosition[] search = search(tag);
        if(search==null)return null;
        lb.debugPrintAllBlocks();
        System.out.println("search = " + Arrays.toString(search));
        return lb.decode(search[1]);
    }

    @Override public byte[] deleteEntry(String tag) {
        BlockPosition[] search = search(tag);
        if(search==null)return null;
        lb.delete(search[0]);
        return lb.deleteEntry(search[1]);
    }

    @Override public TypeToFromRawTransformer<byte[]> createTypeTransformer() {
        return new LITypeToBytesTransformer();
    }

    @Override public TransparentBytesStorage getRawStorageSystem() {
        return lb.getRawStorage();
    }

    @Override public byte[] getEncodedBytes() {
        return lb.getEncoded();
    }

    @Override public void readFromEncodedBytes(byte[] encoded_bytes) {
        lb.readFromEncoded(encoded_bytes);
    }


    @Override public Pair<Long, InputStream> getEntry_asLIStream(String tag) throws StorageSystemException {
        throw new UnsupportedOperationException("actually hard, would have to be a fragmented stream");
    }

    @Override public TagBasedEncoderBytes addEntry_nocheck(String tag, InputStream content, long content_length) throws StorageSystemException {
        throw new UnsupportedOperationException();
    }


    @Override public Iterator<TaggedEntry<byte[]>> iterator() {
        ExtendedIterator<byte[]> lie_iterator = lb.iterator();
        return new Iterator<TaggedEntry<byte[]>>() {
            @Override public boolean hasNext() {
                return lie_iterator.hasNext(); //yes, this does work. Think about it! && Nooo, now you think it actually doesn't work! && Think again! && There you go!!! && Hooray
            }
            @Override public TaggedEntry<byte[]> next() {
                return new TaggedEntry<>(getTag(lie_iterator.next()), lie_iterator.next());
            }
            @Override public void remove() {
                lie_iterator.remove();
                lie_iterator.skip();
                lie_iterator.remove();
            }
        };
    }

    //helper
    protected String getTag(byte[] raw) {
        return raw==null? null: getTypeTransformer().detransform_string(raw);
    }
}
