yamcsd
======

.. program:: yamcsd

**NAME**

    yamcsd - Yamcs Server


**SYNOPSIS**

    .. code-block:: text

        yamcsd [--version] [--help] [--check] [--log LEVEL] [--no-color]
               [--no-stream-redirect] [--etc-dir DIR]


**DESCRIPTION**

    yamcsd is a shell wrapper that launches a JVM running the YamcsServer main program.


**OPTIONS**

    .. option:: --log LEVEL

       Level of verbosity. From 0 (off) to 5 (all). Default: 2. This option only affects console logging, not file logging.

    .. option:: --no-color

       Add this flag to disable ANSI color codes used in console logging.

    .. option:: --no-stream-redirect

       Add this flag to prevent Yamcs from redirecting stdout/stderr output via the logging system.

    .. option:: --etc-dir DIR

       Path to config directory. This defaults to the etc directory relative to the working directory.

    .. option:: --check

       Run syntax tests on configuration files and quit.

    .. option:: -v, --version

       Print version information and quit.

    .. option:: -h, --help

       Show usage.
