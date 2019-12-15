package jokrey.utilities.transparent_storage.bytes.file;

import jokrey.utilities.transparent_storage.StorageSystemException;
import jokrey.utilities.transparent_storage.bytes.TransparentBytesStorage;

import java.io.*;

/**
 * Implementation of {@link TransparentBytesStorage} writing the bytes into a file.
 * Should be as efficient as it get's using a RandomAccessFile.
 * Deletion requires a lot of copying, so whenever possible only call once.
 *
 * Querying all content (via {@link #getContent()}) might result in arrays too large for ram.
 *    That will throw an exception, so generally do not use getContent(). Except for testing(maybe).
 *
 * Thread safe and reentrant for all methods.
 *
 * @author jokrey
 */
public class FileStorage implements TransparentBytesStorage {
    private final RandomAccessFile raf;
    private final int io_buffer_size;

    /**
     * Internally creates and opens access to the provided file.
     * close should be called after one is done using it, to not create a resource leak.
     *
     * io_buffer_size will be used as the buffer size for the internal stream copy operations (namely delete and append(InputStream))
     *    For bigger files it should be between (2^12(4096) and 2^16(65536)) or even bigger depending on RAM capabilities.
     *    However if such a buffer size is simply not required or possible, because the file will remain small of this is run on a RasPi(for example).
     *      Then the overhead may cause issues.
     *      Choose wisely
     *
     * @param file file to create and read from.
     * @param io_buffer_size buffer size to use for internal copy operations
     * @throws FileNotFoundException if the file cannot be created
     */
    public FileStorage(File file, int io_buffer_size) throws FileNotFoundException {
        raf= new RandomAccessFile(file, "rw");
//        raf= new RandomAccessFile(file, "rwd"); //should maybe be used, but might make it less performant
        this.io_buffer_size=io_buffer_size;
    }

    /**
     * Same as {@link FileStorage(File, int),
     *    but takes a standard io_buffer_size of 8192(2^13)
     *
     * @param file file to create and read from.
     * @throws FileNotFoundException if the file cannot be created
     */
    public FileStorage(File file) throws FileNotFoundException {
        this(file, 8192);
    }

    /**
     * Closes the file
     * @throws IOException on underlying file close exception
     */
    public void close() throws IOException {
        raf.close();
    }


    @Override public void clear() {
        setContent(new byte[0]);
    }


    @Override public void setContent(byte[] content) throws StorageSystemException {
        try {
            synchronized (raf) {
                raf.seek(0);
                raf.write(content);
                raf.setLength(content.length);
            }
        } catch (IOException e) {
            throw new StorageSystemException("Internal FileStorage-Error("+e.getMessage()+").");
        }
    }


    @Override public byte[] getContent() throws StorageSystemException {
        try {
            byte[] content;
            int actuallyRead;
            synchronized (raf) {
                raf.seek(0);
                content = new byte[(int) raf.length()]; //might throw an exception, which is fine.
                actuallyRead = raf.read(content);
            }
            if(actuallyRead==content.length) {
                return content;
            } else
                throw new StorageSystemException("Internal FileStorage-Error. It read an inconsistent number of bytes.");
        } catch (IOException e) {
            throw new StorageSystemException("Internal FileStorage-Error("+e.getMessage()+").");
        }
    }


    @Override public TransparentBytesStorage delete(long start, long end) throws StorageSystemException {
        try {
            long len = end - start;
            if (len > 0) {
                synchronized (raf) {
                    long raf_length = raf.length();

                    long rest_of_file_length = raf_length - end;
                    long buffer_size = io_buffer_size;
                    for (long cur_write_index = 0; cur_write_index < rest_of_file_length; cur_write_index += buffer_size) {
                        byte[] rest_of_file_part = sub(end + cur_write_index, end + cur_write_index + Math.min(buffer_size, rest_of_file_length));

                        raf.seek(start + cur_write_index);
                        raf.write(rest_of_file_part);
                    }

                    raf.setLength(raf_length - len);
                }
                return this;
            } else
                throw new StorageSystemException("Cannot delete 0 or less than 0 bytes.");
        } catch (IOException e) {
            throw new StorageSystemException("Internal FileStorage-Error("+e.getMessage()+").");
        }
    }



    @Override public TransparentBytesStorage set(long start, InputStream content, long content_length) throws StorageSystemException {
        try {
            long expected_bytes_count = content_length;
            synchronized (raf) {
//                long raf_length = raf.length();
                raf.seek(start);
                raf.setLength(Math.max(start + content_length, contentSize())); //too ensure at least content_length bytes exist at the correct position now.
                int nRead;
                byte[] data = new byte[(int) Math.min(io_buffer_size, content_length)];
                while (content_length > 0 && (nRead = content.read(data, 0, data.length)) != -1) {
                    raf.write(data, 0, (int) Math.min(nRead, content_length));
                    content_length -= nRead;
                }
                content.close();
                if (content_length > 0) {
                    throw new StorageSystemException("the provided stream failed to deliver the promised number of bytes(missing: " + content_length + " of expected " + expected_bytes_count + ")");
                }
            }
        } catch (IOException ex) {
            throw new StorageSystemException("IO Exception thrown by provided InputStream("+ex.getMessage()+")");
        }
        return this;
    }


    @Override public TransparentBytesStorage set(long start, byte[] part, int off, int off_end) throws StorageSystemException {
        long size = contentSize();
        if(start > size)
            throw new StorageSystemException("IndexOutOfBounds, start > size");
        try {
            synchronized (raf) {
                raf.setLength(Math.max(start + (off_end-off), contentSize())); //pre ensure length met
                raf.seek(start);
                raf.write(part, off,  Math.min(part.length-off, off_end-off));
            }
        } catch (IOException ex) {
            throw new StorageSystemException("IO Exception thrown by provided InputStream("+ex.getMessage()+")");
        }
        return this;
    }

    @Override public TransparentBytesStorage set(long start, byte[] part, int off) throws StorageSystemException {
        return set(start, part, 0, part.length);
    }

    @Override public byte[] sub(long start, long end_given) throws StorageSystemException {
        long size = contentSize();
        long end = end_given > size ? size : end_given; //to satisfy interface doc condition
        try {
            long len = end - start;
            if (len > 0) {
                synchronized (raf) {
                    raf.seek(start);
                    byte[] subarray = new byte[(int) len];
                    raf.read(subarray);
                    return subarray;
                }
            } else if(len==0)
                return new byte[0];
            else
                throw new StorageSystemException("Cannot create sub of less than 0 bytes.");
        } catch (IOException e) {
            throw new StorageSystemException("Internal FileStorage-Error("+e.getMessage()+").");
        }
    }


    /**
     * NEVER THREAD SAFE
     * {@inheritDoc}
     */
    @Override public InputStream substream(long start, long end_given) throws StorageSystemException {
        long size = contentSize();
        long end = end_given > size ? size : end_given; //to satisfy interface doc condition
        try {
            long len = end - start;
            if(start<0) {
                throw new StorageSystemException("the start position("+end+") has to be greater than 0");
            } else if (len < 0) {
                throw new StorageSystemException("Cannot create substream of less than 0 bytes");
            } else {
                raf.seek(start);
                return (new InputStream() {
                    long position = start;
                    @Override public int read() throws IOException {
                        if(position>=end) return -1;

                        position++;
                        return raf.read();
                    }
                    @Override public int read(byte[] b) throws IOException {
                        return read(b, 0, b.length);
                    }
                    @Override public int read(byte[] b, int off, int len) throws IOException {
                        if(position>=end) {
                            return -1;
                        }

                        if(position + len >= end) {
                            len = (int) (end-position);
                        }
                        position += len;
                        return raf.read(b, off, len);
                    }
                    @Override public long skip(long n) throws IOException {
                        System.err.println("FileStorage.substream.skip has been used and will work, but is not optimized. -> Optimize now");
//                        position+=n;
//                        raf.skipBytes((int) n);
                        return super.skip(n);
                    }
                });
            }
        } catch (IOException e) {
            throw new StorageSystemException("Internal FileStorage-Error("+e.getMessage()+").");
        }
    }

    @Override public InputStream stream() {
        try {
            raf.seek(0);
            return new InputStream() {
                @Override public int read() throws IOException {
                    return raf.read();
                }
                @Override public int read(byte[] b, int off, int len) throws IOException {
                    return raf.read(b, off, len);
                }
            };
        } catch (IOException e) {
            throw new StorageSystemException("Internal FileStorage-Error("+e.getMessage()+").");
        }
    }


    @Override public long contentSize() throws StorageSystemException {
        try {
            synchronized (raf) {
                return raf.length();
            }
        } catch (IOException e) {
            throw new StorageSystemException("Internal FileStorage-Error("+e.getMessage()+").");
        }
    }

    //would be too slow to actually use, so prohibit usage

    @Override public boolean equals(Object obj) {
        throw new UnsupportedOperationException("equals not supported on file storage system");
    }

    @Override public int hashCode() {
        throw new UnsupportedOperationException("hashCode not supported on file storage system");
    }
}
