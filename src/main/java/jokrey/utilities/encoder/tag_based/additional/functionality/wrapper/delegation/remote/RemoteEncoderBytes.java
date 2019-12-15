package jokrey.utilities.encoder.tag_based.additional.functionality.wrapper.delegation.remote;

import jokrey.utilities.encoder.tag_based.TagBasedEncoderBytes;
import jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.type.transformer.LITypeToBytesTransformer;
import jokrey.utilities.encoder.type_transformer.TypeToFromRawTransformer;
import jokrey.utilities.network.mcnp.io.MCNP_ClientIO;
import jokrey.utilities.simple.data_structure.pairs.Pair;
import jokrey.utilities.transparent_storage.StorageSystemException;
import jokrey.utilities.transparent_storage.bytes.TransparentBytesStorage;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import static jokrey.utilities.encoder.type_transformer.bytes.TypeToBytesTransformer.string_to_bytes;

/**
 * Detail can be found in the_theory_of_rbae.txt
 *
 * RemoteEncoderBytes is imagined as a lightweight network c to the actual data.
 * Search, add, delete are all executed on the server.
 * Multiple clients can see, edit and be notified about changes on that remote data.
 *
 * @author jokrey
 */
public class RemoteEncoderBytes implements TagBasedEncoderBytes, AutoCloseable {
    private final MCNP_ClientIO client;

    /**
     * Connects to RemoteEncoderServer at url and port.
     *
     * Timeouts after three seconds
     *
     * @param url server url
     * @param port server port
     * @throws IOException on io error
     */
    public RemoteEncoderBytes(String url, int port) throws IOException {
        this(new MCNP_ClientIO(url, port, 3000));
        client.send_cause(RemoteEncoderMCNPCauses.CONNECTION_TYPE__IS_CLIENT);
    }
    public RemoteEncoderBytes(String url) throws IOException {
        this(url, RemoteEncoderMCNPCauses.DEFAULT_SERVER_PORT);
    }
    protected RemoteEncoderBytes(MCNP_ClientIO client) {
        super();
        this.client = client;
    }

    /**
     * Closes c to the server.
     * @throws IOException on io error
     */
    @Override public void close() throws IOException {
        client.close();
    }

    /**
     * Unsupported. The storage system is not locally available. The instance is remote. A copy would not help.
     * @return nothing, throws an exception
     */
    @Override public TransparentBytesStorage getRawStorageSystem() {
        throw new UnsupportedOperationException("remote storage system is remote");
    }

    private TypeToFromRawTransformer<byte[]> transformer = null;
    @Override public TypeToFromRawTransformer<byte[]> getTypeTransformer() {
        if(transformer==null)
            transformer=createTypeTransformer();
        return transformer;
    }
    @Override public TypeToFromRawTransformer<byte[]> createTypeTransformer() {
        return new LITypeToBytesTransformer();
    }


    @Override public synchronized boolean addEntry(String tag, byte... arr) {
        try {
            client.send_cause(RemoteEncoderMCNPCauses.ADD_ENTRY_BYTE_ARR);
            client.send_variable(string_to_bytes(tag));
            client.send_variable(arr);
            byte result = client.receive_byte();
            if(result==RemoteEncoderMCNPCauses.ERROR)
                throw new StorageSystemException("Error getting that status");
            else if(result==RemoteEncoderMCNPCauses.TRUE)
                return true;
            else if(result==RemoteEncoderMCNPCauses.FALSE)
                return false;
            else
                throw new StorageSystemException("Invalid response from server");
        } catch (IOException ex) {
            throw new StorageSystemException("IO Exception thrown by provided InputStream("+ex.getMessage()+")");
        }
    }

    @Override public synchronized RemoteEncoderBytes addEntry_nocheck(String tag, byte[] arr) throws StorageSystemException {
        try {
            client.send_cause(RemoteEncoderMCNPCauses.ADD_ENTRY_BYTE_ARR_NOCHECK);
            client.send_utf8(tag);
            client.send_variable(arr);
            byte result = client.receive_byte();
            if(result != RemoteEncoderMCNPCauses.NO_ERROR) //supposed to indicate that no error occurred
                throw new StorageSystemException("Server said that add entry failed.");
        } catch (IOException ex) {
            throw new StorageSystemException("IO Exception thrown by provided InputStream("+ex.getMessage()+")");
        }
        return this;
    }


    @Override public synchronized byte[] getEntry(String tag) throws StorageSystemException {
        try {
            client.send_cause(RemoteEncoderMCNPCauses.GET_ENTRY_BYTE_ARR);
            client.send_variable(string_to_bytes(tag));
            return client.receive_variable();
        } catch (EOFException ex) {
            return null;
        } catch (IOException e) {
            throw new StorageSystemException("Internal Remote-Error("+e.getMessage()+").");
        }
    }


    @Override public synchronized byte[] deleteEntry(String tag) throws StorageSystemException {
        try {
            client.send_cause(RemoteEncoderMCNPCauses.DELETE_ENTRY_BYTE_ARR);
            client.send_variable(string_to_bytes(tag));
            return client.receive_variable();
        } catch (EOFException ex) {
            return null;
        } catch (IOException e) {
            throw new StorageSystemException("Internal Remote-Error("+e.getMessage()+").");
        }
    }


    @Override public synchronized boolean deleteEntry_noReturn(String tag) {
        try {
            client.send_cause(RemoteEncoderMCNPCauses.DELETE_NO_RETURN);
            client.send_variable(string_to_bytes(tag));
            byte result = client.receive_byte();
            if(result==RemoteEncoderMCNPCauses.ERROR)
                throw new StorageSystemException("Error getting that status");
            else if(result==RemoteEncoderMCNPCauses.TRUE)
                return true;
            else if(result==RemoteEncoderMCNPCauses.FALSE)
                return false;
            else
                throw new StorageSystemException("Invalid response from server");
        } catch (IOException e) {
            throw new StorageSystemException("Internal Remote-Error("+e.getMessage()+").");
        }
    }

    @Override public synchronized RemoteEncoderBytes clear() {
        try {
            client.send_cause(RemoteEncoderMCNPCauses.CLEAR);
            byte result = client.receive_byte();
            if(result != RemoteEncoderMCNPCauses.NO_ERROR)
                 throw new StorageSystemException("Error clearing: "+result);
        } catch (IOException e) {
            throw new StorageSystemException("Internal Remote-Error("+e.getMessage()+").");
        }
        return this;
    }

    @Override public synchronized boolean exists(String tag) {
        try {
            client.send_cause(RemoteEncoderMCNPCauses.EXISTS);
            client.send_variable(string_to_bytes(tag));
            byte result = client.receive_byte();
            if(result==RemoteEncoderMCNPCauses.ERROR)
                throw new StorageSystemException("Error getting that status");
            else if(result==RemoteEncoderMCNPCauses.TRUE)
                return true;
            else if(result==RemoteEncoderMCNPCauses.FALSE)
                return false;
            else
                throw new StorageSystemException("Invalid response from server");
        } catch (IOException e) {
            throw new StorageSystemException("Internal Remote-Error("+e.getMessage()+").");
        }
    }

    @Override public synchronized String[] getTags() {
        try {
            client.send_cause(RemoteEncoderMCNPCauses.GET_TAGS);
            byte[] encoded_tags = client.receive_variable();
            return getTypeTransformer().detransform_array(encoded_tags, String[].class);
        } catch (IOException e) {
            throw new StorageSystemException("Internal Remote-Error("+e.getMessage()+").");
        }
    }

    @Override public synchronized long length(String tag) throws StorageSystemException {
        try {
            client.send_cause(RemoteEncoderMCNPCauses.LENGTH);
            client.send_variable(string_to_bytes(tag));
            long result = client.receive_int64();
            if(result==RemoteEncoderMCNPCauses.ERROR)
                throw new StorageSystemException("Error getting length .");
            else
                return result;
        } catch (IOException e) {
            throw new StorageSystemException("Internal Remote-Error("+e.getMessage()+").");
        }
    }


    @Override public synchronized void readFromEncodedBytes(byte[] encoded_bytes) {
        try {
            client.send_cause(RemoteEncoderMCNPCauses.SET_CONTENT);
            client.send_variable(encoded_bytes);
            if (client.receive_byte() != RemoteEncoderMCNPCauses.NO_ERROR)
                throw new StorageSystemException("Server said that set content failed.");
        } catch (IOException e) {
            throw new StorageSystemException("Internal Remote-Error("+e.getMessage()+").");
        }
    }


    @Override public synchronized byte[] getEncodedBytes() throws StorageSystemException {
        try {
            client.send_cause(RemoteEncoderMCNPCauses.GET_CONTENT);
            return client.receive_variable();
        } catch (IOException e) {
            throw new StorageSystemException("Internal Remote-Error("+e.getMessage()+").");
        }
    }



    public synchronized boolean addEntry(String tag, InputStream content, long content_length) throws StorageSystemException {
        try {
            client.send_cause(RemoteEncoderMCNPCauses.ADD_ENTRY_BYTE_ARR);
            client.send_variable(string_to_bytes(tag));
            client.send_variable_from(content_length, content);
            byte result = client.receive_byte();
            if(result==RemoteEncoderMCNPCauses.ERROR)
                throw new StorageSystemException("Error getting that status");
            else if(result==RemoteEncoderMCNPCauses.TRUE)
                return true;
            else if(result==RemoteEncoderMCNPCauses.FALSE)
                return false;
            else
                throw new StorageSystemException("Invalid response from server");
        } catch (IOException ex) {
            throw new StorageSystemException("IO Exception thrown by provided InputStream("+ex.getMessage()+")");
        }
    }

    public synchronized Pair<Long, InputStream> getEntry_asLIStream(String tag) throws StorageSystemException {
        try {
            client.send_cause(RemoteEncoderMCNPCauses.GET_ENTRY_BYTE_ARR);
            client.send_variable(string_to_bytes(tag));
            return client.get_variable_receiver();
        } catch (EOFException ex) {
            return null;
        } catch (IOException e) {
            throw new StorageSystemException("Internal Remote-Error("+e.getMessage()+").");
        }
    }

    public synchronized RemoteEncoderBytes addEntry_nocheck(String tag, InputStream content, long content_length) throws StorageSystemException {
        try {
            client.send_cause(RemoteEncoderMCNPCauses.ADD_ENTRY_BYTE_ARR_NOCHECK);
            client.send_variable(string_to_bytes(tag));
            client.send_variable_from(content_length, content);
            byte result = client.receive_byte();
            if(result != RemoteEncoderMCNPCauses.NO_ERROR) //supposed to indicate that no error occurred
                throw new StorageSystemException("Server said that add entry failed.");
        } catch (IOException ex) {
            throw new StorageSystemException("IO Exception thrown by provided InputStream("+ex.getMessage()+")");
        }
        return this;
    }


    @Override public Iterator<TaggedEntry<byte[]>> iterator() {
        throw new UnsupportedOperationException("iterator not supported within RBAE");
    }
    @Override public int hashCode() {
        return super.hashCode();
    }
    @Override public boolean equals(Object o) {
        return this == o;
    }
}