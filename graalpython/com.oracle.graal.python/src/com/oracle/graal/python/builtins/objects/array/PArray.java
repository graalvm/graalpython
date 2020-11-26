/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.builtins.objects.array;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.BufferError;

import java.nio.ByteOrder;
import java.util.Arrays;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.util.BufferFormat;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.object.Shape;

// TODO interop library
@ExportLibrary(PythonObjectLibrary.class)
public final class PArray extends PythonBuiltinObject {
    private BufferFormat format;
    private String formatStr;
    private int length;
    private byte[] buffer;
    private volatile int exports;

    public PArray(Object clazz, Shape instanceShape, String formatStr, BufferFormat format) {
        super(clazz, instanceShape);
        this.formatStr = formatStr;
        this.format = format;
        this.length = 0;
        this.buffer = new byte[0];
    }

    public PArray(Object clazz, Shape instanceShape, String formatStr, BufferFormat format, int length) throws OverflowException {
        super(clazz, instanceShape);
        this.formatStr = formatStr;
        this.format = format;
        this.length = length;
        this.buffer = new byte[PythonUtils.multiplyExact(length, format.bytesize)];
    }

    public BufferFormat getFormat() {
        return format;
    }

    public String getFormatStr() {
        return formatStr;
    }

    public byte[] getBuffer() {
        return buffer;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        assert length >= 0;
        this.length = length;
    }

    public int getExports() {
        return exports;
    }

    public void setExports(int exports) {
        this.exports = exports;
    }

    public void checkCanResize(PythonBuiltinBaseNode node) {
        if (exports != 0) {
            throw node.raise(BufferError, ErrorMessages.EXPORTS_CANNOT_RESIZE);
        }
    }

    private int computeNewSize(int newLength, int itemsize) throws OverflowException {
        int newSize = computeNewSizeNoOverflowCheck(newLength, itemsize);
        if (newSize / itemsize < newLength) {
            throw OverflowException.INSTANCE;
        }
        return newSize;
    }

    private int computeNewSizeNoOverflowCheck(int newLength, int itemsize) {
        if (newLength == 0) {
            return 0;
        }
        // Overallocation using the same formula as CPython
        return ((newLength >> 4) + (length < 8 ? 3 : 7) + newLength) * itemsize;
    }

    public void resizeStorage(int newLength) throws OverflowException {
        assert newLength >= 0;
        int itemsize = format.bytesize;
        if (buffer.length / itemsize < newLength || length + 16 >= newLength) {
            byte[] newBuffer = new byte[computeNewSize(newLength, itemsize)];
            PythonUtils.arraycopy(buffer, 0, newBuffer, 0, Math.min(buffer.length, newBuffer.length));
            buffer = newBuffer;
        }
    }

    public void resize(int newLength) throws OverflowException {
        resizeStorage(newLength);
        length = newLength;
    }

    public void shift(int from, int by) throws OverflowException {
        assert from >= 0 && from <= length;
        assert by >= 0;
        int newLength = PythonUtils.addExact(length, by);
        int itemsize = format.bytesize;
        if (buffer.length / itemsize < newLength) {
            byte[] newBuffer = new byte[computeNewSize(newLength, itemsize)];
            PythonUtils.arraycopy(buffer, 0, newBuffer, 0, from * itemsize);
            PythonUtils.arraycopy(buffer, from * itemsize, newBuffer, (from + by) * itemsize, (length - from) * itemsize);
            buffer = newBuffer;
        } else {
            PythonUtils.arraycopy(buffer, from * itemsize, buffer, (from + by) * itemsize, (length - from) * itemsize);
        }
        length = newLength;
    }

    public void delSlice(int at, int count) {
        assert count >= 0;
        assert at + count <= length;
        int newLength = length - count;
        assert newLength >= 0;
        int itemsize = format.bytesize;
        if (length + 16 >= newLength) {
            byte[] newBuffer = new byte[computeNewSizeNoOverflowCheck(newLength, itemsize)];
            PythonUtils.arraycopy(buffer, 0, newBuffer, 0, at * itemsize);
            PythonUtils.arraycopy(buffer, (at + count) * itemsize, newBuffer, at * itemsize, (length - at - count) * itemsize);
            buffer = newBuffer;
        } else {
            PythonUtils.arraycopy(buffer, (at + count) * itemsize, buffer, at * itemsize, (length - at - count) * itemsize);
        }
        length = newLength;
    }

    @ExportMessage
    static boolean isBuffer(@SuppressWarnings("unused") PArray self) {
        return true;
    }

    @ExportMessage
    byte[] getBufferBytes() {
        try {
            return Arrays.copyOf(buffer, getBufferLength());
        } catch (Throwable t) {
            // Break exception edges
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw t;
        }
    }

    @ExportMessage
    int getBufferLength() {
        return length * format.bytesize;
    }

    public enum MachineFormat {
        UNKNOWN_FORMAT(-1, null, null),
        UNSIGNED_INT8(0, BufferFormat.UINT_8, null),
        SIGNED_INT8(1, BufferFormat.INT_8, null),
        UNSIGNED_INT16_LE(2, BufferFormat.UINT_16, ByteOrder.LITTLE_ENDIAN),
        UNSIGNED_INT16_BE(3, BufferFormat.UINT_16, ByteOrder.BIG_ENDIAN),
        SIGNED_INT16_LE(4, BufferFormat.INT_16, ByteOrder.LITTLE_ENDIAN),
        SIGNED_INT16_BE(5, BufferFormat.INT_16, ByteOrder.BIG_ENDIAN),
        UNSIGNED_INT32_LE(6, BufferFormat.UINT_32, ByteOrder.LITTLE_ENDIAN),
        UNSIGNED_INT32_BE(7, BufferFormat.UINT_32, ByteOrder.BIG_ENDIAN),
        SIGNED_INT32_LE(8, BufferFormat.INT_32, ByteOrder.LITTLE_ENDIAN),
        SIGNED_INT32_BE(9, BufferFormat.INT_32, ByteOrder.BIG_ENDIAN),
        UNSIGNED_INT64_LE(10, BufferFormat.UINT_64, ByteOrder.LITTLE_ENDIAN),
        UNSIGNED_INT64_BE(11, BufferFormat.UINT_64, ByteOrder.BIG_ENDIAN),
        SIGNED_INT64_LE(12, BufferFormat.INT_64, ByteOrder.LITTLE_ENDIAN),
        SIGNED_INT64_BE(13, BufferFormat.INT_64, ByteOrder.BIG_ENDIAN),
        IEEE_754_FLOAT_LE(14, BufferFormat.FLOAT, ByteOrder.LITTLE_ENDIAN),
        IEEE_754_FLOAT_BE(15, BufferFormat.FLOAT, ByteOrder.BIG_ENDIAN),
        IEEE_754_DOUBLE_LE(16, BufferFormat.DOUBLE, ByteOrder.LITTLE_ENDIAN),
        IEEE_754_DOUBLE_BE(17, BufferFormat.DOUBLE, ByteOrder.BIG_ENDIAN),
        // TODO
        UTF16_LE(18, null, ByteOrder.LITTLE_ENDIAN),
        UTF16_BE(19, null, ByteOrder.BIG_ENDIAN),
        UTF32_LE(20, BufferFormat.UNICODE, ByteOrder.LITTLE_ENDIAN),
        UTF32_BE(21, BufferFormat.UNICODE, ByteOrder.BIG_ENDIAN);

        public final int code;
        public final BufferFormat format;
        public final ByteOrder order;

        MachineFormat(int code, BufferFormat format, ByteOrder order) {
            this.code = code;
            this.format = format;
            this.order = order;
        }

        @ExplodeLoop
        public static MachineFormat forFormat(BufferFormat format) {
            for (MachineFormat machineFormat : MachineFormat.values()) {
                if (machineFormat.format == format && (machineFormat.order == null || machineFormat.order == ByteOrder.nativeOrder())) {
                    return machineFormat;
                }
            }
            return null;
        }
    }
}
