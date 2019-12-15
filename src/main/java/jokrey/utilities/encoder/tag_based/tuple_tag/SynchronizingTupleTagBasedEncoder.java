package jokrey.utilities.encoder.tag_based.tuple_tag;

import jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.thread_safe.SynchronizingTagBasedEncoder;
import jokrey.utilities.encoder.type_transformer.TypeToFromRawTransformer;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe, wrapper for a generic TupleTagBasedEncoder.
 *    However: all additional functions the generic subclass of TupleTagBasedEncoder provides required additional synchronization.
 *
 * NOTE:: When nesting wrapping delegation, this should always be the outer most wrapper. (DUH)
 *    otherwise the most outer encoder does something additionally that would have also required synchronization.
 *
 * @author jokrey
 */
public class SynchronizingTupleTagBasedEncoder<TTBE extends TupleTagBasedEncoder<SF>, SF> implements SynchronizedTupleTagBasedEncoder<TTBE, SF> {
    private ReadWriteLock rw_lock = new ReentrantReadWriteLock();
    protected Lock w = rw_lock.writeLock();
    protected Lock r = rw_lock.readLock();

    protected final TTBE delegation;
    /**
     * Constructor
     * @param delegation the not-actual tbe
     */
    public SynchronizingTupleTagBasedEncoder(TTBE delegation) {
        this.delegation = delegation;
    }

    @Override public SynchronizingTagBasedEncoder<SF> getSubEncoder(String super_tag) {
        return new SynchronizingTagBasedEncoder<>(delegation.getSubEncoder(super_tag));
    }

    //write locked::
    @Override public boolean addEntry(String super_tag, String tag, SF arr) {
        w.lock();
        try {
            return delegation.addEntry(super_tag, tag, arr);
        } finally { w.unlock(); }
    }
    @Override public SynchronizingTupleTagBasedEncoder<TTBE, SF> addEntry_nocheck(String super_tag, String tag, SF arr) {
        w.lock();
        try {
            delegation.addEntry_nocheck(super_tag, tag, arr);
            return this;
        } finally { w.unlock(); }
    }
    @Override public SF deleteEntry(String super_tag, String tag) {
        w.lock();
        try {
            return delegation.deleteEntry(super_tag, tag);
        } finally { w.unlock(); }
    }
    @Override public boolean deleteEntry_noReturn(String super_tag, String tag) {
        w.lock();
        try {
            return delegation.deleteEntry_noReturn(super_tag, tag);
        } finally { w.unlock(); }
    }
    @Override public SynchronizingTupleTagBasedEncoder<TTBE, SF> clear(String super_tag) {
        w.lock();
        try {
            delegation.clear(super_tag);
            return this;
        } finally { w.unlock(); }
    }
    @Override public SynchronizingTupleTagBasedEncoder<TTBE, SF> clear() {
        w.lock();
        try {
            delegation.clear();
            return this;
        } finally { w.unlock(); }
    }

    //read locked::
    @Override public SF getEntry(String super_tag, String tag) {
        r.lock();
        try {
            return delegation.getEntry(super_tag, tag);
        } finally { r.unlock(); }
    }
    @Override public boolean exists(String super_tag, String tag) {
        r.lock();
        try {
            return delegation.exists(super_tag, tag);
        } finally { r.unlock(); }
    }
    @Override public long length(String super_tag, String tag) {
        r.lock();
        try {
            return delegation.length(super_tag, tag);
        } finally { r.unlock(); }
    }
    @Override public String[] getTags(String super_tag) {
        r.lock();
        try {
            return delegation.getTags(super_tag);
        } finally { r.unlock(); }
    }



    //DOESN'T REQUIRE SYNCHRONIZATION::
    @Override public TypeToFromRawTransformer<SF> createTypeTransformer() {
        return delegation.getTypeTransformer();
    }
    @Override public int hashCode() {
        return delegation.hashCode();
    }
    @Override public boolean equals(Object o) {
        return o instanceof SynchronizingTupleTagBasedEncoder && delegation.equals(((SynchronizingTupleTagBasedEncoder)o).delegation);
    }
    @Override public Iterable<String> tag_iterator(String super_tag) {
        return delegation.tag_iterator(super_tag);
    }


    @Override public<R> R doReadLocked(String super_tag, LockedAction<TTBE, R> action) {
        r.lock();
        try {
            return action.doLocked(super_tag, delegation); //calling the raw delegation here is perfectly fine, because locking is obviously done outside of this (at least for write)..
        } finally {
            r.unlock();
        }
    }
    @Override public<R> R doWriteLocked(String super_tag, LockedAction<TTBE, R> action) {
        w.lock();
        try {
            return action.doLocked(super_tag, delegation); //calling the raw delegation here is perfectly fine, because locking is obviously done outside of this..
        } finally {
            w.unlock();
        }
    }

    //DOESN'T REQUIRE DELEGATION::
//    @Override public SF deleteEntry(String super_tag, String tag, SF default_value) {
//        return delegation.deleteEntry(super_tag, tag, default_value);
//    }
//    @Override public <T> T deleteEntryT(String super_tag, String tag, T default_value) {
//        return delegation.deleteEntryT(super_tag, tag, default_value);
//    }
//    @Override public SF getEntry(String super_tag, String tag, SF default_value) {
//        return delegation.getEntry(super_tag, tag, default_value);
//    }
//    @Override public <T> T getEntryT(String super_tag, String tag, T default_value) {
//        return delegation.getEntryT(super_tag, tag, default_value);
//    }
//    @Override public <T> T deleteEntryT(String super_tag, String tag, Class<T> c) {
//        return delegation.deleteEntryT(super_tag, tag, c);
//    }
//    @Override public <T> T getEntryT(String super_tag, String tag, Class<T> c) {
//        return delegation.getEntryT(super_tag, tag, c);
//    }
//    @Override public <T> boolean addEntryT(String super_tag, String tag, T entry) {
//        return delegation.addEntryT(super_tag, tag, entry);
//    }
//    @Override public <T> SynchronizingTupleTagBasedEncoder<SF> addEntryT_nocheck(String super_tag, String tag, T entry) {
//        delegation.addEntryT_nocheck(super_tag, tag, entry);
//        return this;
//    }
}