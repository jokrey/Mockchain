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
public interface SynchronizedTupleTagBasedEncoderBytes<TTBE extends TupleTagBasedEncoderBytes>
        extends SynchronizedTupleTagBasedEncoder<TTBE, byte[]>, TupleTagBasedEncoderBytes
{
}