package jokrey.utilities.encoder;

/**
 * A class implementing this interface has to also implement a no-arg constructor.
 * It will then be seamlessly encodable with litbe (and use).
 *    Both those classes also implement this interface allowing easy nesting.
 *
 * @author jokrey
 */
public interface Encodable<SF> {
    /**
     * Returns the object encoded in storage format.
     * Should only be used to immediately store it.
     * If anything in the resulting content is altered decoding does not have to be guaranteed.
     *
     * @return the object encoded in SF
     */
    SF getEncoded();

    /**
     * Set's the fields of the object, based on the encoded.
     * Should reset the object to the state it held when getEncoded was called.
     *
     * @param encoded a 'something' previously obtained using getEncoded
     * throws RuntimeException Might throw any kind of runtime exception if the encoded param was not obtained using getEncoded()
     */
    Encodable<SF> readFromEncoded(SF encoded);

    /**
     * Creates a new instance of provided Class c
     *     (using the no-arg constructor - so one has to exist within Class c)
     * and calls readFromEncoded on the newly created Object.
     *
     * @param encoded a 'something' previously obtained using getEncoded
     * @param c a Class (that has a no arg constructor and implements Encodable)
     * @return the newly created object
     * throws RuntimeException Might throw any kind of runtime exception if the encoded_bytes param was not obtained using getEncoded()
     */
    static <T extends Encodable<SF>, SF> T createFromEncoded(SF encoded, Class<T> c) {
        try {
            T inst = c.newInstance();
            inst.readFromEncoded(encoded);
            return inst;
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalArgumentException("Provided Class c does not appear to have a no-arg constructor. - "+e.getMessage());
        }
    }
}