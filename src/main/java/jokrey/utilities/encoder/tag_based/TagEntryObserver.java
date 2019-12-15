package jokrey.utilities.encoder.tag_based;

/**
 * Allows callbacks for successfully completed altering operations on the database system.
 *
 * @author jokrey
 */
public interface TagEntryObserver {
    /**
     * Called when an 'add' update was received (i.e. someone called addEntry, addEntry_nocheck, etc..)
     * @param tag tag of entry updated
     */
    void update_add(String tag);
    /**
     * Called when a 'delete' update was received (i.e. someone called deleteEntry_noReturn, deleteEntry, etc..)
     * @param tag tag of entry updated
     */
    void update_delete(String tag);
    /**
     * Called when the entire content was changed.
     */
    void update_set_content();
}