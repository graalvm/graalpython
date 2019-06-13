/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToDynamicObjectNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;

@ImportStatic(PGuards.class)
@GenerateUncached
public abstract class PRaiseNode extends Node {

    public abstract PException execute(Object type, Object cause, Object format, Object[] arguments);

    public final PException raise(PythonBuiltinClassType type, String format, Object... arguments) {
        throw execute(type, PNone.NO_VALUE, format, arguments);
    }

    public final PException raise(PythonBuiltinClassType type, Exception e) {
        throw execute(type, PNone.NO_VALUE, getMessage(e), new Object[0]);
    }

    public final PException raiseIndexError() {
        return raise(PythonErrorType.IndexError, "cannot fit 'int' into an index-sized integer");
    }

    public final PException raise(LazyPythonClass exceptionType) {
        throw execute(exceptionType, PNone.NO_VALUE, PNone.NO_VALUE, new Object[0]);
    }

    public final PException raise(PythonBuiltinClassType type, PBaseException cause, String format, Object... arguments) {
        throw execute(type, cause, format, arguments);
    }

    public final PException raise(PBaseException exc) {
        throw raise(this, exc);
    }

    public static PException raise(Node raisingNode, PBaseException exc) {
        if (raisingNode.isAdoptable()) {
            throw PException.fromObject(exc, raisingNode);
        } else {
            throw PException.fromObject(exc, NodeUtil.getCurrentEncapsulatingNode());
        }
    }

    @Specialization(guards = {"isNoValue(cause)", "isNoValue(format)", "arguments.length == 0", "exceptionType == cachedType"}, limit = "8")
    PException doPythonBuiltinTypeCached(@SuppressWarnings("unused") PythonBuiltinClassType exceptionType, @SuppressWarnings("unused") PNone cause, @SuppressWarnings("unused") PNone format,
                    @SuppressWarnings("unused") Object[] arguments,
                    @Cached("exceptionType") PythonBuiltinClassType cachedType,
                    @Cached PythonObjectFactory factory) {
        throw raise(factory.createBaseException(cachedType));
    }

    @Specialization(guards = {"isNoValue(cause)", "isNoValue(format)", "arguments.length == 0"}, replaces = "doPythonBuiltinTypeCached")
    PException doPythonBuiltinType(PythonBuiltinClassType exceptionType, @SuppressWarnings("unused") PNone cause, @SuppressWarnings("unused") PNone format,
                    @SuppressWarnings("unused") Object[] arguments,
                    @Shared("factory") @Cached PythonObjectFactory factory) {
        throw raise(factory.createBaseException(exceptionType));
    }

    @Specialization(guards = {"isNoValue(cause)", "isNoValue(format)", "arguments.length == 0"})
    PException doPythonBuiltinClass(PythonBuiltinClass exceptionType, @SuppressWarnings("unused") PNone cause, @SuppressWarnings("unused") PNone format, @SuppressWarnings("unused") Object[] arguments,
                    @Shared("factory") @Cached PythonObjectFactory factory) {
        throw raise(factory.createBaseException(exceptionType));
    }

    @Specialization(guards = {"isNoValue(cause)", "isNoValue(format)", "arguments.length == 0"})
    PException doPythonManagedClass(PythonManagedClass exceptionType, @SuppressWarnings("unused") PNone cause, @SuppressWarnings("unused") PNone format, @SuppressWarnings("unused") Object[] arguments,
                    @Shared("factory") @Cached PythonObjectFactory factory) {
        throw raise(factory.createBaseException(exceptionType));
    }

    @Specialization(guards = {"isNoValue(cause)"})
    PException doBuiltinType(PythonBuiltinClassType type, @SuppressWarnings("unused") PNone cause, String format, Object[] arguments,
                    @Shared("factory") @Cached PythonObjectFactory factory) {
        assert format != null;
        throw raise(factory.createBaseException(type, format, arguments));
    }

    @Specialization(guards = {"!isNoValue(cause)"})
    PException doBuiltinTypeWithCause(PythonBuiltinClassType type, PBaseException cause, String format, Object[] arguments,
                    @Shared("factory") @Cached PythonObjectFactory factory,
                    @Cached WriteAttributeToDynamicObjectNode writeCause) {
        assert format != null;
        PBaseException baseException = factory.createBaseException(type, format, arguments);
        writeCause.execute(baseException.getStorage(), SpecialAttributeNames.__CAUSE__, cause);
        throw raise(baseException);
    }

    @TruffleBoundary
    private static final String getMessage(Exception e) {
        return e.getMessage();
    }

    public static PRaiseNode create() {
        return PRaiseNodeGen.create();
    }

    public static PRaiseNode getUncached() {
        return PRaiseNodeGen.getUncached();
    }
}
