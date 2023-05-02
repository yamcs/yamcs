yamcsd
======

.. program:: yamcsd

Synopsis
--------

.. rst-class:: synopsis

    | **yamcsd** [--version] [--help] [--check] [--log <*LEVEL*>] [--log-config <*FILE*>]
       [--no-color] [--no-stream-redirect] [--etc-dir <*DIR*>] [--data-dir <*DIR*>]
       [--cache-dir <*DIR*>] [--netty-leak-detection <*LEVEL*>]


Description
-----------

:program:`yamcsd` is a shell wrapper that launches a :abbr:`JVM (Java Virtual Machine)` running the Yamcs main program.


Options
-------

.. option:: --log <LEVEL>

   Level of verbosity. From 0 (off) to 4 (all). Default: 2. This option only affects console logging, not file logging. For high verbosity levels, this option should be combined with the option ``--log-config`` to reduce the amount of output to only selected individual loggers.

.. option:: --log-config <FILE>

   Finetune the log level of individual loggers. This option only affects console logging, not file logging. An example is given below. When this option is not specified, all loggers are active.

.. option:: --no-color

   Add this flag to disable ANSI color codes used in console logging.

.. option:: --no-stream-redirect

   Add this flag to prevent Yamcs from redirecting stdout/stderr output via the logging system.

.. option:: --etc-dir <DIR>

   Path to config directory. This defaults to the :file:`etc` directory relative to the working directory.

.. option:: --data-dir <DIR>

   Path to data directory. When unspecified the location is read from the :file:`etc/yamcs.yaml` configuration file.

.. option:: --cache-dir <DIR>

   Path to cache directory. When unspecified the location is read from the :file:`etc/yamcs.yaml` configuration file.

.. option:: --check

   Run syntax tests on configuration files and quit.

.. option:: --netty-leak-detection <LEVEL>

   Level of leak detection used by the Netty library. Leak detection is disabled by default as it has a negative impact on performance. The available levels are:

   DISABLED
      Disables leak detection (default)
   SIMPLE
      Samples 1% of all Netty resources and reports when a leak is detected. Small overhead, but difficult to tell what caused the leak.
   ADVANCED
      Samples 1% of all Netty resources and reports when a leak is detected and where the object was recently accessed. High overhead.
   PARANOID
      Tracks all Netty resources and reports when a leak is detected and where the object was recently accessed. Very high overhead.

   Note that leak detection triggers only upon a GC.

.. option:: -v, --version

   Print version information and quit.

.. option:: -h, --help

   Show usage.


Environment
-----------

The following environment variables may be specified.

.. describe:: YAMCS_DATA_DIR

    Path to data directory.

.. describe:: YAMCS_ETC_DIR

    Path to configuration directory.

.. describe:: YAMCS_CACHE_DIR

    Path to cache directory.

.. describe:: YAMCS_NO_COLOR, NO_COLOR

    Suppress colorized output. The ``NO_COLOR`` alias is a convention used by many other programs.


Log Config Example
------------------

The file specified with the option :option:`--log-config` must be in properties format, where keys represent a logger, and values represent the verbosity level of that logger. Unmentioned loggers are considered to be off (level = 0). Example:

.. code-block:: properties

    # Levels:
    # 0 = off
    # 1 = warnings and errors
    # 2 = info
    # 3 = debug
    # 4 = trace

    org.yamcs = 3
    org.yamcs.http = 1
    com.example.myproject = 4

Note that the effective log level of any specified logger is always ceiled to that of the :option:`--log` option.
