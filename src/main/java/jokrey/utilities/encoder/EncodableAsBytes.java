package jokrey.utilities.encoder;

/**
 * A class implementing this interface has to also implement a no-arg constructor.
 * It will then be seamlessly encodable with litbe (and use).
 *    Both those classes also implement this interface allowing easy nesting.
 *
 * @author jokrey
 */
public interface EncodableAsBytes {
    /**
     * Returns the encoded byte string.
     * Should only be used to immediately store it as a byte string.
     * If any bit in the resulting string is altered decoding does not have to be guaranteed.
     *
     * @return the encoded string
     */
    byte[] getEncodedBytes();

    /**
     * Set's the fields of the object, based on the encoded_bytes.
     * Should reset the object to the state it held when getEncodedBytes was called.
     *
     * @param encoded_bytes a byte string previously obtained using getEncodedBytes
     * throws RuntimeException Might throw any kind of runtime exception if the encoded_bytes param was not obtained using EncodableAsBytes()
     */
    void readFromEncodedBytes(byte[] encoded_bytes);

    /**
     * Creates a new instance of provided Class c
     *     (using the no-arg constructor - so that one has to exist within Class c)
     * and calls readFromEncodedString on the newly created Object.
     *
     * @param encoded_bytes a byte string previously obtained using getEncodedBytes
     * @param c a Class (that has a no arg constructor and implements EncodableAsBytes)
     * @return the newly created object
     * throws RuntimeException Might throw any kind of runtime exception if the encoded_bytes param was not obtained using EncodableAsBytes()
     */
    static <T extends EncodableAsBytes> T createFromEncodedBytes(byte[] encoded_bytes, Class<T> c) {
        try {
            T inst = c.newInstance();
            inst.readFromEncodedBytes(encoded_bytes);
            return inst;
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalArgumentException("Provided Class c does not appear to have a no-arg constructor. - "+e.getMessage());
        }
    }
}