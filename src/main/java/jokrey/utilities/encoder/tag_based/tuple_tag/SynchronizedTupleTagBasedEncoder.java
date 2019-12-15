package jokrey.utilities.encoder.tag_based.tuple_tag;

/**
 * This class does one thing (optionally two)
 * 1. As a marker for thread safety
 * Any TagBasedEncoder implementing this is required to be thread safe and as efficiently as possible synchronize the read and write accesses.
 * It should and mostly will use some sort of read-write-lock to achieve that ( more variable than a synchronized block ).
 *
 * 2. Additional functionality
 * Optionally this class offers some additional synchronization methods.
 *    their default implementation is "unsupported"
 *
 * @author jokrey
 */
public interface SynchronizedTupleTagBasedEncoder<TTBE extends TupleTagBasedEncoder<SF>, SF> extends TupleTagBasedEncoder<SF> {
    /**
     * Any thrown exception by action will be relayed to caller of this method
     * @param super_tag super tag to at least lock
     * @param action action to be executed read locked
     */
    default<R> R doReadLocked(String super_tag, LockedAction<TTBE, R> action) {
        throw new UnsupportedOperationException("By default this functionality is not available.");
    }

    /**
     * Any thrown exception by action will be relayed to caller of this method
     * @param super_tag super tag to at least lock
     * @param action action to be executed write locked
     */
    default<R> R doWriteLocked(String super_tag, LockedAction<TTBE, R> action) {
        throw new UnsupportedOperationException("By default this functionality is not available.");
    }


    /**
     * Anything withing the overridden method {@link #doLocked(String, TupleTagBasedEncoder)} should not access anything
     *    but the provided encoder(or the object this method was called at) at the provided super_tag.
     *    Calling anything else or the encoder with any other super_tag CANNOT be guaranteed to be thread safe.
     */
    @FunctionalInterface
    interface LockedAction<RAW_TTBE extends TupleTagBasedEncoder, R> {
        R doLocked(String super_tag, RAW_TTBE encoder);
    }
}