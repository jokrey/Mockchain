package jokrey.utilities.encoder.tag_based.tuple_tag;

import jokrey.utilities.encoder.tag_based.TagBasedEncoder;
import jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.remote.authenticated.AuthenticatedRemoteEncoderServer;
import jokrey.utilities.encoder.type_transformer.TypeToFromRawTransformer;

/**
 * Does literally NOTHING more than relaying all operations to a completely generic tuple tag encoder.
 * This may seem like it has NO uses, but please check {@link AuthenticatedRemoteEncoderServer} for a valid use.
 *     The reason is to employ more fine grained locking.
 *
 * @author jokrey
 */
public class DelegatingTupleTagBasedEncoder<TTBE extends TupleTagBasedEncoder<SF>, SF> implements TupleTagBasedEncoder<SF> {
    protected final TTBE delegation;
    /**
     * Constructor
     * @param delegation the not-actual tbe
     */
    public DelegatingTupleTagBasedEncoder(TTBE delegation) {
        this.delegation = delegation;
    }

    //just thread safe wrappers
    //because delete and add_nocheck have to be done in direct succession(no other client can swoop in)



    //delegation
    @Override public boolean addEntry(String super_tag, String tag, SF arr) {
        return delegation.addEntry(super_tag, tag, arr);
    }
    @Override public DelegatingTupleTagBasedEncoder<TTBE, SF> addEntry_nocheck(String super_tag, String tag, SF arr) {
        delegation.addEntry_nocheck(super_tag, tag, arr);
        return this;
    }
    @Override public SF getEntry(String super_tag, String tag) {
        return delegation.getEntry(super_tag, tag);
    }
    @Override public SF deleteEntry(String super_tag, String tag) {
        return delegation.deleteEntry(super_tag, tag);
    }
    @Override public boolean deleteEntry_noReturn(String super_tag, String tag) {
        return delegation.deleteEntry_noReturn(super_tag, tag);
    }
    @Override public DelegatingTupleTagBasedEncoder<TTBE, SF> clear(String super_tag) {
        delegation.clear(super_tag);
        return this;
    }
    @Override public DelegatingTupleTagBasedEncoder<TTBE, SF> clear() {
        delegation.clear();
        return this;
    }
    @Override public TagBasedEncoder<SF> getSubEncoder(String super_tag) {
        return delegation.getSubEncoder(super_tag);
    }
    @Override public boolean exists(String super_tag, String tag) {
        return delegation.exists(super_tag, tag);
    }
    @Override public long length(String super_tag, String tag) {
        return delegation.length(super_tag, tag);
    }
    @Override public String[] getTags(String super_tag) {
        return delegation.getTags(super_tag);
    }

    @Override public <T> T deleteEntryT(String super_tag, String tag, Class<T> c) {
        return delegation.deleteEntryT(super_tag, tag, c);
    }
    @Override public <T> T getEntryT(String super_tag, String tag, Class<T> c) {
        return delegation.getEntryT(super_tag, tag, c);
    }
    @Override public <T> boolean addEntryT(String super_tag, String tag, T entry) {
        return delegation.addEntryT(super_tag, tag, entry);
    }
    @Override public <T> DelegatingTupleTagBasedEncoder<TTBE, SF> addEntryT_nocheck(String super_tag, String tag, T entry) {
        delegation.addEntryT_nocheck(super_tag, tag, entry);
        return this;
    }
    @Override public Iterable<String> tag_iterator(String super_tag) {
        return delegation.tag_iterator(super_tag);
    }
    @Override public SF deleteEntry(String super_tag, String tag, SF default_value) {
        return delegation.deleteEntry(super_tag, tag, default_value);
    }
    @Override public <T> T deleteEntryT(String super_tag, String tag, T default_value) {
        return delegation.deleteEntryT(super_tag, tag, default_value);
    }
    @Override public SF getEntry(String super_tag, String tag, SF default_value) {
        return delegation.getEntry(super_tag, tag, default_value);
    }
    @Override public <T> T getEntryT(String super_tag, String tag, T default_value) {
        return delegation.getEntryT(super_tag, tag, default_value);
    }
    @Override public TypeToFromRawTransformer<SF> createTypeTransformer() {
        return delegation.getTypeTransformer();
    }

    @Override public int hashCode() {
        return delegation.hashCode();
    }
    @Override public boolean equals(Object o) {
        return o instanceof DelegatingTupleTagBasedEncoder && delegation.equals(((DelegatingTupleTagBasedEncoder)o).delegation);
    }
}