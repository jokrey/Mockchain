package jokrey.utilities.encoder.tag_based;

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
public interface SynchronizedTagBasedEncoder<SF> extends TagBasedEncoder<SF> {
    default <R> R doReadLocked(LockedAction<SF, R> action) {
        throw new UnsupportedOperationException("By default this functionality is not available.");
    }
    default <R> R doWriteLocked(LockedAction<SF, R> action) {
        throw new UnsupportedOperationException("By default this functionality is not available.");
    }


    /**
     * Anything withing the overridden method {@link #doLocked(TagBasedEncoder)} should not access anything but methods of the provided encoder.
     * Partially so that doLocked completes asap and does not unnecessarily lock the encoder.
     * The parameter is also always the object on which {@link #doReadLocked} or {@link #doWriteLocked} was called,
     *     but the parameter is decidedly not of Type SynchronizedTagBasedEncoder to discourage nested locked calls.
     *
     * @param <SF>
     */
    @FunctionalInterface
    interface LockedAction<SF, R> {
        R doLocked(TagBasedEncoder<SF> encoder);
    }
}