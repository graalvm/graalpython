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
package com.oracle.graal.python.nodes.code;

import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;

public abstract class GetSignatureNode extends PNodeWithContext {
    private static Signature doFunctionInternal(GetFunctionCodeNode getFunctionCodeNode, PFunction function) {
        return getFunctionCodeNode.execute(function).getSignature();
    }

    private static Signature doMethodInternal(GetFunctionCodeNode getFunctionCodeNode, Object function) {
        if (function instanceof PFunction) {
            return doFunctionInternal(getFunctionCodeNode, (PFunction) function);
        } else if (function instanceof PBuiltinFunction) {
            return ((PBuiltinFunction) function).getSignature();
        }
        return null;
    }

    public abstract Signature execute(Object function);

    @Specialization
    Signature doFunction(PFunction function,
                    @Cached("create()") GetFunctionCodeNode getFunctionCodeNode) {
        return doFunctionInternal(getFunctionCodeNode, function);
    }

    @Specialization
    Signature doBuiltinFunction(PBuiltinFunction builtinFunction) {
        return builtinFunction.getSignature();
    }

    @Specialization
    Signature doMethod(PMethod method,
                    @Cached("create()") GetFunctionCodeNode getFunctionCodeNode) {
        return doMethodInternal(getFunctionCodeNode, method.getFunction());
    }

    @Specialization
    Signature doBuiltinMethod(PBuiltinMethod builtinMethod,
                    @Cached("create()") GetFunctionCodeNode getFunctionCodeNode) {
        return doMethodInternal(getFunctionCodeNode, builtinMethod.getFunction());
    }

    public static GetSignatureNode create() {
        return GetSignatureNodeGen.create();
    }
}