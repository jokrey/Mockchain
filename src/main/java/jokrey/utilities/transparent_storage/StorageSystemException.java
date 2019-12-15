package jokrey.utilities.transparent_storage;

/**
 * Thrown by a storage system to propagate a potential error through to the caller.
 *    Typically thrown on IO or corrupt data errors.
 *
 * This exception is a non checked exception.
 *    That is due to the fact that with some storage systems it will never be thrown,
 *      or only be thrown on an error that implies faulty underlying data or illegal arguments (both can be considered dev bugs and should not use performance resources in production).
 *      Therefore the {@link TransparentStorage} methods cannot declare the exception in their interface definition.
 * That said:
 *   With many storage systems it is nonetheless advisable to catch StorageSystemExceptions.
 *   For example in storage systems using IO, then might indicate rethrown IOExceptions.
 *   That is not a very nice way, but the throws clause is part of the method signature in java.
 *
 * @author jokrey
 */
public class StorageSystemException extends RuntimeException {
    public StorageSystemException(String message) {
        super(message);
    }
    public StorageSystemException(Exception ex) {
        super(ex);
    }
}