package jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.bytes.tuple;

import jokrey.utilities.encoder.tag_based.TagBasedEncoder;
import jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.thread_safe.SynchronizingTagBasedEncoderBytes;
import jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.bytes.LITagBytesEncoder;
import jokrey.utilities.encoder.tag_based.tuple_tag.TupleTagBasedEncoder;
import jokrey.utilities.simple.data_structure.pairs.Pair;
import jokrey.utilities.transparent_storage.bytes.file.FileStorage;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation of a {@link TupleTagMultiLIEncodersBytesSynchronized} employing specifically a multitude of files in a directory to store the contents of raw multi {@link LITagBytesEncoder}.
 *
 * Any file in the provided directory maybe subject to change, including creation and deletion.
 *
 * @author jokrey
 */
public class TupleTagMultiFilesLIEncodersBytesSynchronized extends TupleTagMultiLIEncodersBytesSynchronized {
    private final File storage_directory;

    /**
     * Any file in the storage_directory can be deleted and altered.
     * @param storage_directory
     */
    public TupleTagMultiFilesLIEncodersBytesSynchronized(File storage_directory) {
        if(storage_directory.isDirectory() && storage_directory.exists())
            this.storage_directory = storage_directory;
        else
            throw new IllegalArgumentException("Passed storage_directory was either not a directory or could not be created.");
    }

    //not entirely thread safe
    @Override public TupleTagBasedEncoder<byte[]> clear() {
        File[] files_in_storage_dir = storage_directory.listFiles();
        if(files_in_storage_dir!=null) {
            for (File f : files_in_storage_dir) {
                if (!f.delete()) {
                    getSubEncoder(f.getName()).clear(); //if deleting failed, then we can try if the encoder can at least clear itself...
                }
            }
        }
        return this;
    }

    //TODO::: this is not that well tested, and may prove problematic on some file systems
    //required to be deterministic
    private String sanitize_as_filename(String before) { //too slow, not variable enough??
        String dissallowed_chars = "/\\?%*:|\"<> ";            //from: https://en.wikipedia.org/wiki/Filename#Reserved_characters_and_words
        String allowed_replacement_chars = "abcdefghijklmnopqrstuvwxysz1234567890_ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        if(before.isEmpty())
            return "empty._.string._.replacement";
        StringBuilder result = new StringBuilder(before.length());
        for(int i=0;i<before.length();i++) {
            if(dissallowed_chars.contains(String.valueOf(before.charAt(i)))) {
                result.append(allowed_replacement_chars.charAt(i%allowed_replacement_chars.length()));
            } else {
                result.append(before.charAt(i));
            }
        }
        return result.toString();
    }

    @Override public SynchronizingTagBasedEncoderBytes getNewEncoderFor(String super_tag) {
        try {
            synchronized (encoders) {
                if (encoders.size() > 100) {
                    Optional<Map.Entry<String, Pair<TagBasedEncoder<byte[]>, Long>>> oldest_opt = encoders.entrySet().stream().min(Comparator.comparingLong(o -> o.getValue().r));
                    oldest_opt.ifPresent(oldest -> encoders.remove(oldest.getKey()));
                }
            }
            return new SynchronizingTagBasedEncoderBytes(new LITagBytesEncoder(new FileStorage(new File(storage_directory.getAbsolutePath() + "/" +sanitize_as_filename(super_tag)))));
        } catch (FileNotFoundException e) {
            return null;
        }
    }
}
