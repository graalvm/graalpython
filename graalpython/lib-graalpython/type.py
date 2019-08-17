# Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

def __dir__(klass):
    """__dir__ for type objects

    This includes all attributes of klass and all of the base
    classes recursively.
    """
    names = set()
    ns = getattr(klass, '__dict__', None)
    if ns is not None:
        names.update(ns)
    bases = getattr(klass, '__bases__', None)
    if bases is not None:
        # Note that since we are only interested in the keys, the order
        # we merge classes is unimportant
        for base in bases:
            names.update(_classdir(base))
    return list(names)
_classdir = __dir__


def __dir__(obj):
    """__dir__ for generic objects

     Returns __dict__, __class__ and recursively up the
     __class__.__bases__ chain.
    """
    names = set()
    ns = getattr(obj, '__dict__', None)
    if isinstance(ns, dict):
        names.update(ns)
    klass = getattr(obj, '__class__', None)
    if klass is not None:
        names.update(_classdir(klass))
    return list(names)
_objectdir = __dir__


object.__dir__ = _objectdir
type.__dir__ = _classdir

def __subclasshook(cls, subclass):
    return NotImplemented

type.__subclasshook__ = classmethod(__subclasshook)


# TODO -----------------------------------------------------------------------------------------------------------------
# TODO: REMOVEME, temporary patch for coroutines
async def _c(): pass
_c = _c()
def _coro_close():
    pass
ctype = type(_c)
ctype.close = _coro_close
# TODO -----------------------------------------------------------------------------------------------------------------
