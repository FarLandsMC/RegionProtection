package com.kicas.rp.util;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.UUID;

/**
 * This class writes various data types to an output stream in the form of bytes.
 */
public class Encoder implements Flushable, Closeable {
    /**
     * The output stream to write to.
     */
    private final OutputStream out;

    /**
     * Constructs a new encoder with a specified output stream, and defaults to not compressing length measurements.
     *
     * @param out the output stream.
     */
    public Encoder(OutputStream out) {
        this.out = out;
    }

    /**
     * Returns the output stream this encoder is writing to.
     *
     * @return the output stream this encoder is writing to.
     */
    public OutputStream getOutputStream() {
        return out;
    }

    /**
     * Writes a byte to the output stream.
     *
     * @param by the byte.
     * @throws IOException if an I/O error occurs.
     */
    public void write(int by) throws IOException {
        out.write(by);
    }

    /**
     * Writes many bytes to the output stream.
     *
     * @param bytes the bytes.
     * @throws IOException if an I/O error occurs.
     */
    public void write(byte[] bytes) throws IOException {
        out.write(bytes);
    }

    /**
     * Writes the bytes as an array, preceded by its length.
     *
     * @param a the byte array to wright.
     * @throws IOException if an I/O error occurs.
     */
    public void writeByteArray(byte[] a) throws IOException {
        writeCompressedUint(a.length);
        out.write(a);
    }

    /**
     * Writes a boolean to the output stream.
     *
     * @param b the boolean.
     * @throws IOException if an I/O error occurs.
     */
    public void writeBoolean(boolean b) throws IOException {
        out.write(b ? 1 : 0);
    }

    /**
     * Writes a <code>short</code>, or 2-byte integer to the output stream.
     *
     * @param s the <code>short</code>.
     * @throws IOException if an I/O error occurs.
     */
    public void writeShort(short s) throws IOException {
        out.write(s >>> 8);
        out.write(s & 0xFF);
    }

    /**
     * Writes an array of shorts, or 2-byte integers to the output stream.
     *
     * @param a the array.
     * @throws IOException if an I/O error occurs.
     */
    public void writeShortArray(short[] a) throws IOException {
        writeCompressedUint(a.length);
        for (short s : a)
            writeShort(s);
    }

    /**
     * Writes a <code>short</code>, or 2-byte integer to the output stream.
     *
     * @param i the integer, which will be down casted.
     * @throws IOException if an I/O error occurs.
     */
    public void writeShort(int i) throws IOException {
        out.write(i >>> 8);
        out.write(i & 0xFF);
    }

    /**
     * Writes an array of shorts, or 2-byte integers to the output stream.
     *
     * @param a the array.
     * @throws IOException if an I/O error occurs.
     */
    public void writeShortArray(int[] a) throws IOException {
        writeCompressedUint(a.length);
        for (int s : a)
            writeShort(s);
    }

    /**
     * Writes an <code>int</code>, or 4-byte integer to the output stream.
     *
     * @param i the integer.
     * @throws IOException if an I/O error occurs.
     */
    public void writeInt(int i) throws IOException {
        out.write(i >>> 24);
        out.write((i >> 16) & 0xFF);
        out.write((i >> 8) & 0xFF);
        out.write(i & 0xFF);
    }

    /**
     * Writes an array of ints, or 4-byte integers to the output stream.
     *
     * @param a the array.
     * @throws IOException if an I/O error occurs.
     */
    public void writeIntArray(int[] a) throws IOException {
        writeCompressedUint(a.length);
        for (int i : a)
            writeInt(i);
    }

    /**
     * Writes a <code>long</code>, or 8-byte integer to the output stream.
     *
     * @param l the <code>long</code>.
     * @throws IOException if an I/O error occurs.
     */
    public void writeLong(long l) throws IOException {
        out.write((int) (l >>> 56));
        out.write((int) ((l >> 48) & 0xFF));
        out.write((int) ((l >> 40) & 0xFF));
        out.write((int) ((l >> 32) & 0xFF));
        out.write((int) ((l >> 24) & 0xFF));
        out.write((int) ((l >> 16) & 0xFF));
        out.write((int) ((l >> 8) & 0xFF));
        out.write((int) (l & 0xFF));
    }

    /**
     * Writes an array of longs, or 8-byte integers to the output stream.
     *
     * @param a the array.
     * @throws IOException if an I/O error occurs.
     */
    public void writeLongArray(long[] a) throws IOException {
        writeCompressedUint(a.length);
        for (long l : a)
            writeLong(l);
    }

    /**
     * Writes a compressed length (unsigned integer) to the output stream. This algorithm precedes the bytes
     * representing the length with various markers dictating how long the number is, allowing the length to take up
     * less space if its numerical value is relatively small. The length will be mapped like so:
     * <ul>
     * <li>i &lt; 64 -&gt; 1 byte</li>
     * <li>i &lt; 16,384 -&gt; 2 bytes</li>
     * <li>i &lt; 4,194,304 -&gt; 3 bytes</li>
     * <li>else -&gt; 4 bytes</li>
     * </ul>
     *
     * @param i the length to write.
     * @throws IOException If an I/O error occurs.
     */
    public void writeCompressedUint(int i) throws IOException {
        /* Format of first byte in sequence (x is an unknown bit)
         * length   format
         * 1 byte:  00xxxxxx
         * 2 bytes: 01xxxxxx
         * 3 bytes: 10xxxxxx
         * 4 bytes: 11xxxxxx */
        if (i < 0x40)
            out.write(i);
        else if (i < 0x4000) {
            out.write(0x40 | i >>> 8);
            out.write(i & 0xFF);
        } else if (i < 0x400000) {
            out.write(0x80 | i >>> 16);
            out.write(i >>> 8 & 0xFF);
            out.write(i & 0xFF);
        } else {
            out.write(0xC0 | i >>> 24);
            out.write(i >>> 16 & 0xFF);
            out.write(i >>> 8 & 0xFF);
            out.write(i & 0xFF);
        }
    }

    public void writeCompressedInt(int i) throws IOException {
        int absi = i < 0 ? ~i : i;
        int sb = i < 0 ? 0x20 : 0;
        if (absi < 0x20) {
            out.write(sb | absi & 0x1F);
        }else if (absi < 0x2000) {
            out.write(0x40 | sb | absi >>> 8);
            out.write(absi & 0xFF);
        } else if (absi < 0x200000) {
            out.write(0x80 | sb | absi >>> 16 & 0x1F);
            out.write(absi >> 8 & 0xFF);
            out.write(absi & 0xFF);
        } else {
            out.write(0xC0 | sb | absi >>> 24 & 0x1F);
            out.write(absi >> 16 & 0xFF);
            out.write(absi >> 8 & 0xFF);
            out.write(absi & 0xFF);
        }
    }

    /**
     * Writes a <code>float</code> to the output stream according to the IEEE 754 floating-point "single format" bit
     * layout, preserving Not-a-Number (NaN) values.
     *
     * @param f the <code>float</code>.
     * @throws IOException if an I/O error occurs.
     */
    public void writeFloat(float f) throws IOException {
        writeInt(Float.floatToRawIntBits(f));
    }

    /**
     * Writes an array of floats to the output stream according to the IEEE 754 floating-point "single format" bit
     * layout, preserving Not-a-Number (NaN) values.
     *
     * @param a the array.
     * @throws IOException if an I/O error occurs.
     */
    public void writeFloatArray(float[] a) throws IOException {
        writeCompressedUint(a.length);
        for (float f : a)
            writeInt(Float.floatToRawIntBits(f));
    }

    /**
     * Writes a <code>double</code> to the output stream according to the IEEE 754 floating-point "double format" bit
     * layout, preserving Not-a-Number (NaN) values.
     *
     * @param d the <code>double</code>.
     * @throws IOException if an I/O error occurs.
     */
    public void writeDouble(double d) throws IOException {
        writeLong(Double.doubleToRawLongBits(d));
    }

    /**
     * Writes an array of doubles to the output stream according to the IEEE 754 floating-point "double format" bit
     * layout, preserving Not-a-Number (NaN) values.
     *
     * @param a the array.
     * @throws IOException if an I/O error occurs.
     */
    public void writeDoubleArray(double[] a) throws IOException {
        writeCompressedUint(a.length);
        for (double d : a)
            writeLong(Double.doubleToRawLongBits(d));
    }

    /**
     * Writes an unmodified UTF-8 string to the output stream.
     *
     * @param s the string.
     * @throws IOException if an I/O error occur.
     */
    public void writeUTF8Raw(String s) throws IOException {
        if (s.isEmpty())
            out.write(0);
        else {
            byte[] raw = s.getBytes(StandardCharsets.UTF_8);
            writeCompressedUint(raw.length);
            out.write(raw);
        }
    }

    /**
     * Writes an unmodified ASCII string to the output stream.
     *
     * @param s the string.
     * @throws IOException if an I/O error occur.
     */
    public void writeASCIIRaw(String s) throws IOException {
        if (s.isEmpty())
            out.write(0);
        else {
            char[] cs = s.toCharArray();
            writeCompressedUint(cs.length);
            for (char c : cs)
                out.write(c & 0xFF);
        }
    }

    /**
     * Writes a <code>UUID</code> to the output stream.
     *
     * @param uuid the UUID.
     * @throws IOException if an I/O error occur.
     * @see java.util.UUID
     */
    public void writeUuid(UUID uuid) throws IOException {
        writeLong(uuid.getMostSignificantBits());
        writeLong(uuid.getLeastSignificantBits());
    }

    /**
     * Writes an array of the specified type to the output stream using uncompressed, default methods for the encoding.
     *
     * @param a             the array.
     * @param componentType the component type of the array.
     * @throws IOException if an I/O error occurs.
     */
    public void writeArray(Object[] a, Class<?> componentType) throws IOException {
        writeCompressedUint(a.length);
        if (a.length == 0)
            return;
        if (byte.class.equals(componentType) || Byte.class.equals(componentType)) {
            for (Object ele : a)
                out.write((byte) ele);
        } else if (short.class.equals(componentType) || Short.class.equals(componentType)) {
            for (Object ele : a)
                writeShort((short) ele);
        } else if (int.class.equals(componentType) || Integer.class.equals(componentType)) {
            for (Object ele : a)
                writeInt((int) ele);
        } else if (long.class.equals(componentType) || Long.class.equals(componentType)) {
            for (Object ele : a)
                writeLong((long) ele);
        } else if (float.class.equals(componentType) || Float.class.equals(componentType)) {
            for (Object ele : a)
                writeFloat((float) ele);
        } else if (double.class.equals(componentType) || Double.class.equals(componentType)) {
            for (Object ele : a)
                writeDouble((double) ele);
        } else if (String.class.equals(componentType)) {
            for (Object ele : a)
                writeUTF8Raw((String) ele);
        } else if (UUID.class.equals(componentType)) {
            for (Object ele : a)
                writeUuid((UUID) ele);
        } else if (byte[].class.equals(componentType)) {
            for (Object ele : a) {
                writeInt(((byte[]) ele).length);
                write((byte[]) ele);
            }
        } else if (Byte[].class.equals(componentType)) {
            for (Object ele : a) {
                writeInt(((Byte[]) ele).length);
                for (Byte b : ((Byte[]) ele))
                    out.write(b);
            }
        }
    }

    /**
     * Writes a collection to the output stream as an array using uncompressed, default methods for the encoding.
     *
     * @param c             the collection.
     * @param componentType the component type of the collection.
     * @param <T>           the type of data being encoded.
     * @throws IOException if an I/O error occurs.
     */
    public <T> void writeArray(Collection<T> c, Class<T> componentType) throws IOException {
        writeArray(c.toArray(), componentType);
    }

    /**
     * Flushes the output stream used by this encoder.
     *
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void flush() throws IOException {
        out.flush();
    }

    /**
     * Closes the output stream used by this encoder.
     *
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void close() throws IOException {
        out.close();
    }
}
