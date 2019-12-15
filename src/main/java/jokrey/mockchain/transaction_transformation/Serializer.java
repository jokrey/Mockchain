package jokrey.mockchain.transaction_transformation;

@FunctionalInterface
public interface Serializer<T> {
    byte[] serialize(T deSerialized);
}