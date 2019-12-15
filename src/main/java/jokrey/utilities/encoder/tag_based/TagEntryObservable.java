package jokrey.utilities.encoder.tag_based;

/**
 * An Observable TagBasedEncoder should implement this.
 * Or anything else that can observe a TagBasedEncoder.
 * Because if one of those is true it is observable.
 */
public interface TagEntryObservable {
    /**
     * Should add the observer to an internal list, to be called on a change.
     * @param obs an observer
     */
    void addTagEntryObserver(TagEntryObserver obs);
    /**
     * The provided observer will not be called again from this observable.
     * @param obs a previously added observer.
     */
    void removeTagEntryObserver(TagEntryObserver obs);
}
