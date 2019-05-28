# Python Changelog

This changelog summarizes major changes between GraalVM versions of the Python
language runtime. The main focus is on user-observable behavior of the engine.

## Version 19.0.0

* Fix an issue preventing use of encodings in the installable binary
* Fix return value of process when `os.exit` is called with a boolean
* Fix interpretation of foreign objects to prefer interpreting them as integer over double
* Fix performance regression when repeatedly creating a new function in a loop

## Version 1.0.0 RC16

* No user-facing changes

## Version 1.0.0 RC15

* Implement PEP 487 `__init_subclass__`
* Implement PEP 560 `__class_getitem__` and `__mro_entries__`
* Migrate to Truffle libraries for interop
* Support the buffer protocol for mmap
* Support importing java classes using normal Python import syntax
* Improve performance of literal dictionary creation when the first but not all keys are strings
* Improve performance of getting the length of a string
* Improve performance of accessing defaults, keyword-defaults, and code of a function
* Fix getting file separator from the Truffle filesystem rather than the operating system
* Fix constructing and calling methods with non-function callables
* Fix execution of subprocesses with non-default python homes on JVM

## Version 1.0.0 RC14

* Mark a subset of the Graal Python launcher options as "stable". All other options are subject to change and need to be unlocked explicitly on the commandline.
* Automatically install pip when creating a venv. The socket and ssl libraries are still not functional, so pip can only install from local sources or wheels.
* Update the standard library to Python 3.7.0 from 3.6.5.
* Support the `-I` flag to ignore the user environment and not add the working directory to `sys.path`
* Fix an error preventing usage of the memtracer tool. If an object raised an exception in it's `__repr__` method, it would abort the execution.
* Fix issues around not being able to modify function defaults, keyword defaults, or re-defining a function with a different closure.
* Fix continuation prompt in the interactive Python shell when an incomplete statement was typed. Before it raised and ignored a SyntaxError.
* Fix frame restarting of Python functions in the Chrome debugger. Before, functions with closures would have their cells accidentally cleared.

## Version 1.0.0 RC13

* Support marshal.dumps and marshal.loads for code objects and some other built-in objects
* Fix installation of NumPy in a venv
* Initial support for module mmap
* Support debugging with workspace files in the Chrome debugger
* Support the PEP 553 breakpoint() message
* Support running weak reference callbacks and signals on the main thread

## Version 1.0.0 RC12

* Support the `__class__` variable in the class scope
* Support module-level docstrings
* Initial support for the `venv` standard-library tool
* Initial support for the built-in `_bz2` module
* Initial support for the `pandas` package
* Initial support for OSError subclasses based on the `errno` of the exception
* Fix bytearray inplace add to return the same object
* Fix access to standard Python methods (`__repr__`, `__str__`, `__len__` and the like) for foreign objects

## Version 1.0.0 RC11

* Support running setuptools to build and install various packages
* Support running a source release version of NumPy out of the box
* Improve performance of member access to C API objects
* Improve performance of binary operations on C API objects
* Add support for `yield from`
* Support assignment to `object.__dict__` and ensure that managed subclasses of native types also have a `__dict__`
* Fix `[]` access with non-integer keys for array-like foreign objects
* Fix various performance regressions introduced in the last RC
* Implement more built-in methods on the `time` module
* Python no longer exposes internal languages through `polyglot.eval`
* Improve performance of `os.scandir` and functions that build on it (such as `glob`)
* More correct implementation of standard streams, including buffering
* Properly support the `-m` switch to run modules
* Support the standard `zipfile` module
* Add the built-in `_cvs` module
* Add support for `__slots__`
* Allow arbitrary callable objects in methods, not only functions
* Report that we have a TTY console if we are launched on a Terminal through our launcher
* Add the `ginstall` custom module to install known packages such as NumPy and setuptools

## Version 1.0.0 RC10

* Improve performance of C API upcalls
* Improve performance of classmethods, staticmethods, `globals()`, and `locals()`
* Improve performance of various string and bytes operations
* Initial support for the `_thread` builtin module (actual multi-threading is still disabled, the API defaults to a dummy implementation)
* Implement the `zipimporter` module
* Support assignment to `object.__class__`
* Use the new Truffle filesystem API to get/set the current working directory
* Attempt our best to report side-effects in KEY_INFO
* The KEYS message now responds with attributes and methods, never dict keys
* Support the `input` builtin
* Add DEBUG launcher options for performance debugging
* Ensure context isolation for file descriptors and child PIDs
* Fix passing custom locals and globals through `exec` and `eval`
* Fixes to builtin `help`

## Version 1.0.0 RC9

* Support `help` in the builtin Python shell
* Add `readline` to enable history and autocompletion in the Python shell
* Add support for the -q, -E, -s, and -S Python launcher flags
* Improve display of foreign array-like objects
* Improve support for string and bytes regular expressions using our TRegex engine
* Support loading site-packages installed with easy_install
* Initial support for the `binascii` module

## Version 1.0.0 RC8

* Report allocations when the `--memtracer` option is used
* Initial support for `pickle` module

## Version 1.0.0 RC7

* Enhance the `java` interop builtin module with introspection utility methods

## Version 1.0.0 RC6

* Support regular expression patterns built from bytes by using CPython's sre module as a fallback engine to our own
* Support LLVM 5+ for C extension modules
* Introduce native sequence storage so that e.g. Python bytes exposed to C can be mutated
* Introduce lazy string concatenation to significantly speed up benchmarks where strings are concatenated repeatedly
* C-API improvements to support more scikit-learn code
* Fix our distinction between builtin functions, functions, and methods to make the classes for builtin functions equivalent to CPython
* Improve set, frozenset, and dict support
* Attach Python exceptions as cause to ImportErrors raised for C extension modules
* Update standard library to CPython 3.6.5
* Support more code object attributes
* Support constant type ids for objects that are interned on CPython
* Add collections.deque
* Document how to contribute
* Improve efficiency of generators
* Enable re-use of ASTs in multiple Contexts in the same Engine

## Version 1.0.0 RC5

* Generator expressions now properly evaluate their first iterator in the definition scope at definition time
* Fixes for embedders to ensure top scopes are stable and local scopes always contain TruffleObjects
* C-API improvements to support simple Cython modules
* Support recognition of Python source files with the polyglot launcher

## Version 1.0.0 RC4

* No changes

## Version 1.0.0 RC3

* Support for more String encodings
* Implement buffered I/O
* Remove our random module substitute and use the standard library implementation
* Fix a potential thread-safety problem with cached parse trees when multiple Python contexts are used from multiple threads
* Complete support for math module builtins
* C-API improvements to run simple scikit-learn and NumPy examples
* Support the buffer protocol to wrap arbitrary Python sequences with no copy into NumPy arrays

## Version 1.0.0 RC2

* Updates to the polyglot and embedding APIs
* Many additions to the language core implementation
* Performance improvements to the parser
* C-API improvements to compile and run simple C extensions
* Support breakpoints on caught and uncaught exceptions in debugger

## Version 1.0.0 RC1

* LICENSE set to The Universal Permissive License (UPL), Version 1.0.
