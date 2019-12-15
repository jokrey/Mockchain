package jokrey.utilities.encoder.tag_based;

import jokrey.utilities.encoder.Encodable;
import jokrey.utilities.encoder.EncodableAsBytes;
import jokrey.utilities.encoder.EncodableAsString;
import jokrey.utilities.encoder.type_transformer.TypeToFromRawTransformer;
import jokrey.utilities.transparent_storage.TransparentStorage;

import java.util.Arrays;
import java.util.Iterator;

/**
 * Allows for a simple data to be encoded using tags.
 * Nesting this allows for infinitely complex storage structures.
 *      Nesting is achieved by letting complex classes encode their raw data using this utility class
 *         (good practise is to implement EncodableAsString interface and provide a constructor that takes an encoded String)
 * Accessing the data can be done over tags. If a String doesn't exist, the method(getEntry) will return null.
 * When switching around between versions this has to be caught, but if handled right, the remaining data is not lost.
 * The resulting string can then be stored without much effort.
 *
 * One may ask how the overhead compared to Length-Indicator based string encoding(LIse) is justified.
 *       (Since both cannot be altered externally)
 *   The answer lies in the much more declarative code.
 *   When reading from this system each time one has to exactly specify what one wants to read.
 *   Additionally the order or reading(decoding) does not matter anymore.
 *
 * NOTE: This is ENCODING, NOT ENCRYPTING
 *    Though the results of this method may at times be hard to read for any human, should the data be sensitive, then encryption is naturally still required.
 *    For an EncryptedEncoder use the {@link jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.encryption) package
 *
 * NOTE:
 *    Every function in this interface expect not null parameters.
 *    Except where otherwise specified it also returns non null values(except for getEntry and deleteEntry)
 *    Failure to comply results in either a NullPointerException or worse(though less likely) undefined behaviour
 *
 * @param <String> is the tag format. Should typically be a String, but may also be a ImString or anything else.
 * @param <SF> is the storage format the concrete Encoder uses in it's most raw form.
 *            The end user of the API should never see it.
 *
 * @author jokrey
 */
public interface TagBasedEncoder<SF> extends EncodableAsBytes, EncodableAsString, Encodable<SF>, Iterable<TagBasedEncoder.TaggedEntry<SF>> {
    /**
     * Should be a faster version of getEntry(String tag) != null. (have the same functionality)
     *
     * (For the sake of thread safety when using the SynchronizingTagBasedEncoder, this method SHOULD NOT ALTER the internal state at all)
     *
     * @param tag the tag identifying the entry
     * @return true if an entry with the tag exists_in_cache, false if it does not.
     * @throws RuntimeException any kind of RuntimeException if the working string has been altered externally (and the system is therefore in an undefined state)
     */
    boolean exists(String tag);

    /**
     * Returns the length of the content at tag as returned by getEntry(tag).length
     *   only faster because getEntry does not have to be actually queried
     *
     * (For the sake of thread safety when using the SynchronizingTagBasedEncoder, this method SHOULD NOT ALTER the internal state at all)
     *
     * @param tag the tag identifying the entry
     * @return length or -1 if the tag does not exist
     */
    long length(String tag);

    /**
     * Returns all the tags in the system.
     * As per condition each tag should only occur once and each tag should satisfy != null.
     *
     * (For the sake of thread safety when using the SynchronizingTagBasedEncoder, this method SHOULD NOT ALTER the internal state at all)
     *
     * @return tags in system
     */
    String[] getTags();


    /**
     * Deletes all contents and resets them to re encode
     *
     * @return this - for builder type usage
     */
    TagBasedEncoder<SF> clear();

    /**
     * Deletes the entry with the specified tag, but doesn't retrieve the entry
     * faster
     *
     * @param tag specified tag
     * @return if an entry has been deleted
     */
    boolean deleteEntry_noReturn(String tag);

    /**
     * USE WITH CARE
     *
     * Same as addEntryT(String tag, String entry), but does NOT check the for the unique tag condition.
     *    This may cause serious inconsistencies at decoding time, making it possible that this value can only be read with multiple deleteEntryT(tag)'s beforehand.
     *    Should ONLY be used if you KNOW for a fact that the tag does not exist within the system.
     *  May offer a great performance increase compared to addEntryT(String tag, String entry).
     *
     * @param tag the tag identifying the entry at decoding time.
     * @param entry value to be added
     * @return this - for builder type usage
     */
    TagBasedEncoder<SF> addEntry_nocheck(String tag, SF entry);

    /**
     * Adds the entry, with it's specified tag to the system.
     * If an entry with the specified tag is already in the system it is DELETED and replaced.
     *     To maintain the system condition that each tag is unique within the system.
     *
     * @param tag the tag identifying the entry at decoding time.
     * @param entry value to be added
     * @return whether an entry has been deleted | if an entry had to be replaced | tag previously existed
     */
    default boolean addEntry(String tag, SF entry) {
        boolean entry_deleted = deleteEntry_noReturn(tag);
        addEntry_nocheck(tag, entry);
        return entry_deleted;
    }

    /**
     * Retrieves the entry with the specified tag as a string.
     * If it was added to this system as a different type it may require (purposefully) undocumented "casting" or even more complex operations(for arrays).
     * If it was of a different type, then it should be extracted using the type specific getEntryT methods.
     *
     * (For the sake of thread safety when using the SynchronizingTagBasedEncoder, this method SHOULD NOT ALTER the internal state at all)
     *
     * Returns an object most close to the internal storage layout of that entry.
     *     (for LITBE it's byte[], for USE it's String, for example)
     * @param tag the tag identifying the entry
     * @return entry with tag as string OR null if the tag does not exist.
     */
    SF getEntry(String tag);

    /**
     * Retrieves the entry with the specified tag as a string.
     * If it was added to this system as a different type it may require (purposefully) undocumented "casting" or even more complex operations(for arrays).
     * If it was of a different type, then it should be extracted using the type specific getEntryT methods.
     *
     * Returns an object most close to the internal storage layout of that entry.
     *     (for LITBE it's byte[], for USE it's String, for example)
     *
     * Override discouraged.
     * @param tag the tag identifying the entry
     * @param default_value if no entry with the specified tag could be found, this variable is returned
     * @return Raw representation of entry at tag, or null if it does not exist.
     */
    default SF getEntry(String tag, SF default_value) {
        SF entry = getEntry(tag);
        if(entry == null) return default_value;
        else              return entry;
    }

    /**
     * Same as getEntryT(String tag), but if the entry was found it is removed from the working string.
     *      Might be useful for your everday decoding purposes,
     *      because it may offer an incremental performance increase for large working strings.
     *     (Because with each removed string, the strings to be searched become less and less)
     * @param tag the tag identifying the entry
     * @return entry with tag as string OR null if the tag does not exist
     * @throws RuntimeException any kind of RuntimeException if the working string has been altered externally (and the system is therefore in an undefined state)
     */
    SF deleteEntry(String tag);

    /**
     * Same as getEntryT(String tag), but if the entry was found it is removed from the working string.
     *      Might be useful for your everday decoding purposes,
     *      because it may offer an incremental performance increase for large working strings.
     *     (Because with each removed string, the strings to be searched become less and less)
     *
     * Override discouraged.
     * @param tag the tag identifying the entry
     * @param default_value if no entry with the specified tag could be found, this variable is returned
     * @return entry with tag as string OR default_value
     * @throws RuntimeException any kind of RuntimeException if the working string has been altered externally (and the system is therefore in an undefined state)
     */
    @SuppressWarnings("unchecked")
    default SF deleteEntry(String tag, SF default_value) {
        SF entry = deleteEntry(tag);
        if(entry == null) return default_value;
        else              return entry;
    }


    //TYPE METHODS
    /**
     * Supplies the raw type conversion model
     * @return transformer
     */
    default TypeToFromRawTransformer<SF> getTypeTransformer() {
//        if(transformer==null)
//            transformer=createTypeTransformer();
//        return transformer;
        return createTypeTransformer();
    }
//    private TypeToFromRawTransformer<SF> transformer = null;

    /**
     * Creates the raw type conversion model
     * Should be called only once and cached within getTypeTransformer
     * implied in the source code above
     *
     * @return transformer
     */
    TypeToFromRawTransformer<SF> createTypeTransformer();

    /**
     * @return the underlying storage system used by the encoder
     */
    TransparentStorage<SF> getRawStorageSystem();


    /**
     * Converts T to SF and calls:
     *
     * Override discouraged.
     * @see #addEntry_nocheck(String, Object)
     * @return this - for builder type usage
     */
    default <T> TagBasedEncoder<SF> addEntryT_nocheck(String tag, T entry) {
        addEntry_nocheck(tag, getTypeTransformer().transform(entry));
        return this;
    }

    /**
     * Subclass will convert T to SF and call:
     *
     * Override discouraged.
     * @see #addEntry(String, Object)
     * @return whether an entry has been deleted | if an entry had to be replaced | tag previously existed
     */
    default <T> boolean addEntryT(String tag, T entry) {
        return addEntry(tag, getTypeTransformer().transform(entry));
    }

    /**
     * Calls(and converts SF to T afterwards):
     *
     * Override discouraged.
     * @see #getEntry(String)
     */
    default <T>T getEntryT(String tag, Class<T> c) {
        SF entry = getEntry(tag);
        return entry == null ? null : getTypeTransformer().detransform(entry, c);
    }

    /**
     * Calls(and converts SF to T afterwards):
     *
     * Override discouraged.
     * @see #getEntry(String, Object)
     */
    @SuppressWarnings("unchecked")
    default <T>T getEntryT(String tag, T default_value) {
        T entry = (T) getEntryT(tag, default_value.getClass());
        if(entry == null) return default_value;
        else              return entry;
    }

    /**
     * Calls(and converts SF to T afterwards):
     *
     * Override discouraged.
     * @see #deleteEntry(String)
     */
    default <T>T deleteEntryT(String tag, Class<T> c) {
        SF entry = deleteEntry(tag);
        return entry == null ? null : getTypeTransformer().detransform(entry, c);
    }

    /**
     * Calls(and converts SF to T afterwards):
     *
     * Override discouraged.
     * @see #deleteEntry(String, Object)
     */
    @SuppressWarnings("unchecked")
    default <T>T deleteEntryT(String tag, T default_value) {
        T entry = (T) deleteEntryT(tag, default_value.getClass());
        if(entry == null) return default_value;
        else              return entry;
    }




    /**
     * Decodes from a raw representation.
     *     (for LITBE it calls readFromEncodedBytes, for USE it readFromEncodedString, for example)
     * Will restore the state it had when getEncoded was called.
     * @param encoded_raw encoded raw - preferably obtained using {@link #getEncoded()}
     */
    Encodable<SF> readFromEncoded(SF encoded_raw);

    /**
     * Maybe used with readFromEncoded for 'serialization'
     *
     * (For the sake of thread safety when using the SynchronizingTagBasedEncoder, this method SHOULD NOT ALTER the internal state at all)
     *
     * @return data currently encoded in the rawest form available
     */
    SF getEncoded();

    /**
     * Provides an iterator over each tagged entry.
     * Has to support nested iterators.
     * Has to support remove.
     * @return iterable
     */
    @Override Iterator<TaggedEntry<SF>> iterator();

    /**
     * Provides a tag iterator.
     * Has to support nested iterators.
     * Has to support remove.
     * @return iterable
     */
    default Iterable<String> tag_iterator() {
        Iterator<String> str_iter = Arrays.asList(getTags()).iterator();
        return () -> new Iterator<String>() {
            String last = null;
            @Override public boolean hasNext() {
                return str_iter.hasNext();
            }
            @Override public String next() {
                last = str_iter.next();
                return last;
            }
            @Override public void remove() {
                if(last == null)
                    throw new IllegalStateException("No last element to be removed is known at this time (no first next call or multiple remove calls).");
                deleteEntry_noReturn(last);
                last=null; //cannot remove twice
            }
        };
    }

    /**
     * Generic Tagged Entry to be used by the iterator
     * @param <SF>
     */
    class TaggedEntry<SF> {
        public final String tag;
        public final SF val;
        public TaggedEntry(String tag, SF val) {
            this.tag=tag;
            this.val=val;
        }
    }


    /**
     * subclass should also override:
     */
    @Override int hashCode();
    /**
     * subclass should also override:
     */
    @Override boolean equals(Object o);
}