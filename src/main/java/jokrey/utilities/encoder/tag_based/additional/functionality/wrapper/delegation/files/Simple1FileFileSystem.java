package jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.files;

import jokrey.utilities.encoder.tag_based.TagBasedEncoderBytes;
import jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.bytes.LITagBytesEncoder;
import jokrey.utilities.transparent_storage.StorageSystemException;
import jokrey.utilities.transparent_storage.bytes.file.FileStorage;

import java.io.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Extremely simple, directory lacking, file system inside a single file.
 * Every tag represents a path inside the system.
 *
 * Essentially just a semantic, delegation instead of inheritance wrapper around a UniversalByteEncoder.
 * As such it may be able to read a raw encoder FileStorage file.. ( interpreting the tags of that system as internal file paths )
 *
 * @author jokrey
 */
public class Simple1FileFileSystem implements AutoCloseable {
    /**
     * Standard file ending for this file system.
     */
    public static final String FILE_ENDING = "litbe";

    private TagBasedEncoderBytes encoder;
    private String file_path;

    /**
     * Default no-arg, do nothing constructor.
     * System can be initialised later using {@link #open(String)} or {@link #open(TagBasedEncoderBytes, String)}
     */
    public Simple1FileFileSystem() {}

    /**
     * Instantly opens this system with provided litbe_encoded_file.
     * The File should either not exist, be empty or provide an existing encoder layout.
     * @param file_path a file path (if it does not exist a file will be created)
     * @throws IOException on error opening the provided file
     */
    public Simple1FileFileSystem(String file_path) throws Exception {
        open(file_path);
    }

    /**
     * Instantly opens this system with provided litbe_encoded_file.
     * The File should either not exist, be empty or provide an existing encoder layout.
     * @param file_path a file path (if it does not exist a file will be created)
     * @throws IOException on error opening the provided file
     */
    public Simple1FileFileSystem(TagBasedEncoderBytes encoder, String file_path) {
        try {
            open(encoder, file_path);
        } catch(Exception e) {
            //not thrown
        }
    }

    /**
     * Closes any previous state and opens this system with provided litbe_encoded_file.
     * The File should either not exist, be empty or provide an existing encoder layout.
     * @param file_path file path
     * @throws IOException on error opening the provided file
     */
    public void open(String file_path) throws FileNotFoundException {
        try {
            open(new LITagBytesEncoder(new FileStorage(new File(file_path), 16384)), file_path);
        } catch (FileNotFoundException e) {
            throw e;
        } catch(Exception e) {
            //not thrown
        }
    }

    public void open(TagBasedEncoderBytes encoder, String file_path) throws Exception {
        close();
        this.encoder = encoder;
        this.file_path=file_path;
    }

    /**
     * Closes this system, releases resources. Close the file the encoder has been editing.
     * @throws IOException on error closing any of the used resources
     */
    @Override public void close() throws Exception {
        if(encoder != null) encoder.getRawStorageSystem().close();
        encoder =null;
        file_path = null;
    }


    /**
     * Helper to create an internal path from a previous absolute path
     * @param dir_root_path actually existing part of the complete path
     * @param complete_path path containing the inner path
     * @return inner path
     */
    public String getInnerPath(String dir_root_path, String complete_path) {
        complete_path = complete_path.replaceAll("\\\\", "/");
        dir_root_path = dir_root_path.replaceAll("\\\\", "/");
        if(complete_path.contains(dir_root_path)) {
            String sub = complete_path.substring(dir_root_path.length());
            if(sub.startsWith("/"))
                return sub.substring(1);
            else
                return sub;
        }
        return null;
    }

    /**
     * Helper to pretend a file path was extended into this 'directory'.
     * f^-1 for getStoredVirtualPaths()
     *
     * @param virtual_file_path complete path
     * @return inner path
     */
    public String getInnerPath(String virtual_file_path) {
        return getInnerPath(file_path, virtual_file_path);
    }

    /**
     * Helper to pretend the internal path are complete files extended into this directory.
     * @return all stored virtual paths
     */
    public String[] getStoredVirtualPaths() {
        String[] internals = getStoredInternalPaths();
        String[] virtual_paths = new String[internals.length];
        for(int i=0;i<internals.length;i++)
            virtual_paths[i] = file_path+"/"+internals[i];
        return virtual_paths;
    }


    /**
     * Returns all 'internal path's used by the file queries and file manipulation functions
     * @return an array of internal paths
     */
    public String[] getStoredInternalPaths() {
        try {
            return encoder.getTags();
        } catch (StorageSystemException e) {
            return new String[0];
        }
    }

    /**
     * @param internal_path internal path (as received by for example {@link #getStoredInternalPaths()})
     * @return Whether the internal path exist
     */
    public boolean exists(String internal_path) {
        try {
            return internal_path!=null && (internal_path.isEmpty() || encoder.exists(internal_path));
        } catch (StorageSystemException e) {
            return false;
        }
    }

    /**
     * @param internal_path internal path (as received by for example {@link #getStoredInternalPaths()})
     * @return Length of byte array stored at internal path or -1 if it does not exist
     */
    public long length(String internal_path) {
        try {
            return internal_path==null?-1: encoder.length(internal_path);
        } catch (StorageSystemException e) {
            return -1;
        }
    }



    //IO IO

    /**
     * Deletes file at path or does nothing if the path doesn't exist
     * @param internal_path internal path
     * @return whether or something has been deleted
     */
    public boolean delete(String internal_path) {
        try {
            if(internal_path!=null) {
                encoder.deleteEntry_noReturn(internal_path);
                return true;
            }
        } catch (StorageSystemException e) {
            return false;
        }
        return false;
    }

    /**
     * Finds the InputStream for the file at path internal path or null if the path is unknown.
     * @param internal_path internal path
     * @return stream or null
     */
    public InputStream getInputStream(String internal_path) {
        try {
            return internal_path==null?
                    null:
                    encoder.getEntry_asLIStream(internal_path).r;
        } catch (StorageSystemException e) {
            return null;
        }
    }

    /**
     * Internally first deletes the existing file content at path, then writes the new file to the end of storage.
     * Meaning any subsequent operations on the path may be slower in access(has to search the entire file),
     *   but may be quicker in setting the paths content(because delete has to copy less)
     *
     * @param internal_path path to the set content of
     * @param arr content to be set
     */
    public void setFileContent(String internal_path, byte[] arr) throws StorageSystemException {
        encoder.addEntry(internal_path, arr);
    }

    /**
     * Internally first deletes the existant file content at path, then writes the new file to the end of storage.
     * Meaning any subsequent operations on the path may be slower in access(has to search the entire file),
     *   but may be quicker in setting the paths content(because delete has to copy less)
     *
     * Stream will be CLOSED after reading.
     *
     * content_length has to be correct or an exception will be thrown
     *
     * @param internal_path path to the set content of
     * @param content stream to read content from
     * @param content_length exact content length
     */
    public void setFileContent(String internal_path, InputStream content, long content_length) throws StorageSystemException {
        encoder.addEntry(internal_path, content, content_length);
    }

    /**
     * Same as setFileContent(String internal_path, InputStream content, long content_length),
     *    BUT it is NOT checked whether or not the path already exists_in_cache.
     *    It basically tells the system to trust that it doesn't exist.
     *    If it turns out it does already exist, then one of the two files may not be easily decodable.
     *
     * @param internal_path path to the set content of
     * @param content stream to read content from
     * @param content_length exact content length
     */
    public void addGuaranteedNewFile(String internal_path, FileInputStream content, long content_length) throws StorageSystemException {
        encoder.addEntry_nocheck(internal_path, content, content_length);
    }


    /**
     * Iterator over the internal Files as streams.
     * Should the underlying data change, the streams may fail.
     * @return iterator over files as streams
     */
    public Iterable<LITagBytesEncoder.TaggedStream> getFileIterator() {
        return encoder.getEntryIterator_stream();
    }








    //VIRTUAL DIRECTORIES FUNCTIONALITY (NO EMPTY DIRECTORIES)
    /**
     * @param virtual_dir_path
     * @return .l = dirs, .r = fies
     */
    public String[] getVirtualPathsInVirtualDir(String virtual_dir_path) {
        if(!virtual_dir_path.contains(file_path)) return new String[0];
        String inner_dir_path = virtual_dir_path.substring(file_path.length());
        if(inner_dir_path.equals("/"))
            inner_dir_path = "";
        if(inner_dir_path.startsWith("/"))
            inner_dir_path = inner_dir_path.substring(1);

        Set<String> virtual = new HashSet<>();

        String[] allInternalPaths = getStoredInternalPaths(); //todo: too memory straining, allow a getTags(regex) search..
        for(String s:allInternalPaths) {
            if(s.startsWith(inner_dir_path)) {
                s = s.substring(inner_dir_path.length());
                if(s.startsWith("/")) s=s.substring(1);

                if (s.contains("/")) {
                    virtual.add(file_path + "/" + inner_dir_path + "/" + s.substring(0, s.indexOf("/") + 1));
                } else {
                    virtual.add(file_path + "/" + inner_dir_path + "/" + s);
                }
            }
        }

        return virtual.toArray(new String[0]);
    }

    public boolean isDirectory(String virtual_dir_path) {
        if(!virtual_dir_path.contains(file_path)) return false;
        virtual_dir_path = virtual_dir_path.substring(file_path.length());
        if(virtual_dir_path.endsWith("/"))
            virtual_dir_path = virtual_dir_path.substring(0, virtual_dir_path.length()-1);
        if(virtual_dir_path.startsWith("/"))
            virtual_dir_path = virtual_dir_path.substring(1);

        String[] allInternalPaths = getStoredInternalPaths(); //todo: too memory straining, allow a getTags(regex) search..
        for(String s:allInternalPaths) {
            if(s.startsWith(virtual_dir_path) && s.length() > virtual_dir_path.length()) {
                return true;
            }
        }
        return false;
    }

    public long addNewDirectoryToSystem(File original_directory, File directory_to_add) {
        if(!directory_to_add.exists() || !directory_to_add.isDirectory())
            throw new IllegalArgumentException("Provided directory file(arg2) is not a valid directory");

        File[] sub_files = directory_to_add.listFiles();
        long errors = 0;//yes we do need a long for this. (honestly we do not actually, but I think it's funny)
        if(sub_files!=null) {
            for(File f:sub_files) {
                if(f.isDirectory()) {
                    errors+= addNewDirectoryToSystem(original_directory, f);
                } else {
                    try {
                        addGuaranteedNewFile(getInnerPath(original_directory.getAbsolutePath(), f.getAbsolutePath()),
                                new FileInputStream(f), f.length());
                    } catch(Exception ex) {
                        ex.printStackTrace();
                        errors++;
                    }
                }
            }
        }
        return errors;
    }
}