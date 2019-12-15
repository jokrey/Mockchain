package jokrey.utilities.encoder.type_transformer;

/**
 * Thrown by a transformer if the user attempts to Transform an unrecognised type.
 * Unchecked exception as this can be seen as a non recoverable dev problem.
 */
public class NotTransformableException extends RuntimeException {
    public NotTransformableException(Object entry) {
        this(entry, entry==null?null:entry.getClass());
    }
    public NotTransformableException(Object entry, Class<?> c) {
        super("entry("+entry+")"+" - unrecognised object type: "+c);
    }
}
