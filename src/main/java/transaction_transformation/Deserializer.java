package transaction_transformation;

@FunctionalInterface
public interface Deserializer<T> {
    T deserialize(byte[] serialized);
}