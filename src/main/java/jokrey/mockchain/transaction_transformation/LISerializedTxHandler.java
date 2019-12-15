package jokrey.mockchain.transaction_transformation;

import jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.serialization.LIObjectEncoderFull;

/**
 * @author jokrey
 */
public class LISerializedTxHandler<T> extends SerializedTransactionHandler<T> {
    public LISerializedTxHandler(Class<T> clazz, TransactionHandler<T> handler) {
        super(handler, clazz, serialized -> LIObjectEncoderFull.deserialize(serialized, clazz), LIObjectEncoderFull::serialize);
    }
}
