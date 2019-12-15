package jokrey.utilities.bitsandbytes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Provides efficient helper functionality for some bit, byte and conversion operations.
 */
public class BitHelper {
    /**
     * Returns bit string for byte string
     * Highly inefficient regarding space complexity.
     * Only interesting for debug println outs.
     * @param bytes bytes
     * @return int[] containing only 1/0 and of length bytes.length*8
     */
    public static int[] getBits(byte[] bytes) {
        int[] bits = new int[bytes.length*8];
        for(int i=0;i<bytes.length;i++) {
            int[] bits_in_byte = getBits(bytes[i]);
			System.arraycopy(bits_in_byte, 0, bits, i * 8, bits_in_byte.length);
			//was::
//			for(int ii=0;ii<bits_in_byte.length;ii++) {
//				bits[i*8+ii] = bits_in_byte[ii];
//			}
        }
        return bits;
    }
    /**
     * Returns bit string for int
     * Highly inefficient regarding space complexity.
     * Only interesting for debug println outs.
     * @param integer
     * @return
     */
    public static int[] getBits(byte integer) {
        int[] bits = new int[8];
        for(int i=0;i<8;i++) {
            bits[i] = getBit(integer, 7-i);
        }
        return bits;
    }

    /** @see #getBits(byte) */
	public static int[] getBits(int integer) {
		int[] bits = new int[32];
		for(int i=0;i<32;i++) {
			bits[i] = getBit(integer, 31-i);
		}
		return bits;
	}
    /** @see #getBits(byte) */
	public static int[] getBits(long integer64) {
		int[] bits = new int[64];
		for(int i=0;i<64;i++) {
			bits[i] = getBit(integer64, 63-i);
		}
		return bits;
	}

    /**
     * @param n 8bit string - byte
     * @param k position
     * @return 0 or 1
     */
	public static int getBit(byte n, int k) {
		return (n >> k) & 1;
	}
    /** @see #getBit(byte, int) */
	public static int getBit(int n, int k) {
		return (n >> k) & 1;
	}
    /** @see #getBit(byte, int) */
    public static int getBit(long n, int k) {
        return (int) ((n >> k) & 1);
    }

    /**
     * @param n 8bit string - byte
     * @param k position
     * @return byte n with bit at position k set (1)
     */
	public static byte setBit(byte n, int k) {
		return (byte) (n | (1 << k));
	}
    /**
     * @param n 8bit string - byte
     * @param k position
     * @return byte n with bit at position k set (0)
     */
	public static byte unsetBit(byte n, int k) {
		return (byte) (n & ~(1 << k));
	}

    /**
     * Bits of length of return type
     * @param bits array of 0's and 1's
     * @return a byte
     */
    public static byte getByteFromBits(int[] bits) {
        byte integer = 0;
        for (int i = 0; i != bits.length;i++)
            if(bits[i]==1)
                integer^= (1 << (7-i));
        return integer;
    }
    /** @see #getByteFromBits(int[]) */
    public static int getIntFromBits(int[] bits) {
        int integer = 0;
        for (int i = 0; i != bits.length;i++)
            if(bits[i]==1)
                integer^= (1 << (31-i));
        return integer;
    }

    /** @see #getIntFromBits(int[]) */
	public static void printBitsFor(int integer) {
		int[] bits = getBits(integer);
	    for (int i = 0; i != bits.length;i++) {
	        if(i%4==0&&i!=0)System.out.print(" ");
	        System.out.print(bits[i]);
	    }
	    System.out.println();
	}
    /** @see #getIntFromBits(int[]) */
	public static void printBitsFor(int integer, int printRange_min, int printRange_max) {
		int[] bits = getBits(integer);
	    for (int i = Math.min(Math.max(printRange_min, 0), Math.min(bits.length-1, printRange_max)); i < Math.min(bits.length, printRange_max);i++) {
	        if(i%4==0&&i!=0)System.out.print(" ");
	        System.out.print(bits[i]);
	    }
	    System.out.println();
	}


	/**
	 * Bitweises negiertes oder unter Benutzung des negiertens und und
	 * Nach DeMorgan gilt: ~( x | y ) äquivalent zu  ~x & ~y
	 * @param x 32bit integer 1
	 * @param y 32bit integer 2
	 * @return Das Ergebnis der nor Operation
	 */
	public static int bitNor(int x, int y) {
		return ~x & ~y;
	}

	/**
	 * Bitweise negieren von xor, unter benutzung von negierung und oder
	 * 
	 * Hierzu das bekannte negierte oder: ~(x | y)
	 * dies ergibt das folgende diagram: 0,0-> 1 || 0,1->0 || 1,0->0|| 1,1->0
	 * Bei Xnor soll 1,1 allerdings 1 ergeben.
	 * Somit brauchen wir noch eine Verkettung die uns bei 1,1 eine 1 und sonst eine 0 gibt.
	 * Dazu hilft uns diese:
	 *    ~(~x | ~y)
	 *    Sie gibt nur genau bei 1,1 eine 1 zurück. 
	 * Nun können wir die beiden mit einem weiteren oder verketten. Der zweite Teil beinflusst das Ergebnis nur bei 1,1 was ja genau unser Problem war.
	 * Daher:
	 * @param x 32bit integer 1
	 * @param y 32bit integer 2
	 * @return Das Ergebnis der xnor Operation
	 */
	public static int bitXnor(int x, int y) {
		return ~(x | y) | ~(~x | ~y);
	}

	/**
	 * 1 byte besteht aus 8 bit
	 * Wir schieben den Integer um n*8 stellen nach rechts, damit die 8 bit die uns für unser gewähltes n'tes Byte interessant sind, ganz rechts stehen.
	 * n<<3 ist äquivalent zu * 8(byte length) für 0<=n<=3
	 * -> (x >> (n<<3))
	 * weniger signifikante bits als die für die wir uns interessieren verschwinden so bereits automatisch, aber es kann noch signifikantere bits geben.
	 * Die können wir mit einer simplen & Operation auf 0 setzen:
	 *
	 * (x >> (n<<3)) & 0x000000FF
	 * @param x 16bit integer
	 * @param n Zwischen 0 und 1
	 * @return unser gewähltes Byte -> eine zahl zwischen 0 und 255
	 */
	public static byte getByte(short x, int n) {
		return (byte) ((x >> (n<<3)) & 0x000000FF);
	}
	/** @see #getByte(short, int) */
	public static byte[] getBytes(short x) {
		byte[] bytes = new byte[2];
		for(int n=0;n<bytes.length;n++)
			bytes[n] = BitHelper.getByte(x, (bytes.length-1)-n);
		return bytes;
	}

	/**
	 * 1 byte besteht aus 8 bit
	 * Wir schieben den Integer um n*8 stellen nach rechts, damit die 8 bit die uns für unser gewähltes n'tes Byte interessant sind, ganz rechts stehen.
	 * n<<3 ist äquivalent zu * 8(byte length) für 0<=n<=3
	 * -> (x >> (n<<3))
	 * weniger signifikante bits als die für die wir uns interessieren verschwinden so bereits automatisch, aber es kann noch signifikantere bits geben.
	 * Die können wir mit einer simplen & Operation auf 0 setzen:
	 *
	 * (x >> (n<<3)) & 0x000000FF
	 * @param x 32bit integer
	 * @param n Zwischen 0 und 3
	 * @return unser gewähltes Byte -> eine zahl zwischen 0 und 255
	 */
	public static byte getByte(int x, int n) {
		return (byte) ((x >> (n<<3)) & 0x000000FF);
	}
    /** @see #getByte(int, int) */
	public static byte[] getBytes(int x) {
		byte[] bytes = new byte[4];
		for(int n=0;n<bytes.length;n++)
			bytes[n] = BitHelper.getByte(x, (bytes.length-1)-n);
		return bytes;
	}
	
	/**
	 * 1 byte besteht aus 8 bit
	 * Wir schieben den Integer um n*8 stellen nach rechts, damit die 8 bit die uns für unser gewähltes n'tes Byte interessant sind, ganz rechts stehen.
	 * n<<3 ist äquivalent zu * 8(byte length) für 0<=n<=3
	 * -> (x >> (n<<3))
	 * weniger signifikante bits als die für die wir uns interessieren verschwinden so bereits automatisch, aber es kann noch signifikantere bits geben.
	 * Die können wir mit einer simplen & Operation auf 0 setzen:
	 * 
	 * (x >> (n<<3)) & 0x000000FF
	 * @param x 32bit integer
	 * @param n Zwischen 0 und 3
	 * @return unser gewähltes Byte -> eine zahl zwischen 0 und 255
	 */
	public static byte getByte(long x, int n) {
		return (byte) ((x >> (n<<3)) & 0x000000FF);
	}
    /** @see #getByte(long, int) */
	public static byte[] getBytes(long x) {
		byte[] bytes = new byte[8];
		for(int n=0;n<bytes.length;n++)
			bytes[n] = BitHelper.getByte(x, (bytes.length-1)-n);
		return bytes;
	}

    /** With as few as possible. @see #getBytes(long) */
    public static byte[] getMinimalBytes(long x) {
        int byte_count = (int) Math.max(0, Math.ceil(Math.floor(log2(x) + 1) / 8));

        byte[] bytes = new byte[byte_count];
        for(int n=0;n<bytes.length;n++)
            bytes[n] = BitHelper.getByte(x, (bytes.length-1)-n);
        return bytes;
    }
    /** With exactly number. @see #getBytes(long) */
    public static byte[] getInNBytes(long x, int number) {
        byte[] bytes = new byte[number];
        writeInNBytes(bytes, 0, x, number);
        return bytes;
//        byte[] minimal_bytes = getMinimalBytes(x);
//        if(number == minimal_bytes.length)
//            return minimal_bytes;
//        else if(number > minimal_bytes.length) {
//            byte[] bytes = new byte[number];
//            System.arraycopy(minimal_bytes, 0, bytes, number-minimal_bytes.length, minimal_bytes.length);
//            return bytes;
//        } else { // number < minimal_bytes.length
//            byte[] bytes = new byte[number];
//            System.arraycopy(minimal_bytes, 0, bytes, 0, number);
//            return bytes;
//        }
    }

    public static void writeInNBytes(byte[] bytes, int offset, long value, int number) {
        for(int n=0;n<number;n++)
            bytes[offset + n] = BitHelper.getByte(value, (number-1)-n);
    }


    /** ld */
    public static double log2(double x) {
        return Math.log(x)/Math.log(2);
    }
    public static double ld(double x) {
        return Math.log(x)/Math.log(2);
    }

	/**
	 * Wir verschieben unsere bits bis n ans Ende und unsere bits ab n an den Anfang.
	 * Automatisch wird dabei der Rest mit 0en aufgefüllt.
	 * Diese 0en erlauben es uns mit einer oder Operation unsere bits darauf zu "addieren".
	 * 
	 * @param x 32 bit integer
	 * @param n Zahl der Stellen um die nach Rechts verschoben werden soll >=0 und <=31
	 * @return unsere Verschobene Zahl
	 */
	public static int rotateRight(int x, int n) {
		return x >> n | x << (32-n);
	}

	/**
	 * Integer sind im Zweierkomplement.
	 * Eine Zahl im Zweierkomplement kann invertiert werden indem jede Zahl invertiert und eine 1 hinzugefügt wird
	 * Die Invertierung einer Binärzahl lässt sich mit xor 1 realisieren.
	 *        Wenn unsere Zahl 0 ist wird es 1, wenn sie eins ist wird es 0
	 * Die Addierung findet ganz normal statt.
	 * 
	 * Nun müssen wir also einen Weg ohne if finden in dem auf eine Positive Zahl automatisch  xor 0 und auf eine negative xor 1 angewendet wird.
	 * Dazu der shift Operator:
	 * >> behält das Vorzeichen bei und schiebt(verringert) die Zahl um n Zweierpotenzen, bis zu ihrer niedrigsten Position(0 wird von mir mal positiv interpretiert)
	 * Wenn man um 31 Stellen schiebt erhalten wird bei einer positiven Zahl somit nur 0en(entspricht einer 0) und bei einer negativen nur 1en(entspricht einer -1)
	 * Daher erhalten wir den ersten Teil unserer Operation:
	 * 
	 * x ^ (x >> 31)
	 * 
	 * Nun fehlt noch die Addition mit einer 0 bei einer positiven Zahl und die Addition mit 1 bei einer negativen
	 * Eine Zahl im Zweierkomplement ist genau dann negativ wenn das Most Signifikant Bit 1 ist.
	 * Also schieben wir unsere Zahl um 31 Stellen nach rechts und füllen mit 0en auf. Das msb steht dann alleine(nur 0en vorne weg), an erster Stelle.
	 *     Bei einer Positiven Zahl ergibt das eine 0, bei einer negativen eine 1.
	 * 
	 * Das einzige Problem ist nur das Zweierkomplemente, eine negative Zahl mehr haben.
	 * Wenn wir also die kleinst mögliche Zahl (10000000000000000000000000000000)2 eingeben addieren wir nach invertierung auf die (11111111111111111111111111111111)2 noch eine 1 und es kommt zum Überlauf.
	 * Das Ergebnis für die niedrigste Zahl ist somit die Zahl selbst
	 * @param x einen int
	 * @return einen positiven int der gleichen Entferung zu 0
	 */
	public static int abs(int x) {
		return x | (x >> 31) + (x >>> 31);
	}


    /**
     * Returns the raw byte representation of a float. (for example interesting for efficient storage or network transfer)
     * @param f a float
     * @return byte array of length 4
     */
	public static byte[] getBytes(float f) {
		return getBytes(Float.floatToRawIntBits(f));
	}
    /**
     * Returns the raw byte representation of a double. (for example interesting for efficient storage or network transfer)
     * @param d a double
     * @return byte array of length 8
     */
	public static byte[] getBytes(double d) {
		return getBytes(Double.doubleToRawLongBits(d));
	}

    /**
     * Turns a byte string into it's representation as a short.
     * @param bytes has to be length 2
     * @return short represented by bytes
     * @throws IllegalArgumentException for a byte array of length != 2
     */
	public static short getInt16From(byte[] bytes) {
		if(bytes.length==2) {
			return ByteBuffer.wrap(bytes).getShort();
		}throw new IllegalArgumentException("read error, n!=2");
	}

    /**
     * Turns a byte string into it's representation as an int.
     * @param bytes a byte array exactly length 4
     * @return an int
     */
    public static int getInt32From(byte[] bytes) {
        if(bytes.length==4) {
			return getInt32From(bytes, 0);
		}throw new IllegalArgumentException("read error, n!=4");
    }
    /** BigEndian */
    public static int getInt32From(byte[] bytes, int off) {
        return  ((bytes[off  ]       ) << 24) |
                ((bytes[off+1] & 0xff) << 16) |
                ((bytes[off+2] & 0xff) <<  8) |
                ((bytes[off+3] & 0xff)      );
    }

    public static long getIntFromNBytes(byte[] bytes, int offset, int len) {
        long l = 0;
        for(int i=0;i<len;i++) {
            if(i==0)
                l |= (bytes[offset + i]) << (len - i - 1) * 8;
            else
                l |= (bytes[offset + i] & 0xff) << (len - i - 1) * 8;
        }
        return l;
//        return  ((bytes[off  ]       ) << 24) |
//                ((bytes[off+1] & 0xff) << 16) |
//                ((bytes[off+2] & 0xff) <<  8) |
//                ((bytes[off+3] & 0xff)      );
    }

	public static long getUIntFromNBytes(byte[] b, int offset, int len) {
		long l = 0;
		for(int i=0;i<len;i++) {
			l |= b[offset + i] & 0xFF;
			if(i+1!=len)
				l <<= 8;
		}
//		l |= b[offset    ] & 0xFF;
//		l <<= 8;
//		l |= b[offset + 1] & 0xFF;
//		l <<= 8;
//		l |= b[offset + 2] & 0xFF;
//		l <<= 8;
//		l |= b[offset + 3] & 0xFF;
		return l;
	}

    /**
     * Turns a byte string into it's representation as a long.
     * @param bytes a byte array exactly length 8
     * @return a long
     */
	public static long getInt64From(byte[] bytes) {
		if(bytes.length==8) {
			return ByteBuffer.wrap(bytes).getLong();// big-endian by default
		}
		throw new IllegalArgumentException("read error, n!=8");
	}

    /**
     * Turns a byte string into it's representation as a float.
     * @param bytes a byte array exactly length 4
     * @return a double
     */
	public static float getFloat32From(byte[] bytes) {
		return Float.intBitsToFloat(getInt32From(bytes));
	}

    /**
     * Turns a byte string into it's representation as a double.
     * @param bytes a byte array exactly length 8
     * @return a double
     */
	public static double getFloat64From(byte[] bytes) {
		return Double.longBitsToDouble(getInt64From(bytes));
	}

    /**
     * Turns a byte string into it's representation as a number (big endian).
     * if the buffer is too big or too small it will be silently compensated
     * @param bytes a byte array
     * @return a long
     */
    public static long getIntFrom(byte[] bytes) {
        if(bytes.length == 8) {
            return ByteBuffer.wrap(bytes).getLong();// big-endian by default
        } else if(bytes.length < 8) {
            byte[] morebytes = new byte[8];
            System.arraycopy(bytes, 0, morebytes, morebytes.length-bytes.length, bytes.length);
            return getIntFrom(morebytes);
        } else {
            byte[] lessbytes = new byte[8];
            System.arraycopy(bytes, bytes.length-lessbytes.length, lessbytes, 0, lessbytes.length);
            return getIntFrom(lessbytes);
        }
    }

    public static void writeInt32(byte[] bytes, int offset, int value) {
        for(int n=0;n<4;n++)
            bytes[offset + n] = BitHelper.getByte(value, (4-1)-n);
    }

    /** @return bytes in hex format */
    public static String getHexString(byte[] bytes) {
        StringBuilder ashex = new StringBuilder();
        for (byte b:bytes)
            ashex.append(String.format("%02X", b));
        return ashex.toString();
    }
    /** @see #getBits(byte[]) */
    public static String getBitsString(byte[] bytes) {
        StringBuilder asbits = new StringBuilder();
        for(int i : BitHelper.getBits(bytes))
            asbits.append(i);
        return asbits.toString();
    }


    /**
     * Uses 4096 as buffer_length
     * @see #toByteArray(InputStream, int)
     */
    public static byte[] toByteArray(InputStream in) throws IOException {
        return toByteArray(in, 4096);
    }
	/**
	 * Will fully read (until read returns -1) 'in' and return read contents as byte array.
     * If stream returns too many bytes this will throw an OutOfMemoryError
	 * @param input stream
     * @param buffer_length length of buffer - certain sizes are much better than other. Typically you should stick to buffer_length=2^n.
	 * @return stream contents
	 * @throws IOException only from input stream
     * @throws OutOfMemoryError if input stream provides too many bytes
	 */
	public static byte[] toByteArray(InputStream input, int buffer_length) throws IOException {
		ByteArrayOutputStream os = new ByteArrayOutputStream();

		int read;
        byte[] buffer = new byte[buffer_length];
		while ((read = input.read(buffer)) != -1)
			os.write(buffer, 0, read);

		return os.toByteArray();
	}



    public static byte[] getUInt32Bytes(long x) {
        byte[] bytes = new byte[4];
        writeUInt32Bytes(bytes, 0, x);
        return bytes;
    }
    public static void writeUInt32Bytes(byte[] b, int off, long x) {
        if(x > Integer.MAX_VALUE*2L+1 || x < 0)
            throw new IllegalArgumentException("Provided long("+x+") does not represent an uint32");
        b[off] = (byte) (x);
        b[off+1] = (byte) (x >> 8);
        b[off+2] = (byte) (x >> 16);
        b[off+3] = (byte) (x >> 24);
    }


    public static long getUInt32(byte[] b) {
        return getUInt32(b, 0);
    }
    public static long getUInt32(byte[] b, int offset) {
        long l = 0;
        l |= b[offset    ] & 0xFF;
        l <<= 8;
        l |= b[offset + 1] & 0xFF;
        l <<= 8;
        l |= b[offset + 2] & 0xFF;
        l <<= 8;
        l |= b[offset + 3] & 0xFF;
        return l;
    }
}