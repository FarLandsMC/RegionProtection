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
     * @param out the output stream.
     */
    public Encoder(OutputStream out) {
        this.out = out;
    }

    /**
     * Returns the output stream this encoder is writing to.
     * @return the output stream this encoder is writing to.
     */
    public OutputStream getOutputStream() {
        return out;
    }

    /**
     * Writes a byte to the output stream.
     * @param by the byte.
     * @throws IOException if and I/O error occurs.
     */
    public void write(int by) throws IOException {
        out.write(by);
    }

    /**
     * Writes many bytes to the output stream.
     * @param bytes the bytes.
     * @throws IOException if and I/O error occurs.
     */
    public void write(byte[] bytes) throws IOException {
        out.write(bytes);
    }

    /**
     * Writes the bytes as an array, preceded by its length.
     * @param a the byte array to wright.
     * @throws IOException if an I/O error occurs.
     */
    public void writeByteArray(byte[] a) throws IOException {
        writeInt(a.length);
        out.write(a);
    }

    /**
     * Writes a <code>short</code>, or 2-byte integer to the output stream.
     * @param s the <code>short</code>.
     * @throws IOException if and I/O error occurs.
     */
    public void writeShort(short s) throws IOException {
        out.write(s >>> 8);
        out.write(s & 0xFF);
    }

    /**
     * Writes an array of shorts, or 2-byte integers to the output stream.
     * @param a the array.
     * @throws IOException if and I/O error occurs.
     */
    public void writeShortArray(short[] a) throws IOException {
        writeInt(a.length);
        for(short s : a)
            writeShort(s);
    }

    /**
     * Writes a <code>short</code>, or 2-byte integer to the output stream.
     * @param i the integer, which will be down casted.
     * @throws IOException if and I/O error occurs.
     */
    public void writeShort(int i) throws IOException {
        out.write(i >>> 8);
        out.write(i & 0xFF);
    }

    /**
     * Writes an array of shorts, or 2-byte integers to the output stream.
     * @param a the array.
     * @throws IOException if and I/O error occurs.
     */
    public void writeShortArray(int[] a) throws IOException {
        writeInt(a.length);
        for(int s : a)
            writeShort(s);
    }

    /**
     * Writes an <code>int</code>, or 4-byte integer to the output stream.
     * @param i the integer.
     * @throws IOException if and I/O error occurs.
     */
    public void writeInt(int i) throws IOException {
        out.write(i >>> 24);
        out.write((i >> 16) & 0xFF);
        out.write((i >> 8) & 0xFF);
        out.write(i & 0xFF);
    }

    /**
     * Writes an array of ints, or 4-byte integers to the output stream.
     * @param a the array.
     * @throws IOException if and I/O error occurs.
     */
    public void writeIntArray(int[] a) throws IOException {
        writeInt(a.length);
        for(int i : a)
            writeInt(i);
    }

    /**
     * Writes a <code>long</code>, or 8-byte integer to the output stream.
     * @param l the <code>long</code>.
     * @throws IOException if and I/O error occurs.
     */
    public void writeLong(long l) throws IOException {
        out.write((int)(l >>> 56));
        out.write((int)((l >> 48) & 0xFF));
        out.write((int)((l >> 40) & 0xFF));
        out.write((int)((l >> 32) & 0xFF));
        out.write((int)((l >> 24) & 0xFF));
        out.write((int)((l >> 16) & 0xFF));
        out.write((int)((l >> 8) & 0xFF));
        out.write((int)(l & 0xFF));
    }

    /**
     * Writes an array of longs, or 8-byte integers to the output stream.
     * @param a the array.
     * @throws IOException if and I/O error occurs.
     */
    public void writeLongArray(long[] a) throws IOException {
        writeInt(a.length);
        for(long l : a)
            writeLong(l);
    }

    /**
     * Writes a <code>float</code> to the output stream according to the IEEE 754 floating-point "single format" bit
     * layout, preserving Not-a-Number (NaN) values.
     * @param f the <code>float</code>.
     * @throws IOException if and I/O error occurs.
     */
    public void writeFloat(float f) throws IOException {
        writeInt(Float.floatToRawIntBits(f));
    }

    /**
     * Writes an array of floats to the output stream according to the IEEE 754 floating-point "single format" bit
     * layout, preserving Not-a-Number (NaN) values.
     * @param a the array.
     * @throws IOException if and I/O error occurs.
     */
    public void writeFloatArray(float[] a) throws IOException {
        writeInt(a.length);
        for(float f : a)
            writeInt(Float.floatToRawIntBits(f));
    }

    /**
     * Writes a <code>double</code> to the output stream according to the IEEE 754 floating-point "double format" bit
     * layout, preserving Not-a-Number (NaN) values.
     * @param d the <code>double</code>.
     * @throws IOException if and I/O error occurs.
     */
    public void writeDouble(double d) throws IOException {
        writeLong(Double.doubleToRawLongBits(d));
    }

    /**
     * Writes an array of doubles to the output stream according to the IEEE 754 floating-point "double format" bit
     * layout, preserving Not-a-Number (NaN) values.
     * @param a the array.
     * @throws IOException if and I/O error occurs.
     */
    public void writeDoubleArray(double[] a) throws IOException {
        writeInt(a.length);
        for(double d : a)
            writeLong(Double.doubleToRawLongBits(d));
    }

    /**
     * Writes an unmodified UTF-8 string to the output stream.
     * @param s the string.
     * @throws IOException if an I/O error occur.
     */
    public void writeUTF8Raw(String s) throws IOException {
        byte[] raw = s.getBytes(StandardCharsets.UTF_8);
        writeInt(raw.length);
        out.write(raw);
    }

    /**
     * Writes an unmodified ASCII string to the output stream.
     * @param s the string.
     * @throws IOException if an I/O error occur.
     */
    public void writeASCIIRaw(String s) throws IOException {
        char[] cs = s.toCharArray();
        writeInt(cs.length);
        for(char c : cs)
            out.write(c & 0xFF);
    }

    /**
     * Writes a <code>UUID</code> to the output stream.
     * @param uuid the UUID.
     * @throws IOException if an I/O error occur.
     * @see java.util.UUID
     */
    public void writeUuid(UUID uuid) throws IOException {
        writeLong(uuid.getMostSignificantBits());
        writeLong(uuid.getLeastSignificantBits());
    }

    /**
     * Writes a generic array to the output stream, assuming the encoder provided is a method reference to this object.
     * @param a the array.
     * @param encoder the encoder method.
     * @param <T> the type of data being written.
     * @throws Exception and an exception occurs within the encoder.
     */
    public <T> void writeArray(T[] a, UnsafeConsumer<T, Exception> encoder) throws Exception {
        writeInt(a.length);
        for(T ele : a)
            encoder.accept(ele);
    }

    /**
     * Writes an array of the specified type to the output stream using uncompressed, default methods for the encoding.
     * @param a the array.
     * @param <T> the type of data being encoded.
     * @throws IOException if an I/O error occurs.
     */
    public <T> void writeArray(T[] a) throws IOException {
        writeInt(a.length);
        if(a.length == 0)
            return;
        Class<?> componentType = a.getClass().getComponentType();
        if(byte.class.equals(componentType) || Byte.class.equals(componentType)) {
            for(T ele : a)
                out.write((byte)ele);
        }else if(short.class.equals(componentType) || Short.class.equals(componentType)) {
            for(T ele : a)
                writeShort((short)ele);
        }else if(int.class.equals(componentType) || Integer.class.equals(componentType)) {
            for(T ele : a)
                writeInt((int)ele);
        }else if(long.class.equals(componentType) || Long.class.equals(componentType)) {
            for(T ele : a)
                writeLong((long)ele);
        }else if(float.class.equals(componentType) || Float.class.equals(componentType)) {
            for(T ele : a)
                writeFloat((float)ele);
        }else if(double.class.equals(componentType) || Double.class.equals(componentType)) {
            for(T ele : a)
                writeDouble((double)ele);
        }else if(String.class.equals(componentType)) {
            for(T ele : a)
                writeUTF8Raw((String)ele);
        }else if(UUID.class.equals(componentType)) {
            for(T ele : a)
                writeUuid((UUID)ele);
        }else if(byte[].class.equals(componentType)) {
            for(T ele : a) {
                writeInt(((byte[])ele).length);
                write((byte[])ele);
            }
        }else if(Byte[].class.equals(componentType)) {
            for(T ele : a) {
                writeInt(((Byte[])ele).length);
                for(Byte b : ((Byte[])ele))
                    out.write(b);
            }
        }
    }

    /**
     * Writes a collection to the output stream in the form of an array, assuming the encoder provided is a method
     * reference to this object.
     * @param c the collection.
     * @param encoder the encoder.
     * @param <T> the type of data being written.
     * @throws Exception if an exception occurs within the encoder.
     */
    @SuppressWarnings("unchecked")
    public <T> void writeArray(Collection<T> c, UnsafeConsumer<T, Exception> encoder) throws Exception {
        writeInt(c.size());
        for(T ele : c)
            encoder.accept(ele);
    }

    /**
     * Writes a collection to the output stream as an array using uncompressed, default methods for the encoding.
     * @param c the collection.
     * @param <T> the type of data being encoded.
     * @throws IOException if an I/O error occurs.
     */
    public <T> void writeArray(Collection<T> c) throws IOException {
        writeInt(c.size());
        writeArray(c.toArray());
    }

    /**
     * Flushes the output stream used by this encoder.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void flush() throws IOException {
        out.flush();
    }

    /**
     * Closes the output stream used by this encoder.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void close() throws IOException {
        out.close();
    }
}