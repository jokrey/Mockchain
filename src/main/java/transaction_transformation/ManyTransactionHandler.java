package transaction_transformation;

import storage_classes.Transaction;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

/**
 * Provides the functionality for the block chain to decide what kind of transaction a transaction is.
 * Additionally it allows the transactions to be automatically deserialized and handled by the appropriate handler.
 */
public class ManyTransactionHandler {
    private final HashMap<Byte, SerializedTransactionHandler> transactionHandlers_b = new HashMap<>();
    private final HashMap<Class, SerializedTransactionHandler> transactionHandlers_c = new HashMap<>();
    private final HashMap<Class, Byte> cToB = new HashMap<>();

    public ManyTransactionHandler(SerializedTransactionHandler... handlers) {
        if(handlers.length > 256) throw new IllegalArgumentException("too many handlers");
        byte identifier = Byte.MIN_VALUE;
        for(SerializedTransactionHandler handler: handlers) {
            transactionHandlers_b.put(identifier, handler);
            transactionHandlers_c.put(handler.clazz, handler);
            cToB.put(handler.clazz, identifier);
            identifier++;
        }
    }


    public Transaction makeDistinct(Object o) {
        return new Transaction(serialize(o));
    }
    /**
     * Creates a Transaction from the identifier and the actual serialized object
     * @param identifier identifier, preferably known to the system
     * @param raw_serialized_transaction_data serialized object
     * @return the new transaction
     */
    private byte[] makeDistinct(byte identifier, byte[] raw_serialized_transaction_data) {
        byte[] transaction_data = new byte[raw_serialized_transaction_data.length + 1];
        System.arraycopy(raw_serialized_transaction_data, 0, transaction_data, 1, raw_serialized_transaction_data.length);
        transaction_data[0] = identifier;
        return transaction_data;
    }

    /**
     * Finds and calls the appropriate handler for the given transaction.
     * The Transaction is required to have been created using {@link #makeDistinct(byte, byte[])} for this method to properly work.
     * Additionally the identifier(used in {@link #makeDistinct(byte, byte[])}) has to be known to the system and
     *              the deserializer has to work for the raw_serialized_transaction_data (used in {@link #makeDistinct(byte, byte[])}).
     * @param transaction given transaction
     * @throws UnidentifiableTransactionException if canHandle(transaction) == false
     */
    public void handle(Transaction transaction) {
        handle(transaction, transaction.getContent());
    }
    public void handle(Transaction transaction, byte[] transactionContent) {
        byte identifier = transactionContent[0];
        byte[] raw_serialized_transaction_data = Arrays.copyOfRange(transactionContent, 1, transactionContent.length);
        SerializedTransactionHandler handler = transactionHandlers_b.get(identifier);
        if(handler!=null)
            handler.handleSerialized(raw_serialized_transaction_data, transaction);
        else
            throw new UnidentifiableTransactionException();
    }

    /**
     * Finds and deserializes the given transaction using the appropriate deserializer for the given transaction.
     * The Transaction is required to have been created using {@link #makeDistinct(byte, byte[])} for this method to properly work.
     * Additionally the identifier(used in {@link #makeDistinct(byte, byte[])}) has to be known to the system and
     *              the deserializer has to work for the raw_serialized_transaction_data (used in {@link #makeDistinct(byte, byte[])}).
     * @param transaction given transaction
     * @return the deserialized object.
     * @throws UnidentifiableTransactionException if canHandle(transaction) == false
     */
    public Object deserialize(Transaction transaction) {
        byte[] raw_transaction_data = transaction.getContent();
        byte identifier = raw_transaction_data[0];
        byte[] raw_serialized_transaction_data = Arrays.copyOfRange(raw_transaction_data, 1, raw_transaction_data.length);
        SerializedTransactionHandler handler = transactionHandlers_b.get(identifier);
        if(handler!=null)
            return handler.deserialize(raw_serialized_transaction_data);
        else
            throw new UnidentifiableTransactionException();
    }

    public byte[] serialize(Object o) {
        return makeDistinct(cToB.get(o.getClass()), transactionHandlers_c.get(o.getClass()).serialize(o));
    }

    /**
     * Calls {@link #handle(Transaction)} for each of the transactions
     * @param transactions transactions to handle
     */
    public void handleAll(List<Transaction> transactions) {
        for(Transaction transaction:transactions)
            handle(transaction);
    }
    public void handleAllTxs(List<byte[]> transactions) {
        for(byte[] transaction:transactions)
            handle(null, transaction);
    }

    /**
     * Calls {@link #handle(Transaction)} for each of the transactions
     * @param transactions transactions to handle
     */
    public void handleAll(List<Transaction> transactions, Consumer<Transaction> unidentifiableHandler) {
        for(Transaction transaction:transactions) {
            byte[] raw_transaction_data = transaction.getContent();
            byte identifier = raw_transaction_data[0];
            byte[] raw_serialized_transaction_data = Arrays.copyOfRange(raw_transaction_data, 1, raw_transaction_data.length);
            SerializedTransactionHandler handler = transactionHandlers_b.get(identifier);
            if(handler!=null) {
                try {
                    handler.handleSerialized(raw_serialized_transaction_data, transaction);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    unidentifiableHandler.accept(transaction);
                }
            } else
                unidentifiableHandler.accept(transaction);
        }
    }

    /**
     * @param transaction given transaction
     * @return whether or not a given transaction will be handleable and deserializable by the system.
     */
    public boolean canHandle(Transaction transaction) {
        byte[] raw_transaction_data = transaction.getContent();
        byte identifier = raw_transaction_data[0];
        return transactionHandlers_b.get(identifier) != null;
    }


    public boolean isOfType(Transaction transaction, Class clazz) {
        Byte identifierForClass = cToB.get(clazz);
        if(identifierForClass!=null) {
            byte[] raw_transaction_data = transaction.getContent();
            byte identifier = raw_transaction_data[0];
            return identifierForClass == identifier;
        } else
            throw new UnidentifiableTransactionException();
    }

    /**
     * RuntimeException used to indicate that a provided transactions type is not known to the system
     * Unchecked because it is a developer error
     *    since both serialization and deserialization are within node logic.
     */
    private class UnidentifiableTransactionException extends RuntimeException {}
}
