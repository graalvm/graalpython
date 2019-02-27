package com.oracle.graal.python.builtins.objects.cext;

import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_NATIVE_HANDLE_FOR_ARRAY;

import com.oracle.graal.python.builtins.objects.bytes.PIBytesLike;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins;
import com.oracle.graal.python.builtins.objects.list.ListBuiltinsFactory;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltinsFactory;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallTernaryNode;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.EmptySequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;

/**
 * Wraps a sequence object (like a list) such that it behaves like a bare C array.
 */
@ExportLibrary(InteropLibrary.class)
public final class PySequenceArrayWrapper extends NativeWrappers.PythonNativeWrapper {

    /** Number of bytes that constitute a single element. */
    private final int elementAccessSize;

    public PySequenceArrayWrapper(Object delegate, int elementAccessSize) {
        super(delegate);
        this.elementAccessSize = elementAccessSize;
    }

    public int getElementAccessSize() {
        return elementAccessSize;
    }

    static boolean isInstance(TruffleObject o) {
        return o instanceof PySequenceArrayWrapper;
    }

    @ExportMessage
    final long getArraySize(
            @Shared("getSequenceStorageNode") @Cached(allowUncached = true) SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
            @Shared("lenNode") @Cached(allowUncached = true) SequenceStorageNodes.LenNode lenNode) {
        return lenNode.execute(getSequenceStorageNode.execute(this.getDelegate()));
    }

    @ExportMessage
    final boolean hasArrayElements() {
        return true;
    }

    @ExportMessage
    final Object readArrayElement(long index,
             @Exclusive @Cached(allowUncached = true) ReadArrayItemNode readArrayItemNode) {
        return readArrayItemNode.execute(this.getDelegate(), index);
    }

    @ExportMessage
    final boolean isArrayElementReadable(long identifier,
             @Shared("getSequenceStorageNode") @Cached(allowUncached = true) SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
             @Shared("lenNode") @Cached(allowUncached = true) SequenceStorageNodes.LenNode lenNode) {
        return 0 <= identifier && identifier < getArraySize(getSequenceStorageNode, lenNode);
    }

    @ImportStatic(SpecialMethodNames.class)
    @TypeSystemReference(PythonTypes.class)
    abstract static class ReadArrayItemNode extends Node {

        public abstract Object execute(Object arrayObject, Object idx);

        @Specialization
        Object doTuple(PTuple tuple, long idx,
                       @Cached(value = "createTupleGetItem()", allowUncached = true) TupleBuiltins.GetItemNode getItemNode,
                       @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(getItemNode.execute(tuple, idx));
        }

        @Specialization
        Object doTuple(PList list, long idx,
                       @Cached("createListGetItem()") ListBuiltins.GetItemNode getItemNode,
                       @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(getItemNode.execute(list, idx));
        }

        /**
         * The sequence array wrapper of a {@code bytes} object represents {@code ob_sval}. We type
         * it as {@code uint8_t*} and therefore we get a byte index. However, we return
         * {@code uint64_t} since we do not know how many bytes are requested.
         */
        @Specialization
        long doBytesI64(PIBytesLike bytesLike, long byteIdx,
                        @Cached("createClassProfile()") ValueProfile profile,
                        @Cached("create()") SequenceStorageNodes.LenNode lenNode,
                        @Cached("create()") SequenceStorageNodes.GetItemNode getItemNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            PIBytesLike profiled = profile.profile(bytesLike);
            int len = lenNode.execute(profiled.getSequenceStorage());
            // simulate sentinel value
            if (byteIdx == len) {
                return 0L;
            }
            int i = (int) byteIdx;
            long result = 0;
            SequenceStorage store = profiled.getSequenceStorage();
            result |= getItemNode.executeInt(store, i);
            if (i + 1 < len)
                result |= ((long) getItemNode.executeInt(store, i + 1) << 8L) & 0xFF00L;
            if (i + 2 < len)
                result |= ((long) getItemNode.executeInt(store, i + 2) << 16L) & 0xFF0000L;
            if (i + 3 < len)
                result |= ((long) getItemNode.executeInt(store, i + 3) << 24L) & 0xFF000000L;
            if (i + 4 < len)
                result |= ((long) getItemNode.executeInt(store, i + 4) << 32L) & 0xFF00000000L;
            if (i + 5 < len)
                result |= ((long) getItemNode.executeInt(store, i + 5) << 40L) & 0xFF0000000000L;
            if (i + 6 < len)
                result |= ((long) getItemNode.executeInt(store, i + 6) << 48L) & 0xFF000000000000L;
            if (i + 7 < len)
                result |= ((long) getItemNode.executeInt(store, i + 7) << 56L) & 0xFF00000000000000L;
            return result;
        }

        @Specialization(guards = {"!isTuple(object)", "!isList(object)"})
        Object doGeneric(Object object, long idx,
                         @Cached("create(__GETITEM__)") LookupAndCallBinaryNode getItemNode,
                         @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            return toSulongNode.execute(getItemNode.executeObject(object, idx));
        }

        protected static ListBuiltins.GetItemNode createListGetItem() {
            return ListBuiltinsFactory.GetItemNodeFactory.create();
        }

        protected static TupleBuiltins.GetItemNode createTupleGetItem() {
            return TupleBuiltinsFactory.GetItemNodeFactory.create();
        }

        protected boolean isTuple(Object object) {
            return object instanceof PTuple;
        }

        protected boolean isList(Object object) {
            return object instanceof PList;
        }

        public static ReadArrayItemNode create() {
            return PySequenceArrayWrapperFactory.ReadArrayItemNodeGen.create();
        }
    }

    @ExportMessage
    public void writeArrayElement(long index, Object value,
          @Exclusive @Cached(allowUncached = true) WriteArrayItemNode writeArrayItemNode) {
        writeArrayItemNode.execute(this.getDelegate(), index, value);
    }

    @ExportMessage
    public void removeArrayElement(long index) throws UnsupportedMessageException, InvalidArrayIndexException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    public boolean isArrayElementModifiable(long index,
            @Shared("getSequenceStorageNode") @Cached(allowUncached = true) SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
            @Shared("lenNode") @Cached(allowUncached = true) SequenceStorageNodes.LenNode lenNode) {
        return 0 <= index && index < getArraySize(getSequenceStorageNode, lenNode);
    }

    @ExportMessage
    public boolean isArrayElementInsertable(long index,
            @Shared("getSequenceStorageNode") @Cached(allowUncached = true) SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
            @Shared("lenNode") @Cached(allowUncached = true) SequenceStorageNodes.LenNode lenNode) {
        return 0 <= index && index <= getArraySize(getSequenceStorageNode, lenNode);
    }

    @ExportMessage
    public boolean isArrayElementRemovable(long index,
           @Shared("getSequenceStorageNode") @Cached(allowUncached = true) SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
           @Shared("lenNode") @Cached(allowUncached = true) SequenceStorageNodes.LenNode lenNode) {
        return 0 <= index && index < getArraySize(getSequenceStorageNode, lenNode);
    }

    @ImportStatic(SpecialMethodNames.class)
    @TypeSystemReference(PythonTypes.class)
    abstract static class WriteArrayItemNode extends Node {
        public abstract Object execute(Object arrayObject, Object idx, Object value);

        @Specialization
        Object doBytes(PIBytesLike s, long idx, byte value,
                       @Shared("getSequenceStorageNode") @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                       @Shared("setByteItemNode") @Cached("create(__SETITEM__)") SequenceStorageNodes.SetItemNode setByteItemNode) {
            setByteItemNode.executeLong(getSequenceStorageNode.execute(s), idx, value);
            return value;
        }

        @Specialization
        @ExplodeLoop
        Object doBytes(PIBytesLike s, long idx, short value,
                       @Shared("getSequenceStorageNode") @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                       @Shared("setByteItemNode") @Cached("create(__SETITEM__)") SequenceStorageNodes.SetItemNode setByteItemNode) {
            for (int offset = 0; offset < Short.BYTES; offset++) {
                setByteItemNode.executeLong(getSequenceStorageNode.execute(s), idx + offset, (byte) (value >> (8 * offset)) & 0xFF);
            }
            return value;
        }

        @Specialization
        @ExplodeLoop
        Object doBytes(PIBytesLike s, long idx, int value,
                       @Shared("getSequenceStorageNode") @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                       @Shared("setByteItemNode") @Cached("create(__SETITEM__)") SequenceStorageNodes.SetItemNode setByteItemNode) {
            for (int offset = 0; offset < Integer.BYTES; offset++) {
                setByteItemNode.executeLong(getSequenceStorageNode.execute(s), idx + offset, (byte) (value >> (8 * offset)) & 0xFF);
            }
            return value;
        }

        @Specialization
        @ExplodeLoop
        Object doBytes(PIBytesLike s, long idx, long value,
                       @Shared("getSequenceStorageNode") @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                       @Shared("setByteItemNode") @Cached("create(__SETITEM__)") SequenceStorageNodes.SetItemNode setByteItemNode) {
            for (int offset = 0; offset < Long.BYTES; offset++) {
                setByteItemNode.executeLong(getSequenceStorageNode.execute(s), idx + offset, (byte) (value >> (8 * offset)) & 0xFF);
            }
            return value;
        }

        @Specialization
        Object doList(PList s, long idx, Object value,
                      @Shared("toJavaNode") @Cached CExtNodes.ToJavaNode toJavaNode,
                      @Cached("createSetListItem()") SequenceStorageNodes.SetItemNode setListItemNode,
                      @Cached("createBinaryProfile()") ConditionProfile updateStorageProfile) {
            SequenceStorage storage = s.getSequenceStorage();
            SequenceStorage updatedStorage = setListItemNode.executeLong(storage, idx, toJavaNode.execute(value));
            if (updateStorageProfile.profile(storage != updatedStorage)) {
                s.setSequenceStorage(updatedStorage);
            }
            return value;
        }

        @Specialization
        Object doTuple(PTuple s, long idx, Object value,
                       @Shared("toJavaNode") @Cached CExtNodes.ToJavaNode toJavaNode,
                       @Cached("createSetItem()") SequenceStorageNodes.SetItemNode setListItemNode) {
            setListItemNode.executeLong(s.getSequenceStorage(), idx, toJavaNode.execute(value));
            return value;
        }

        @Fallback
        Object doGeneric(Object sequence, Object idx, Object value) {
            CExtNodes.ToJavaNode toJavaNode = CExtNodes.ToJavaNode.getUncached();
            LookupAndCallTernaryNode setItemNode = LookupAndCallTernaryNode.getUncached();
            setItemNode.execute(sequence, idx, toJavaNode.execute(value));
            return value;
        }

        protected static SequenceStorageNodes.SetItemNode createSetListItem() {
            return SequenceStorageNodes.SetItemNode.create(SequenceStorageNodes.NormalizeIndexNode.forArrayAssign(), () -> SequenceStorageNodes.ListGeneralizationNode.create());
        }

        protected static SequenceStorageNodes.SetItemNode createSetItem() {
            return SequenceStorageNodes.SetItemNode.create("invalid item for assignment");
        }

        public static WriteArrayItemNode create() {
            return PySequenceArrayWrapperFactory.WriteArrayItemNodeGen.create();
        }
    }

    @ExportMessage
    public void toNative(@Exclusive @Cached(allowUncached = true) ToNativeArrayNode toPyObjectNode,
                         @Exclusive @Cached(allowUncached = true) InvalidateNativeObjectsAllManagedNode invalidateNode) {
        invalidateNode.execute();
        if (!this.isNative()) {
            this.setNativePointer(toPyObjectNode.execute(this));
        }
    }

    abstract static class ToNativeArrayNode extends CExtNodes.CExtBaseNode {
        public abstract Object execute(PySequenceArrayWrapper object);

        @Specialization(guards = "isPSequence(object.getDelegate())")
        Object doPSequence(PySequenceArrayWrapper object,
                           @Exclusive @Cached ToNativeStorageNode toNativeStorageNode) {
            PSequence sequence = (PSequence) object.getDelegate();
            NativeSequenceStorage nativeStorage = toNativeStorageNode.execute(sequence.getSequenceStorage());
            if (nativeStorage == null) {
                throw new AssertionError("could not allocate native storage");
            }
            // switch to native storage
            sequence.setSequenceStorage(nativeStorage);
            return nativeStorage.getPtr();
        }

        @Fallback
        Object doGeneric(PySequenceArrayWrapper object) {
            // TODO correct element size
            PCallNativeNode callNativeBinary = PCallNativeNodeGen.getUncached();
            InteropLibrary interopLibrary = InteropLibrary.getFactory().getUncached();
            TruffleObject PyObjectHandle_FromJavaObject = importCAPISymbol(interopLibrary, FUN_NATIVE_HANDLE_FOR_ARRAY);
            return callBinaryIntoCapi(PyObjectHandle_FromJavaObject, object, 8L, callNativeBinary);
        }


        protected boolean isPSequence(Object obj) {
            return obj instanceof PSequence;
        }

        private Object callBinaryIntoCapi(TruffleObject fun, Object arg0, Object arg1, PCallNativeNode callNativeBinary) {
            return callNativeBinary.execute(fun, new Object[]{arg0, arg1});
        }

        public static ToNativeArrayNode create() {
            return PySequenceArrayWrapperFactory.ToNativeArrayNodeGen.create();
        }
    }

    static abstract class ToNativeStorageNode extends PNodeWithContext {
        @Child private SequenceStorageNodes.StorageToNativeNode storageToNativeNode;

        public abstract NativeSequenceStorage execute(SequenceStorage object);

        @Specialization(guards = "!isNative(s)")
        NativeSequenceStorage doManaged(SequenceStorage s) {
            return getStorageToNativeNode().execute(s.getInternalArrayObject());
        }

        @Specialization
        NativeSequenceStorage doNative(NativeSequenceStorage s) {
            return s;
        }

        @Specialization
        NativeSequenceStorage doEmptyStorage(@SuppressWarnings("unused") EmptySequenceStorage s) {
            // TODO(fa): not sure if that completely reflects semantics
            return getStorageToNativeNode().execute(new byte[0]);
        }

        @Fallback
        NativeSequenceStorage doGeneric(@SuppressWarnings("unused") SequenceStorage s) {
            CompilerDirectives.transferToInterpreter();
            throw new UnsupportedOperationException("Unknown storage type: " + s);
        }

        protected static boolean isNative(SequenceStorage s) {
            return s instanceof NativeSequenceStorage;
        }

        private SequenceStorageNodes.StorageToNativeNode getStorageToNativeNode() {
            if (storageToNativeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                storageToNativeNode = insert(SequenceStorageNodes.StorageToNativeNode.create());
            }
            return storageToNativeNode;
        }

        public static ToNativeStorageNode create() {
            return PySequenceArrayWrapperFactory.ToNativeStorageNodeGen.create();
        }
    }

    @ExportMessage
    public boolean isPointer(@Exclusive @Cached(allowUncached = true) CExtNodes.IsPointerNode pIsPointerNode) {
        return pIsPointerNode.execute(this);
    }

    @ExportMessage
    public long asPointer(@CachedLibrary(limit = "1") InteropLibrary interopLibrary) throws UnsupportedMessageException {
        Object nativePointer = this.getNativePointer();
        if (nativePointer instanceof Long) {
            return (long) nativePointer;
        }
        return interopLibrary.asPointer(nativePointer);
    }

}
