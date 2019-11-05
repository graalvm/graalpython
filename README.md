# GraalVM Implementation of Python

This is an early-stage experimental implementation of Python. A primary goal is
to support SciPy and its constituent libraries. This Python implementation
currently aims to be compatible with Python 3.7, but it is a long way from
there, and it is very likely that any Python program that requires any packages
at all will hit something unsupported. At this point, the Python implementation
is made available for experimentation and curious end-users.

### Trying it

To try it, you can use the bundled releases from
[www.graalvm.org](https://www.graalvm.org/downloads/). For more information and
some examples of what you can do with it, check out the
[reference](https://www.graalvm.org/docs/reference-manual/languages/python/).

### Create a virtual environment
#### Requirements:
---
* [mx](https://github.com/graalvm/mx) - a separate Python tool co-developed for GraalVM development. This tool must be downloaded and put onto your PATH:

```
git clone https://github.com/graalvm/mx.git
export PATH=$PWD/mx:$PATH
```
---
The best way of using the GraalVM implementation of Python is out of a virtual environment. This generates 
wrapper scripts and makes the implementation usable from shell as a standard Python interpreter. To do so
execute the following command in the project directory:

```
mx python -m venv <dir-to-venv>
```

To activate the environment in your shell session call:

```
source <dir-to-venv>/bin/activate
```

In the venv, multiple executables are available, like `python`, `python3` and `graalpython`. 

### Using modules with C extensions

This Python implementation is able to load and run modules with C extensions.
Supporting C extensions is one of the most difficult features for any Python 
implementation since it requires to be compatible to CPython's C API.

However, GraalVM's Python implementation is capable of executing C extensions and
there is also no optimization boundary.

In order to be able to run C extensions, a user must first build the C API
runtime library. It is recommended to build the C API in any case because
it will only be used if necessary. 
The recommended way to do so is to create a venv (see [Create a virtual environment](#create-a-virtual-environment))
and run everything within the venv.

If you don't want to create and use a venv, the C API can be built using
following command:
```
mx python -m build_capi
```

You can test if everything was built correctly by, for example, using 
a memoryvew object:
```
(your-venv) graalpython -c "print(repr(memoryview(b'')))"
```

in the venv or

```
mx python -c "print(repr(memoryview(b'')))"
```

without a venv.

### Installing packages

Currently, not enough of the standard library is implemented to run the
standard package installers for many packages. As a convenience, we provide a
simple module to install packages that we know to be working (including
potential patches required for those packages). Try the following to find out
more:

```
graalpython -m ginstall --help
```

As a slightly more exciting example, try:

```
graalpython -m ginstall install numpy
```

If all goes well (you'll need to have `clang`, `llvm-link`, `llvm-extract`,
`llvm-nm`, and `opt` in your `PATH` in addition to the normal NumPy build
dependencies), you should be able to `import numpy` afterwards.

Support for more extension modules is high priority for us. We are actively
building out our support for the Python C API to make extensions such as NumPy,
SciPy, Scikit-learn, Pandas, Tensorflow and the like work. This work means that
some other extensions might also already work, but we're not actively testing
other extensions right now and cannot promise anything. Note that to try
extensions on this implementation, you have to download, build, and install them
manually for now. To do so, we recommend LLVM 6. Other versions might also work,
but this version of LLVM is what we're testing with in our CI.

### Polyglot Usage

We have a [document](doc/POLYGLOT.md) describing how we implement the
cross-language interop. This will hopefully give you an idea how to use it.

### Contributing

If you're thinking about contributing something to this repository, you will need
to sign the [Oracle Contributor
Agreement](http://www.graalvm.org/community/contributors/) for us to able to
merge your work. Please also take note of our [code of
conduct](http://www.graalvm.org/community/conduct/) for contributors.

To get you started, we have [written a bit](doc/CONTRIBUTING.md) about the
structure of this interpreter that should show how to fix things or add
features.

### Licensing

This GraalVM implementation of Python is copyright (c) 2017, 2019 Oracle and/or
its affiliates and is made available to you under the terms the Universal
Permissive License v 1.0 as shown at
[http://oss.oracle.com/licenses/upl](http://oss.oracle.com/licenses/upl). This
implementation is in part derived from and contains additional code from 3rd
parties, the copyrights and licensing of which is detailed in the
[LICENSE](LICENSE) and [3rd_party_licenses.txt](3rd_party_licenses.txt) files.

