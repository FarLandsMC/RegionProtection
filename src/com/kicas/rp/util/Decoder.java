package com.kicas.rp.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;

/**
 * This class reads various types of data from an input stream.
 */
public class Decoder implements Closeable {
    /**
     * The input stream.
     */
    private final InputStream in;

    /**
     * Constructs a new decoder with a specified input stream that defaults to not decompressing lengths.
     *
     * @param in the input stream.
     */
    public Decoder(InputStream in) {
        this.in = in;
    }

    /**
     * Returns the input stream this decoder is using.
     *
     * @return the input stream this decoder is using.
     */
    public InputStream getInputStream() {
        return in;
    }

    /**
     * Reads and returns the next byte in the stream.
     *
     * @return the next byte in the stream.
     * @throws IOException if an I/O error occurs.
     */
    public int read() throws IOException {
        return in.read();
    }

    /**
     * Reads a number of bytes into a specified destination array at a specified starting index.
     *
     * @param dest       the destination array.
     * @param startIndex the start index.
     * @param len        the number of bytes the read.
     * @return the actual number of bytes read.
     * @throws IOException if an I/O error occurs.
     */
    public int read(byte[] dest, int startIndex, int len) throws IOException {
        return in.read(dest, startIndex, len);
    }

    /**
     * Reads an array of bytes from the input stream.
     *
     * @return the next byte array in the input stream
     * @throws IOException if an I/O error occurs.
     */
    public byte[] readByteArray() throws IOException {
        int len = readCompressedUint();
        byte[] a = new byte[len];
        read(a, 0, len);
        return a;
    }

    /**
     * Reads a boolean from the input stream.
     *
     * @return the next boolean in the input stream.
     * @throws IOException if an I/O error occurs.
     */
    public boolean readBoolean() throws IOException {
        return in.read() == 1;
    }

    /**
     * Reads a <code>short</code>, or 2-byte integer from the input stream.
     *
     * @return the next <code>short</code> in the input stream.
     * @throws IOException if an I/O error occurs.
     */
    public short readShort() throws IOException {
        return (short) ((in.read() << 8) | in.read());
    }

    /**
     * Reads an array of shorts, or 2-byte integers from the input stream.
     *
     * @return the array of shorts.
     * @throws IOException if an I/O error occurs.
     */
    public short[] readShortArray() throws IOException {
        short[] a = new short[readCompressedUint()];
        for (int i = 0; i < a.length; ++i)
            a[i] = readShort();
        return a;
    }

    /**
     * Reads an <code>int</code>, or 4-byte integer from the input stream.
     *
     * @return the next <code>int</code> in the input stream.
     * @throws IOException if an I/O error occurs.
     */
    public int readInt() throws IOException {
        return (in.read() << 24) | (in.read() << 16) | (in.read() << 8) | in.read();
    }

    /**
     * Reads an array of ints, or 4-byte integers from the input stream.
     *
     * @return the array of ints.
     * @throws IOException if an I/O error occurs.
     */
    public int[] readIntArray() throws IOException {
        int[] a = new int[readCompressedUint()];
        for (int i = 0; i < a.length; ++i)
            a[i] = readInt();
        return a;
    }

    /**
     * Reads a <code>long</code>, or 8-byte integer from the input stream.
     *
     * @return the next <code>long</code> in the input stream.
     * @throws IOException if an I/O error occurs.
     */
    public long readLong() throws IOException {
        return ((long) in.read()) << 56 | ((long) in.read()) << 48 | ((long) in.read()) << 40 |
                ((long) in.read()) << 32 | ((long) in.read()) << 24 | ((long) in.read()) << 16 |
                ((long) in.read()) << 8 | ((long) in.read());
    }

    /**
     * Reads an array of longs, or 8-byte integers from the input stream.
     *
     * @return the next array of longs.
     * @throws IOException if an I/O error occurs.
     */
    public long[] readLongArray() throws IOException {
        long[] a = new long[readCompressedUint()];
        for (int i = 0; i < a.length; ++i)
            a[i] = readLong();
        return a;
    }

    /**
     * Decompresses an unsigned integer as encoded by the <code>writeCompressedUint</code> method in the encoder class.
     *
     * @return the decoded unsigned integer.
     * @throws IOException If an I/O error occurs.
     */
    public int readCompressedUint() throws IOException {
        int b0 = in.read();
        switch (b0 & 0xC0) {
            case 0x0:
                return b0 & 0x3F;
            case 0x40:
                return ((b0 & 0x3F) << 8) | in.read();
            case 0x80:
                return (b0 & 0x3F) << 16 | in.read() << 8 | in.read();
            case 0xC0:
                return (b0 & 0x3F) << 24 | in.read() << 16 | in.read() << 8 | in.read();
            default:
                return 0;
        }
    }

    /**
     * Decompresses a signed integer from the input stream as encoded by the <code>writeCompressedInt</code> method in
     * the encoder class.
     *
     * @return the decoded integer.
     * @throws IOException if an I/O error occurs.
     */
    public int readCompressedInt() throws IOException {
        int b0 = in.read();
        int val;
        switch (b0 & 0xC0) {
            case 0x0:
                val = b0 & 0x1F;
                break;
            case 0x40:
                val = (b0 & 0x1F) << 8 | in.read();
                break;
            case 0x80:
                val = (b0 & 0x1F) << 16 | in.read() << 8 | in.read();
                break;
            case 0xC0:
                val = (b0 & 0x1F) << 24 | in.read() << 16 | in.read() << 8 | in.read();
                break;
            default:
                return 0;
        }
        return (b0 & 0x20) != 0 ? ~val : val;
    }

    /**
     * Reads a <code>float</code> from the input stream according to the IEEE 754 floating-point "single format" bit
     * layout, preserving Not-a-Number (NaN) values.
     *
     * @return the next <code>float</code> in the input stream.
     * @throws IOException if an I/O error occurs.
     */
    public float readFloat() throws IOException {
        return Float.intBitsToFloat(readInt());
    }

    /**
     * Reads an array of floats according to the IEEE 754 floating-point "single format" bit layout, preserving
     * Not-a-Number (NaN) values.
     *
     * @return the next array of floats in the input stream.
     * @throws IOException if an I/O error occurs.
     */
    public float[] readFloatArray() throws IOException {
        float[] a = new float[readCompressedUint()];
        for (int i = 0; i < a.length; ++i)
            a[i] = Float.intBitsToFloat(readInt());
        return a;
    }

    /**
     * Reads a <code>double</code> from the input stream according to the IEEE 754 floating-point "double format" bit
     * layout, preserving Not-a-Number (NaN) values.
     *
     * @return the next <code>double</code> in the input stream.
     * @throws IOException if an I/O error occurs.
     */
    public double readDouble() throws IOException {
        return Double.longBitsToDouble(readLong());
    }

    /**
     * Reads an array of doubles according to the IEEE 754 floating-point "double format" bit layout, preserving
     * Not-a-Number (NaN) values.
     *
     * @return the next array of doubles in the input stream.
     * @throws IOException if an I/O error occurs.
     */
    public double[] readDoubleArray() throws IOException {
        double[] a = new double[readCompressedUint()];
        for (int i = 0; i < a.length; ++i)
            a[i] = Double.longBitsToDouble(readLong());
        return a;
    }

    /**
     * Reads a raw UTF-8 string from the input stream.
     *
     * @return the next raw UTF-8 string.
     * @throws IOException if an I/O error occurs.
     */
    public String readUTF8Raw() throws IOException {
        byte[] buffer = new byte[readCompressedUint()];
        if (buffer.length == 0)
            return "";
        int read = read(buffer, 0, buffer.length);
        return new String(buffer, 0, read, StandardCharsets.UTF_8);
    }

    /**
     * Reads a raw ASCII string from the input stream.
     *
     * @return the next ASCII string in the stream.
     * @throws IOException if an I/O error occurs.
     */
    public String readASCIIRaw() throws IOException {
        byte[] buffer = new byte[readCompressedUint()];
        if (buffer.length == 0)
            return "";
        int read = read(buffer, 0, buffer.length);
        return new String(buffer, 0, read, StandardCharsets.US_ASCII);
    }

    /**
     * Reads a UUID from the input stream.
     *
     * @return the next UUID in the input stream.
     * @throws IOException if an I/O error occurs.
     * @see java.util.UUID
     */
    public UUID readUuid() throws IOException {
        return new UUID(readLong(), readLong());
    }

    /**
     * Reads an array of the specified type from the input stream using default decoders.
     *
     * @param dest the destination array, which can be of size 0.
     * @param <T>  the type of data being decoded.
     * @return the next array of the given type in the input stream.
     * @throws IOException if an I/O error occurs.
     */
    @SuppressWarnings("unchecked")
    public <T> T[] readArray(T[] dest) throws IOException {
        return readArrayAsList((Class<T>) dest.getClass().getComponentType()).toArray(dest);
    }

    /**
     * Reads an array of the given type as a list from the input stream using default decoders.
     *
     * @param clazz the class of the data being decoded.
     * @param <T>   the type of data being decoded.
     * @return the next array of the given type in the input stream as a list.
     * @throws IOException if an I/O error occurs.
     */
    public <T> ArrayList<T> readArrayAsList(Class<T> clazz) throws IOException {
        int len = readCompressedUint();
        if (len == 0)
            return new ArrayList<>();
        ArrayList<T> list = new ArrayList<>(len);
        if (byte.class.equals(clazz) || Byte.class.equals(clazz)) {
            while (len > 0) {
                list.add(clazz.cast(read()));
                --len;
            }
        } else if (short.class.equals(clazz) || Short.class.equals(clazz)) {
            while (len > 0) {
                list.add(clazz.cast(readShort()));
                --len;
            }
        } else if (int.class.equals(clazz) || Integer.class.equals(clazz)) {
            while (len > 0) {
                list.add(clazz.cast(readInt()));
                --len;
            }
        } else if (long.class.equals(clazz) || Long.class.equals(clazz)) {
            while (len > 0) {
                list.add(clazz.cast(readLong()));
                --len;
            }
        } else if (float.class.equals(clazz) || Float.class.equals(clazz)) {
            while (len > 0) {
                list.add(clazz.cast(readFloat()));
                --len;
            }
        } else if (double.class.equals(clazz) || Double.class.equals(clazz)) {
            while (len > 0) {
                list.add(clazz.cast(readDouble()));
                --len;
            }
        } else if (String.class.equals(clazz)) {
            while (len > 0) {
                list.add(clazz.cast(readUTF8Raw()));
                --len;
            }
        } else if (UUID.class.equals(clazz)) {
            while (len > 0) {
                list.add(clazz.cast(readUuid()));
                --len;
            }
        } else if (byte[].class.equals(clazz)) {
            while (len > 0) {
                list.add(clazz.cast(readByteArray()));
                --len;
            }
        } else if (Byte[].class.equals(clazz)) {
            while (len > 0) {
                Byte[] subA = new Byte[readInt()];
                for (int i = 0; i < subA.length; ++i)
                    subA[i] = (byte) read();
                list.add(clazz.cast(subA));
                --len;
            }
        }
        return list;
    }

    /**
     * Closes the input stream used by this encoder.
     *
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void close() throws IOException {
        in.close();
    }
}
