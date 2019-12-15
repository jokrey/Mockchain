package jokrey.utilities.encoder;

/**
 * A class implementing this interface has to also implement a no-arg constructor.
 * It will then be seamlessly encodable with use (and litbe).
 *    Both those classes also implement this interface allowing easy nesting.
 *
 * @author jokrey
 */
public interface EncodableAsString {
    /**
     * Returns the encoded string.
     * Should only be used to immediately store it as a string.
     * If any character in the resulting string is altered decoding does not have to be guaranteed.
     *
     * @return the encoded string
     */
    String getEncodedString();

    /**
     * Set's the fields of the object, based on the encoded_string.
     * Should reset the object to the state it hold when getEncodedString was called.
     *
     * @param encoded_string a string previously obtained using getEncodedString
     * throws RuntimeException Might throw any kind of runtime exception if the encoded_string param was not obtained using getEncodedString()
     */
    void readFromEncodedString(String encoded_string);

    /**
     * Creates a new instance of provided Class c
     *     (using the no-arg constructor - so that one has to exist within Class c)
     * and calls readFromEncodedString on the newly created Object.
     *
     * @param encoded_string a string previously obtained using getEncodedString
     * @param c a Class (that has a no arg constructor and implements EncodableAsString)
     * @return the newly created object
     * throws RuntimeException Might throw any kind of runtime exception if the encoded_string param was not obtained using getEncodedString()
     */
    static <T extends EncodableAsString> T createFromEncodedString(String encoded_string, Class<T> c) {
        try {
            T inst = c.newInstance();
            inst.readFromEncodedString(encoded_string);
            return inst;
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalArgumentException("Provided Class c does not appear to have a no-arg constructor. - "+e.getMessage());
        }
    }


    //The following is not what those methods have to actually look like.
    //but semantically they very likely should.
//    default boolean equals(Object o) {
//        return getEncodedString().equals(((EncodableAsString) o).getEncodedString());
//    }
//    default int hashCode() {
//        return getEncodedString().hashCode();
//    }
}
