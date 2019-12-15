package jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.thread_safe;

import jokrey.utilities.encoder.tag_based.SynchronizedTagBasedEncoder;
import jokrey.utilities.encoder.tag_based.TagBasedEncoder;
import jokrey.utilities.encoder.type_transformer.TypeToFromRawTransformer;
import jokrey.utilities.transparent_storage.TransparentStorage;

import java.util.Iterator;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe delegation wrapper for TagBasedEncoder.
 * Only thread safe for the methods it provides and only when those methods are used.
 * After passing the delegation into this encoder that object is assumed to never be used again by the caller.
 * Note: not all methods are overridden by this wrapper. Some still use the default implementation in {@link TagBasedEncoder}.
 *    This entails that some special overrides of those methods by the delegated encoder will not be used.
 *
 * NOTE:: When nesting wrapping delegation, this should always be the outer most wrapper. (DUH)
 *
 * @see SynchronizedTagBasedEncoder
 * @author jokrey
 */
public class SynchronizingTagBasedEncoder<SF> implements SynchronizedTagBasedEncoder<SF> {
    private ReadWriteLock rw_lock = new ReentrantReadWriteLock();
    protected Lock w = rw_lock.writeLock();
//    protected Lock r = w;
    protected Lock r = rw_lock.readLock();
    final TagBasedEncoder<SF> delegation;
    /**
     * Constructor
     * @param delegation the not-actual tbe
     */
    public SynchronizingTagBasedEncoder(TagBasedEncoder<SF> delegation) {
        this.delegation = delegation;
    }


    /**
     * NOT THREAD SAFE
     * {@inheritDoc}
     */
    @Override public TransparentStorage<SF> getRawStorageSystem() {
        return delegation.getRawStorageSystem();
    }


    //delegation

    //write locked::
    //  ((could just write for all write-locked: doWriteLocked((encoder) -> delegation.addEntry(tag, arr))
    //    but in something THIS performance critical one object creation per call is too much...))

    @Override public boolean addEntry(String tag, SF arr) {
        w.lock();
        try {
            return delegation.addEntry(tag, arr);
        } finally { w.unlock(); }
    }
    @Override public SynchronizingTagBasedEncoder<SF> addEntry_nocheck(String tag, SF arr) {
        w.lock();
        try {
            delegation.addEntry_nocheck(tag, arr);
            return this;
        } finally { w.unlock(); }
    }
    @Override public SF deleteEntry(String tag) {
        w.lock();
        try {
            return delegation.deleteEntry(tag);
        } finally { w.unlock(); }
    }
    @Override public boolean deleteEntry_noReturn(String tag) {
        w.lock();
        try {
            return delegation.deleteEntry_noReturn(tag);
        } finally { w.unlock(); }
    }
    @Override public SynchronizingTagBasedEncoder<SF> clear() {
        w.lock();
        try {
            delegation.clear();
            return this;
        } finally { w.unlock(); }
    }
    @Override public void readFromEncodedBytes(byte[] encoded_bytes) {
        w.lock();
        try {
            delegation.readFromEncodedBytes(encoded_bytes);
        } finally { w.unlock(); }
    }
    @Override public void readFromEncodedString(String encoded_string) {
        w.lock();
        try {
            delegation.readFromEncodedString(encoded_string);
        } finally { w.unlock(); }
    }
    @Override public SynchronizingTagBasedEncoder<SF> readFromEncoded(SF encoded_raw) {
        w.lock();
        try {
            delegation.readFromEncoded(encoded_raw);
            return this;
        } finally { w.unlock(); }
    }


    //read-locked::

    @Override public SF getEntry(String tag) {
        r.lock();
        try {
            return delegation.getEntry(tag);
        } finally { r.unlock(); }
    }
    @Override public boolean exists(String tag) {
        r.lock();
        try {
            return delegation.exists(tag);
        } finally { r.unlock(); }
    }
    @Override public long length(String tag) {
        r.lock();
        try {
            return delegation.length(tag);
        } finally { r.unlock(); }
    }
    @Override public String[] getTags() {
        r.lock();
        try {
            return delegation.getTags();
        } finally { r.unlock(); }
    }
    @Override public byte[] getEncodedBytes() {
        r.lock();
        try {
            return delegation.getEncodedBytes();
        } finally { r.unlock(); }
    }
    @Override public String getEncodedString() {
        r.lock();
        try {
            return delegation.getEncodedString();
        } finally { r.unlock(); }
    }
    @Override public SF getEncoded() {
        r.lock();
        try {
            return delegation.getEncoded();
        } finally { r.unlock(); }
    }


    //non locking delegation::
    @Override public TypeToFromRawTransformer<SF> createTypeTransformer() {
        return delegation.getTypeTransformer();
    }
    @Override public int hashCode() {
        return delegation.hashCode();
    }
    @Override public boolean equals(Object o) {
        return o instanceof SynchronizingTagBasedEncoder && delegation.equals(((SynchronizingTagBasedEncoder)o).delegation);
    }

    /**
     * NOT THREAD SAFE
     * {@inheritDoc}
     */
    @Override public Iterator<TaggedEntry<SF>> iterator() {
        return delegation.iterator();
    }
    /**
     * NOT THREAD SAFE
     * {@inheritDoc}
     */
    @Override public Iterable<String> tag_iterator() {
        return delegation.tag_iterator();
    }


    @Override public <R> R doReadLocked(LockedAction<SF, R> action) {
        r.lock();
        try {
            return action.doLocked(this);
        } finally {
            r.unlock();
        }
    }
    @Override public <R> R doWriteLocked(LockedAction<SF, R> action) {
        w.lock();
        try {
            return action.doLocked(this);
        } finally {
            w.unlock();
        }
    }
}