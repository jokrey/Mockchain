package transaction_transformation;

import storage_classes.Transaction;

/**
 * Combines functionality fo a {@link Deserializer} and a {@link TransactionHandler}.
 *
 * Amends the functionality by doing deserialization and handling in a single step.
 *
 * @param <T> the type of object to be deserialized to and subsequently handled by the given handler
 *
 * Immutable
 */
public class SerializedTransactionHandler<T> implements TransactionHandler<T>, Deserializer<T>, Serializer<T> {
    private final TransactionHandler<T> handler;
    private final Deserializer<T> deserializer;
    private final Serializer<T> serializer;
    public final Class<T> clazz;

    /**
     * Constructor
     * @param handler handler
     * @param deserializer deserializer
     * @param clazz
     */
    public SerializedTransactionHandler(TransactionHandler<T> handler, Class<T> clazz, Deserializer<T> deserializer, Serializer<T> serializer) {
        this.handler = handler;
        this.deserializer = deserializer;
        this.serializer = serializer;
        this.clazz = clazz;
    }

    public void handleSerialized(byte[] raw_transaction_data, Transaction raw) {
        handle(deserialize(raw_transaction_data), raw);
    }



    @Override public void handle(T deSerialized, Transaction raw) {
        handler.handle(deSerialized, raw);
    }
    @Override public T deserialize(byte[] raw_transaction_data) {
        return deserializer.deserialize(raw_transaction_data);
    }
    @Override public byte[] serialize(T o) {
        return serializer.serialize(o);
    }
}
