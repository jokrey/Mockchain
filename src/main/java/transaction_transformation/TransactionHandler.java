package transaction_transformation;

import storage_classes.Transaction;

/**
 * Generic functional interface.
 *
 * Very last element of the chain that turns raw transaction into handleable objects of type T.
 * These objects can then be much more easily used for validation and eventually for storage
 *
 * @param <T> the type of object to be handled
 */
@FunctionalInterface
public interface TransactionHandler<T> {
    void handle(T object, Transaction raw);
}
