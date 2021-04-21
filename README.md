# GraalVM Implementation of Python

This is an early-stage experimental implementation of Python. A primary goal is
to support SciPy and its constituent libraries. GraalPython can usually execute
pure Python code faster than CPython (but not when C extensions are
involved). GraalPython currently aims to be compatible
with Python 3.8, but it is a long way from there, and it is very likely that any
Python program that uses more features of standard library modules or external
packages will hit something unsupported. At this point, the Python
implementation is made available for experimentation and curious end-users.

### Trying it

The easiest option to try GraalPython is
[Pyenv](https://github.com/pyenv/pyenv/), the Python version manager. It allows
you to easily install different GraalPython releases. To get version 20.2, for
example, just run `pyenv install graalpython-20.2`.

To try GraalPython with a full GraalVM, including the support for Java embedding
and interop with other languages, you can use the bundled releases from
[www.graalvm.org](https://www.graalvm.org/downloads/).

If you want to build GraalPython from source, checkout this repository and the
[mx](https://github.com/graalvm/mx) build tool, and run `mx --dy /compiler
python-gvm` in the `graalpython` repository root. If the build is fine, it will
print the full path to the `graalpython` executable as the last line of output.

For more information and some examples of what you can do with GraalPython,
check out the [reference](https://www.graalvm.org/reference-manual/python/).

### Create a virtual environment

The best way of using the GraalVM implementation of Python is out of a virtual
environment. To create the venv, run the following:

```
graalpython -m venv <dir-to-venv>
```

To activate the environment in your shell session call:

```
source <dir-to-venv>/bin/activate
```

In the venv multiple executables are available, like `python`, `python3` and `graalpython`.


### Installing packages

At the moment not enough of the standard library is implemented to run the
standard package installers for many packages. As a convenience, we provide a
simple module to install packages that we know to be working (including
potential patches required for those packages). Try the following to find out
which packages are at least partially supported and tested by us in our CI:

```
graalpython -m ginstall install --help
```

As a slightly exciting example, try:

```
graalpython -m ginstall install pandas
```

If all goes well (also consider native dependencies of NumPy), you should be
able to `import numpy` and `import pandas` afterwards.

Support for more extension modules is high priority for us. We are actively
building out our support for the Python C API to make extensions such as NumPy,
SciPy, Scikit-learn, Pandas, Tensorflow and the like work fully. This work means
that some other extensions might also already work, but we're not actively
testing other extensions right now and cannot promise anything. Note that to try
other extensions on this implementation, you have to download, build, and
install them manually for now.

### Polyglot Usage

We have a [document](docs/user/Interoperability.md) describing how we implement the
cross-language interop. This will hopefully give you an idea how to use it.

### Jython Support

We are working on a mode that is "mostly compatible" with some of Jython's
features, minus of course that Jython implements Python 2.7 and we implement
Python 3.8+. We describe the current status of the compatibility mode
[here](docs/user/Jython.md).

### Contributing

If you're thinking about contributing something to this repository, you will need
to sign the [Oracle Contributor
Agreement](http://www.graalvm.org/community/contributors/) for us to able to
merge your work. Please also take note of our [code of
conduct](http://www.graalvm.org/community/conduct/) for contributors.

To get you started, we have [written a bit](docs/contributor/CONTRIBUTING.md) about the
structure of this interpreter that should show how to fix things or add
features.

### Licensing

This GraalVM implementation of Python is copyright (c) 2017, 2019 Oracle and/or
its affiliates and is made available to you under the terms the Universal
Permissive License v 1.0 as shown at
[http://oss.oracle.com/licenses/upl](http://oss.oracle.com/licenses/upl). This
implementation is in part derived from and contains additional code from 3rd
parties, the copyrights and licensing of which is detailed in the
[LICENSE](LICENSE) and [THIRD_PARTY_LICENSE](THIRD_PARTY_LICENSE.txt) files.
