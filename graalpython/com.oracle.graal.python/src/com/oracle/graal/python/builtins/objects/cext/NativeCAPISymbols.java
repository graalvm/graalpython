/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.builtins.objects.cext;

import java.lang.reflect.Field;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;

public abstract class NativeCAPISymbols {

    public static final String FUN_NATIVE_POINTER_TO_JAVA = "native_pointer_to_java";
    public static final String FUN_NATIVE_LONG_TO_JAVA = "native_long_to_java";
    public static final String FUN_NATIVE_TO_JAVA = "native_to_java_exported";
    public static final String FUN_PY_TRUFFLE_STRING_TO_CSTR = "PyTruffle_StringToCstr";
    public static final String FUN_PY_OBJECT_HANDLE_FOR_JAVA_OBJECT = "PyObjectHandle_ForJavaObject";
    public static final String FUN_PY_OBJECT_HANDLE_FOR_JAVA_TYPE = "PyObjectHandle_ForJavaType";
    public static final String FUN_NATIVE_HANDLE_FOR_ARRAY = "NativeHandle_ForArray";
    public static final String FUN_PY_NONE_HANDLE = "PyNoneHandle";
    public static final String FUN_WHCAR_SIZE = "PyTruffle_Wchar_Size";
    public static final String FUN_PY_TRUFFLE_CSTR_TO_STRING = "PyTruffle_CstrToString";
    public static final String FUN_PY_FLOAT_AS_DOUBLE = "truffle_read_ob_fval";
    public static final String FUN_GET_OB_TYPE = "get_ob_type";
    public static final String FUN_GET_TP_DICT = "get_tp_dict";
    public static final String FUN_GET_TP_BASES = "get_tp_bases";
    public static final String FUN_GET_TP_NAME = "get_tp_name";
    public static final String FUN_GET_TP_MRO = "get_tp_mro";
    public static final String FUN_GET_TP_ALLOC = "get_tp_alloc";
    public static final String FUN_GET_TP_FLAGS = "get_tp_flags";
    public static final String FUN_GET_TP_SUBCLASSES = "get_tp_subclasses";
    public static final String FUN_GET_TP_DICTOFFSET = "get_tp_dictoffset";
    public static final String FUN_GET_TP_BASICSIZE = "get_tp_basicsize";
    public static final String FUN_GET_TP_ITEMSIZE = "get_tp_itemsize";
    public static final String FUN_DEREF_HANDLE = "truffle_deref_handle_for_managed";
    public static final String FUN_GET_BYTE_ARRAY_TYPE_ID = "get_byte_array_typeid";
    public static final String FUN_GET_PTR_ARRAY_TYPE_ID = "get_ptr_array_typeid";
    public static final String FUN_PTR_COMPARE = "truffle_ptr_compare";
    public static final String FUN_PY_TRUFFLE_BYTE_ARRAY_TO_NATIVE = "PyTruffle_ByteArrayToNative";
    public static final String FUN_PY_TRUFFLE_INT_ARRAY_TO_NATIVE = "PyTruffle_IntArrayToNative";
    public static final String FUN_PY_TRUFFLE_LONG_ARRAY_TO_NATIVE = "PyTruffle_LongArrayToNative";
    public static final String FUN_PY_TRUFFLE_DOUBLE_ARRAY_TO_NATIVE = "PyTruffle_DoubleArrayToNative";
    public static final String FUN_PY_TRUFFLE_OBJECT_ARRAY_TO_NATIVE = "PyTruffle_ObjectArrayToNative";
    public static final String FUN_PY_OBJECT_GENERIC_GET_DICT = "_PyObject_GenericGetDict";
    public static final String FUN_PY_OBJECT_GENERIC_NEW = "PyTruffle_Type_GenericNew";
    public static final String FUN_GET_THREAD_STATE_TYPE_ID = "get_thread_state_typeid";
    public static final String FUN_ADD_NATIVE_SLOTS = "PyTruffle_Type_AddSlots";
    public static final String FUN_PY_TRUFFLE_TUPLE_SET_ITEM = "PyTruffle_Tuple_SetItem";
    public static final String FUN_PY_TRUFFLE_TUPLE_GET_ITEM = "PyTruffle_Tuple_GetItem";
    public static final String FUN_PY_TRUFFLE_OBJECT_SIZE = "PyTruffle_Object_Size";
    public static final String FUN_PY_TYPE_READY = "PyType_Ready";

    @CompilationFinal(dimensions = 1) private static final String[] values;
    static {
        Field[] declaredFields = NativeCAPISymbols.class.getDeclaredFields();
        values = new String[declaredFields.length - 1]; // omit the values field
        for (int i = 0; i < declaredFields.length; i++) {
            Field s = declaredFields[i];
            if (s.getType() == String.class) {
                try {
                    values[i] = (String) s.get(NativeMemberNames.class);
                } catch (IllegalArgumentException | IllegalAccessException e) {
                }
            }
        }
    }

    @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL)
    public static boolean isValid(String name) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(name)) {
                return true;
            }
        }
        return false;
    }
}
