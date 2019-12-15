package jokrey.utilities.transparent_storage.bytes.non_persistent;

import jokrey.utilities.transparent_storage.StorageSystemException;
import jokrey.utilities.transparent_storage.bytes.TransparentBytesStorage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Implementation of {@link TransparentBytesStorage} writing the bytes into RAM.
 * Uses an only growing byte[] to store the data in RAM.
 *
 * This never shrinking is a little insane. This issues is addressed by the new version: {@link ByteArrayStorage}
 *
 * @author jokrey
 * @deprecated replaced by {@link ByteArrayStorage}
 */
public class ByteArrayStorageLegacy extends ByteArrayOutputStream implements TransparentBytesStorage {
    /**
     * Default, no-arg constructor.
     * Preallocates 64 bytes in internal byte buffer.
     */
    public ByteArrayStorageLegacy() {
        super(64);
    }

    /**
     * Quick init-Constructor
     * Short for: new ByteArrayStorage().setContent(start_buf)
     *
     * @param start_buf initial raw buffer
     */
    public ByteArrayStorageLegacy(byte[] start_buf) {
        super(0);
        setContent(start_buf);
    }


    @Override public void clear() {
        buf = new byte[64];
        count=0;
    }


    @Override public void setContent(byte[] content) {
        buf=content;
        count = content.length;
    }


    @Override public byte[] getContent() {
        return toByteArray();
    }


    @Override public ByteArrayStorageLegacy delete(long start, long end) throws StorageSystemException {
        int len = (int) (end - start);
        if (len > 0) {
            System.arraycopy(this.buf, (int)start+len, this.buf, (int)start, (int) (this.count-end));
            this.count -= len;

            if(count < buf.length/2) { //if the internal array is more than half as big as the currently used up space
                byte[] temp = new byte[buf.length/2];
                System.arraycopy(buf, 0, temp, 0, count);
                buf = temp;
            }

            return this;
        } else {
            throw new StorageSystemException("Cannot delete 0 or less than 0 bytes.");
        }
    }



    @Override public TransparentBytesStorage append(InputStream content, long content_length) throws StorageSystemException {
        try {
            int nRead;
            byte[] data = new byte[4096];
            while (content_length>0 && (nRead = content.read(data, 0, data.length)) != -1) {
                write(data, 0, (int) Math.min(nRead, content_length));
                content_length-=nRead;
            }
            content.close();
            if(content_length>0) {
                while (content_length>0) {
                    write(data, 0, (int) Math.min(data.length, content_length));
                    content_length -= data.length;
                }
                throw new StorageSystemException("The provided stream failed to deliver the promised number of bytes");
            }
        } catch (IOException ex) {
            throw new StorageSystemException("IO Exception thrown by provided InputStream("+ex.getMessage()+").");
        }
        return this;
    }

    @Override public TransparentBytesStorage set(long start, InputStream content, long content_length) throws StorageSystemException {
        try {
            int nRead;
            byte[] data = new byte[4096];
            while (content_length>0 && (nRead = content.read(data, 0, data.length)) != -1) {
                set(start, nRead!=4069?Arrays.copyOfRange(data, 0, nRead):data);
                start+=nRead;
                content_length-=nRead;
//                write(data, 0, (int) Math.min(nRead, content_length));
            }
            content.close();
            if(content_length>0) {
                while (content_length>0) {
                    int toWrite = (int) Math.min(data.length, content_length);
                    set(start, toWrite!=4069?Arrays.copyOfRange(data, 0, toWrite):data);
                    start+=toWrite;
                    content_length -= data.length;
                }
                throw new StorageSystemException("The provided stream failed to deliver the promised number of bytes");
            }
        } catch (IOException ex) {
            throw new StorageSystemException("IO Exception thrown by provided InputStream("+ex.getMessage()+").");
        }
        return this;
    }


    @Override public byte[] sub(long start, long end) throws StorageSystemException {
        if(end > count)end=count; //to satisfy interface doc condition
        int len = (int) (end - start);
        if (len > 0) {
            byte[] part = new byte[(int) (end - start)];
            System.arraycopy(buf, (int) start, part, 0, part.length);
            //        for(int i=0;i<part.length;i++) //substring
            //            part[i] = buf[i1 + i];
            return part;
        } else if(len==0)
            return new byte[0];
        else
            throw new StorageSystemException("Cannot create a byte array with less than 0 bytes.");
    }

    @Override public TransparentBytesStorage set(long start, byte[] part, int off, int off_end) throws StorageSystemException {
        if(start > count) throw new StorageSystemException("Too large, start > count");
        if(start==count)
            write(part, off, off_end);
        else {
            while (start + part.length > count)
                write(0);
            System.arraycopy(part, off, buf, (int) start, off_end-off);
        }
        return this;
    }

    @Override public TransparentBytesStorage set(long start, byte[] part, int off) throws StorageSystemException {
        return set(start, part, 0, part.length);
    }

    @Override public InputStream substream(long start, long end) throws StorageSystemException {
        return new ByteArrayInputStream(sub(start, end));
    }

    @Override public InputStream stream() {
        return new ByteArrayInputStream(buf);
    }


    @Override public long contentSize() {
        return size();
    }


    @Override public int hashCode() {
        return Arrays.hashCode(getContent());
    }

    @Override public boolean equals(Object o) {
        return o instanceof ByteArrayStorage && Arrays.equals(getContent(), ((ByteArrayStorage) o).getContent());
    }

    @Override public synchronized String toString() {
        return "["+getClass().getSimpleName()+": l="+size()+", cont="+Arrays.toString(getContent())+"]";
    }
}