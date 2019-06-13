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

import argparse
import json
import os
import shutil
import site
import subprocess
import sys
import tempfile

def system(cmd, msg=""):
    status = os.system(cmd)
    if status != 0:
        xit(msg, status=status)


def known_packages():
    def PyYAML(*args):
        install_from_pypi("PyYAML==3.13", args)

    def six(*args):
        install_from_pypi("six==1.12.0", args)

    def Cython(*args):
        install_from_pypi("Cython==0.29.2", ('--no-cython-compile',) + args)

    def setuptools(*args):
        install_from_pypi("setuptools==40.6.3", args)

    def setuptools_scm(*args):
        install_from_url("https://files.pythonhosted.org/packages/70/bc/f34b06274c1260c5e4842f789fb933a09b89f23549f282b36a15bdf63614/setuptools_scm-1.15.0rc1.tar.gz", extra_opts=args)

    def numpy(*args):
        try:
            import setuptools as st
        except ImportError:
            print("Installing required dependency: setuptools")
            setuptools(*args)

        patch = """
diff --git a/setup.py 2018-02-28 17:03:26.000000000 +0100
index e450a66..ed538b4 100644
--- a/setup.py
+++ b/setup.py
@@ -348,6 +348,8 @@
 metadata = dict(
         name = 'numpy',
         maintainer = "NumPy Developers",
+        zip_safe = False, # Truffle: make sure we're not zipped
+        include_package_data = True,
         maintainer_email = "numpy-discussion@python.org",
         description = DOCLINES[0],
         long_description = "\n".join(DOCLINES[2:]),


diff --git a/numpy/ctypeslib.py 2018-02-28 17:03:26.000000000 +0100
index e450a66..ed538b4 100644
--- a/numpy/ctypeslib.py
+++ b/numpy/ctypeslib.py
@@ -59,6 +59,6 @@
 from numpy.core.multiarray import _flagdict, flagsobj

 try:
-    import ctypes
+    ctypes = None # Truffle: use the mock ctypes
 except ImportError:
     ctypes = None



diff --git a/numpy/core/include/numpy/ndarraytypes.h 2018-02-28 17:03:26.000000000 +0100
index e450a66..ed538b4 100644
--- a/numpy/core/include/numpy/ndarraytypes.h
+++ b/numpy/core/include/numpy/ndarraytypes.h
@@ -407,6 +407,6 @@
 typedef int (PyArray_FromStrFunc)(char *s, void *dptr, char **endptr,
                                   struct _PyArray_Descr *);

-typedef int (PyArray_FillFunc)(void *, npy_intp, void *);
+typedef void (PyArray_FillFunc)(void *, npy_intp, void *);

 typedef int (PyArray_SortFunc)(void *, npy_intp, void *);
 typedef int (PyArray_ArgSortFunc)(void *, npy_intp *, npy_intp, void *);


diff --git a/numpy/linalg/setup.py 2018-02-28 17:03:26.000000000 +0100
index e450a66..ed538b4 100644
--- a/numpy/linalg/setup.py
+++ b/numpy/linalg/setup.py
@@ -29,6 +29,7 @@
     lapack_info = get_info('lapack_opt', 0)  # and {}

     def get_lapack_lite_sources(ext, build_dir):
+        return all_sources
         if not lapack_info:
             print("### Warning:  Using unoptimized lapack ###")
             return all_sources


diff --git a/numpy/core/getlimits.py b/numpy/core/getlimits.py
index e450a66..ed538b4 100644
--- a/numpy/core/getlimits.py
+++ b/numpy/core/getlimits.py
@@ -160,70 +160,70 @@ _float64_ma = MachArLike(_f64,
                          huge=(1.0 - _epsneg_f64) / _tiny_f64 * _f64(4),
                          tiny=_tiny_f64)

-# Known parameters for IEEE 754 128-bit binary float
-_ld = ntypes.longdouble
-_epsneg_f128 = exp2(_ld(-113))
-_tiny_f128 = exp2(_ld(-16382))
-# Ignore runtime error when this is not f128
-with numeric.errstate(all='ignore'):
-    _huge_f128 = (_ld(1) - _epsneg_f128) / _tiny_f128 * _ld(4)
-_float128_ma = MachArLike(_ld,
-                         machep=-112,
-                         negep=-113,
-                         minexp=-16382,
-                         maxexp=16384,
-                         it=112,
-                         iexp=15,
-                         ibeta=2,
-                         irnd=5,
-                         ngrd=0,
-                         eps=exp2(_ld(-112)),
-                         epsneg=_epsneg_f128,
-                         huge=_huge_f128,
-                         tiny=_tiny_f128)
-
-# Known parameters for float80 (Intel 80-bit extended precision)
-_epsneg_f80 = exp2(_ld(-64))
-_tiny_f80 = exp2(_ld(-16382))
-# Ignore runtime error when this is not f80
-with numeric.errstate(all='ignore'):
-    _huge_f80 = (_ld(1) - _epsneg_f80) / _tiny_f80 * _ld(4)
-_float80_ma = MachArLike(_ld,
-                         machep=-63,
-                         negep=-64,
-                         minexp=-16382,
-                         maxexp=16384,
-                         it=63,
-                         iexp=15,
-                         ibeta=2,
-                         irnd=5,
-                         ngrd=0,
-                         eps=exp2(_ld(-63)),
-                         epsneg=_epsneg_f80,
-                         huge=_huge_f80,
-                         tiny=_tiny_f80)
-
-# Guessed / known parameters for double double; see:
-# https://en.wikipedia.org/wiki/Quadruple-precision_floating-point_format#Double-double_arithmetic
-# These numbers have the same exponent range as float64, but extended number of
-# digits in the significand.
-_huge_dd = (umath.nextafter(_ld(inf), _ld(0))
-            if hasattr(umath, 'nextafter')  # Missing on some platforms?
-            else _float64_ma.huge)
-_float_dd_ma = MachArLike(_ld,
-                          machep=-105,
-                          negep=-106,
-                          minexp=-1022,
-                          maxexp=1024,
-                          it=105,
-                          iexp=11,
-                          ibeta=2,
-                          irnd=5,
-                          ngrd=0,
-                          eps=exp2(_ld(-105)),
-                          epsneg= exp2(_ld(-106)),
-                          huge=_huge_dd,
-                          tiny=exp2(_ld(-1022)))
+# # Known parameters for IEEE 754 128-bit binary float
+# _ld = ntypes.longdouble
+# _epsneg_f128 = exp2(_ld(-113))
+# _tiny_f128 = exp2(_ld(-16382))
+# # Ignore runtime error when this is not f128
+# with numeric.errstate(all='ignore'):
+#     _huge_f128 = (_ld(1) - _epsneg_f128) / _tiny_f128 * _ld(4)
+# _float128_ma = MachArLike(_ld,
+#                          machep=-112,
+#                          negep=-113,
+#                          minexp=-16382,
+#                          maxexp=16384,
+#                          it=112,
+#                          iexp=15,
+#                          ibeta=2,
+#                          irnd=5,
+#                          ngrd=0,
+#                          eps=exp2(_ld(-112)),
+#                          epsneg=_epsneg_f128,
+#                          huge=_huge_f128,
+#                          tiny=_tiny_f128)
+
+# # Known parameters for float80 (Intel 80-bit extended precision)
+# _epsneg_f80 = exp2(_ld(-64))
+# _tiny_f80 = exp2(_ld(-16382))
+# # Ignore runtime error when this is not f80
+# with numeric.errstate(all='ignore'):
+#     _huge_f80 = (_ld(1) - _epsneg_f80) / _tiny_f80 * _ld(4)
+# _float80_ma = MachArLike(_ld,
+#                          machep=-63,
+#                          negep=-64,
+#                          minexp=-16382,
+#                          maxexp=16384,
+#                          it=63,
+#                          iexp=15,
+#                          ibeta=2,
+#                          irnd=5,
+#                          ngrd=0,
+#                          eps=exp2(_ld(-63)),
+#                          epsneg=_epsneg_f80,
+#                          huge=_huge_f80,
+#                          tiny=_tiny_f80)
+
+# # Guessed / known parameters for double double; see:
+# # https://en.wikipedia.org/wiki/Quadruple-precision_floating-point_format#Double-double_arithmetic
+# # These numbers have the same exponent range as float64, but extended number of
+# # digits in the significand.
+# _huge_dd = (umath.nextafter(_ld(inf), _ld(0))
+#             if hasattr(umath, 'nextafter')  # Missing on some platforms?
+#             else _float64_ma.huge)
+# _float_dd_ma = MachArLike(_ld,
+#                           machep=-105,
+#                           negep=-106,
+#                           minexp=-1022,
+#                           maxexp=1024,
+#                           it=105,
+#                           iexp=11,
+#                           ibeta=2,
+#                           irnd=5,
+#                           ngrd=0,
+#                           eps=exp2(_ld(-105)),
+#                           epsneg= exp2(_ld(-106)),
+#                           huge=_huge_dd,
+#                           tiny=exp2(_ld(-1022)))


 # Key to identify the floating point type.  Key is result of
@@ -234,17 +234,17 @@ _KNOWN_TYPES = {
     b'\\x9a\\x99\\x99\\x99\\x99\\x99\\xb9\\xbf' : _float64_ma,
     b'\\xcd\\xcc\\xcc\\xbd' : _float32_ma,
     b'f\\xae' : _float16_ma,
-    # float80, first 10 bytes containing actual storage
-    b'\\xcd\\xcc\\xcc\\xcc\\xcc\\xcc\\xcc\\xcc\\xfb\\xbf' : _float80_ma,
-    # double double; low, high order (e.g. PPC 64)
-    b'\\x9a\\x99\\x99\\x99\\x99\\x99Y<\\x9a\\x99\\x99\\x99\\x99\\x99\\xb9\\xbf' :
-    _float_dd_ma,
-    # double double; high, low order (e.g. PPC 64 le)
-    b'\\x9a\\x99\\x99\\x99\\x99\\x99\\xb9\\xbf\\x9a\\x99\\x99\\x99\\x99\\x99Y<' :
-    _float_dd_ma,
-    # IEEE 754 128-bit binary float
-    b'\\x9a\\x99\\x99\\x99\\x99\\x99\\x99\\x99\\x99\\x99\\x99\\x99\\x99\\x99\\xfb\\xbf' :
-    _float128_ma,
+    # # float80, first 10 bytes containing actual storage
+    # b'\\xcd\\xcc\\xcc\\xcc\\xcc\\xcc\\xcc\\xcc\\xfb\\xbf' : _float80_ma,
+    # # double double; low, high order (e.g. PPC 64)
+    # b'\\x9a\\x99\\x99\\x99\\x99\\x99Y<\\x9a\\x99\\x99\\x99\\x99\\x99\\xb9\\xbf' :
+    # _float_dd_ma,
+    # # double double; high, low order (e.g. PPC 64 le)
+    # b'\\x9a\\x99\\x99\\x99\\x99\\x99\\xb9\\xbf\\x9a\\x99\\x99\\x99\\x99\\x99Y<' :
+    # _float_dd_ma,
+    # # IEEE 754 128-bit binary float
+    # b'\\x9a\\x99\\x99\\x99\\x99\\x99\\x99\\x99\\x99\\x99\\x99\\x99\\x99\\x99\\xfb\\xbf' :
+    # _float128_ma,
 }


--
2.14.1

"""
        install_from_url("https://files.pythonhosted.org/packages/b0/2b/497c2bb7c660b2606d4a96e2035e92554429e139c6c71cdff67af66b58d2/numpy-1.14.3.zip", patch=patch, extra_opts=args)


    def dateutil(*args):
        try:
            import setuptools_scm as st_scm
        except ImportError:
            print("Installing required dependency: setuptools_scm")
            setuptools_scm(*args)
        install_from_url("https://files.pythonhosted.org/packages/0e/01/68747933e8d12263d41ce08119620d9a7e5eb72c876a3442257f74490da0/python-dateutil-2.7.5.tar.gz", extra_opts=args)


    def pytz(*args):
        install_from_url("https://files.pythonhosted.org/packages/cd/71/ae99fc3df1b1c5267d37ef2c51b7d79c44ba8a5e37b48e3ca93b4d74d98b/pytz-2018.7.tar.gz", extra_opts=args)


    def pandas(*args):
        try:
            import numpy as np
        except ImportError:
            print("Installing required dependency: numpy")
            numpy(*args)


        try:
            import pytz as _dummy_pytz
        except ImportError:
            print("Installing required dependency: pytz")
            pytz(*args)

        try:
            import six as _dummy_six
        except ImportError:
            print("Installing required dependency: six")
            six(*args)

        try:
            import dateutil as __dummy_dateutil
        except ImportError:
            print("Installing required dependency: dateutil")
            dateutil(*args)

        # download pandas-0.20.3
        patch = """diff --git a/pandas/_libs/src/period_helper.c b/pandas/_libs/src/period_helper.c
index 19f810e..2f01238 100644
--- a/pandas/_libs/src/period_helper.c
+++ b/pandas/_libs/src/period_helper.c
@@ -1105,7 +1105,7 @@ static int dInfoCalc_SetFromAbsDateTime(struct date_info *dinfo,
     /* Bounds check */
     Py_AssertWithArg(abstime >= 0.0 && abstime <= SECONDS_PER_DAY,
                      PyExc_ValueError,
-                     "abstime out of range (0.0 - 86400.0): %f", abstime);
+                     "abstime out of range (0.0 - 86400.0): %f", (long long)abstime);

     /* Calculate the date */
     if (dInfoCalc_SetFromAbsDate(dinfo, absdate, calendar)) goto onError;
diff --git a/pandas/_libs/src/period_helper.c b/pandas/_libs/src/period_helper.c
index 2f01238..6c79eb5 100644
--- a/pandas/_libs/src/period_helper.c
+++ b/pandas/_libs/src/period_helper.c
@@ -157,7 +157,7 @@ static int dInfoCalc_SetFromDateAndTime(struct date_info *dinfo, int year,
                 (second < (double)60.0 ||
                  (hour == 23 && minute == 59 && second < (double)61.0)),
             PyExc_ValueError,
-            "second out of range (0.0 - <60.0; <61.0 for 23:59): %f", second);
+            "second out of range (0.0 - <60.0; <61.0 for 23:59): %f", (long long)second);

         dinfo->abstime = (double)(hour * 3600 + minute * 60) + second;

diff --git a/pandas/io/msgpack/_packer.cpp b/pandas/io/msgpack/_packer.cpp
index 8b5b382..7544707 100644
--- a/pandas/io/msgpack/_packer.cpp
+++ b/pandas/io/msgpack/_packer.cpp
@@ -477,10 +477,7 @@ typedef struct {PyObject **p; const char *s; const Py_ssize_t n; const char* enc
     (sizeof(type) == sizeof(Py_ssize_t) &&\\
           (is_signed || likely(v < (type)PY_SSIZE_T_MAX ||\\
                                v == (type)PY_SSIZE_T_MAX)))  )
-#if defined (__cplusplus) && __cplusplus >= 201103L
-    #include <cstdlib>
-    #define __Pyx_sst_abs(value) std::abs(value)
-#elif SIZEOF_INT >= SIZEOF_SIZE_T
+#if SIZEOF_INT >= SIZEOF_SIZE_T
     #define __Pyx_sst_abs(value) abs(value)
 #elif SIZEOF_LONG >= SIZEOF_SIZE_T
     #define __Pyx_sst_abs(value) labs(value)
diff --git a/pandas/io/msgpack/_unpacker.cpp b/pandas/io/msgpack/_unpacker.cpp
index fa08f53..49f3bf3 100644
--- a/pandas/io/msgpack/_unpacker.cpp
+++ b/pandas/io/msgpack/_unpacker.cpp
@@ -477,10 +477,7 @@ typedef struct {PyObject **p; const char *s; const Py_ssize_t n; const char* enc
     (sizeof(type) == sizeof(Py_ssize_t) &&\\
           (is_signed || likely(v < (type)PY_SSIZE_T_MAX ||\\
                                v == (type)PY_SSIZE_T_MAX)))  )
-#if defined (__cplusplus) && __cplusplus >= 201103L
-    #include <cstdlib>
-    #define __Pyx_sst_abs(value) std::abs(value)
-#elif SIZEOF_INT >= SIZEOF_SIZE_T
+#if SIZEOF_INT >= SIZEOF_SIZE_T
     #define __Pyx_sst_abs(value) abs(value)
 #elif SIZEOF_LONG >= SIZEOF_SIZE_T
     #define __Pyx_sst_abs(value) labs(value)

"""
        cflags = "-allowcpp" if sys.implementation.name == "graalpython" else ""
        install_from_url("https://files.pythonhosted.org/packages/ee/aa/90c06f249cf4408fa75135ad0df7d64c09cf74c9870733862491ed5f3a50/pandas-0.20.3.tar.gz", patch=patch, extra_opts=args, add_cflags=cflags)

    return locals()


KNOWN_PACKAGES = known_packages()


def xit(msg, status=-1):
    print(msg)
    exit(-1)


def install_from_url(url, patch=None, extra_opts=[], add_cflags=""):
    name = url[url.rfind("/")+1:]
    tempdir = tempfile.mkdtemp()

    # honor env var 'HTTP_PROXY' and 'HTTPS_PROXY'
    env = os.environ
    curl_opts = []
    if url.startswith("http://") and "HTTP_PROXY" in env:
        curl_opts += ["--proxy", env["HTTP_PROXY"]]
    elif url.startswith("https://") and "HTTPS_PROXY" in env:
        curl_opts += ["--proxy", env["HTTPS_PROXY"]]

    # honor env var 'CFLAGS' and 'CPPFLAGS'
    cppflags = os.environ.get("CPPFLAGS", "")
    cflags = "-v " + os.environ.get("CFLAGS", "") + ((" " + add_cflags) if add_cflags else "")

    system("curl %s -o %s/%s %s" % (" ".join(curl_opts), tempdir, name, url))
    if name.endswith(".tar.gz"):
        system("tar xzf %s/%s -C %s" % (tempdir, name, tempdir))
        bare_name = name[:-len(".tar.gz")]
    elif name.endswith(".zip"):
        system("unzip -u %s/%s -d %s" % (tempdir, name, tempdir))
        bare_name = name[:-len(".zip")]

    if patch:
        with open("%s/%s.patch" % (tempdir, bare_name), "w") as f:
            f.write(patch)
        system("patch -d %s/%s/ -p1 < %s/%s.patch" % ((tempdir, bare_name)*2))

    if "--prefix" not in extra_opts and site.ENABLE_USER_SITE:
        user_arg = "--user"
    else:
        user_arg = ""
    system("cd %s/%s; %s %s %s setup.py install %s %s" % (tempdir, bare_name, 'CFLAGS="%s"' % cflags if cflags else "", 'CPPFLAGS="%s"' % cppflags if cppflags else "", sys.executable, user_arg, " ".join(extra_opts)))


def install_from_pypi(package, extra_opts=[]):
    if "==" in package:
        package, _, version = package.rpartition("==")
        url = "https://pypi.org/pypi/%s/%s/json" % (package, version)
    else:
        url = "https://pypi.org/pypi/%s/json" % package

    r = subprocess.check_output("curl %s" % url, shell=True).decode("utf8")
    try:
        urls = json.loads(r)["urls"]
    except:
        pass
    else:
        for url_info in urls:
            if url_info["python_version"] == "source":
                url = url_info["url"]
                break

    if url:
        tempdir = tempfile.mkdtemp()
        filename = url.rpartition("/")[2]
        system("curl -L -o %s/%s %s" % (tempdir, filename, url), msg="Download error")
        dirname = None
        if filename.endswith(".zip"):
            system("unzip -u %s/%s -d %s" % (tempdir, filename, tempdir))
            dirname = filename[:-4]
        elif filename.endswith(".tar.gz"):
            system("tar -C %s -xzf %s/%s" % (tempdir, tempdir, filename), msg="Error during extraction")
            dirname = filename[:-7]
        elif filename.endswith(".tar.bz2"):
            system("tar -C %s -xjf %s/%s" % (tempdir, tempdir, filename), msg="Error during extraction")
            dirname = filename[:-7]
        else:
            xit("Unknown file type: %s" % filename)

        if "--prefix" not in extra_opts and site.ENABLE_USER_SITE:
            user_arg = "--user"
        else:
            user_arg = ""
        status = os.system("cd %s/%s; %s setup.py install %s %s" % (tempdir, dirname, sys.executable, user_arg, " ".join(extra_opts)))
        if status != 0:
            xit("An error occurred trying to run `setup.py install %s %s'" % (user_arg, " ".join(extra_opts)))
    else:
        xit("Package not found: '%s'" % package)


def main(argv):
    parser = argparse.ArgumentParser(description="The simple Python package installer for GraalVM")

    subparsers = parser.add_subparsers(title="Commands", dest="command", metavar="Use COMMAND --help for further help.")

    subparsers.add_parser(
        "list",
        help="list locally installed packages"
    )

    install_parser = subparsers.add_parser(
        "install",
        help="install a known package",
        description="Install a known package. Known packages are " + ", ".join(KNOWN_PACKAGES.keys())
    )
    install_parser.add_argument(
        "package",
        help="comma-separated list"
    )
    install_parser.add_argument(
        "--prefix",
        help="user-site path prefix"
    )

    subparsers.add_parser(
        "uninstall",
        help="remove installation folder of a local package",
    ).add_argument(
        "package",
        help="comma-separated list"
    )

    subparsers.add_parser(
        "pypi",
        help="attempt to install a package from PyPI (untested, likely won't work, and it won't install dependencies for you)",
        description="Attempt to install a package from PyPI"
    ).add_argument(
        "package",
        help="comma-separated list, can use `==` at the end of a package name to specify an exact version"
    )

    args = parser.parse_args(argv)

    if args.command == "list":
        if site.ENABLE_USER_SITE:
            user_site = site.getusersitepackages()
        else:
            for s in site.getsitepackages():
                if s.endswith("site-packages"):
                    user_site = s
                    break
        print("Installed packages:")
        for p in sys.path:
            if p.startswith(user_site):
                print(p[len(user_site) + 1:])
    elif args.command == "uninstall":
        print("WARNING: I will only delete the package folder, proper uninstallation is not supported at this time.")
        user_site = site.getusersitepackages()
        for pkg in args.package.split(","):
            deleted = False
            for p in sys.path:
                if p.startswith(user_site):
                    # +1 due to the path separator
                    pkg_name = p[len(user_site)+1:]
                    if pkg_name.startswith(pkg):
                        if os.path.isdir(p):
                            shutil.rmtree(p)
                        else:
                            os.unlink(p)
                        deleted = True
                        break
            if deleted:
                print("Deleted %s" % p)
            else:
                xit("Unknown package: '%s'" % pkg)
    elif args.command == "install":
        for pkg in args.package.split(","):
            if pkg not in KNOWN_PACKAGES:
                xit("Unknown package: '%s'" % pkg)
            else:
                if args.prefix:
                    KNOWN_PACKAGES[pkg]("--prefix", args.prefix)
                else:
                    KNOWN_PACKAGES[pkg]()
    elif args.command == "pypi":
        for pkg in args.package.split(","):
            install_from_pypi(pkg)



if __name__ == "__main__":
    main(sys.argv[1:])
