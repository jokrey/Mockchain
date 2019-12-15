package jokrey.utilities.encoder;

import jokrey.utilities.encoder.tag_based.TagBasedEncoderBytes;
import jokrey.utilities.encoder.tag_based.implementation.paired.length_indicator.bytes.LITagBytesEncoder;
import jokrey.utilities.transparent_storage.bytes.file.FileStorage;

import java.io.File;
import java.io.FileNotFoundException;

public class Creator {
    public static TagBasedEncoderBytes liTagOnFile(File f) throws FileNotFoundException {
        return new LITagBytesEncoder(new FileStorage(f));
    }
}
