yamcsd
======

.. program:: yamcsd

**NAME**

    yamcsd - Yamcs Server


**SYNOPSIS**

    .. code-block:: text

        yamcsd [--version] [--help] [--check] [--log LEVEL] [--log-config FILE]
               [--no-color] [--no-stream-redirect] [--etc-dir DIR]
               [--data-dir DIR]


**DESCRIPTION**

    yamcsd is a shell wrapper that launches a JVM running the YamcsServer main program.


**OPTIONS**

    .. option:: --log LEVEL

       Level of verbosity. From 0 (off) to 4 (all). Default: 2. This option only affects console logging, not file logging. For high verbosity levels, this option should be combined with the option ``--log-config`` to reduce the amount of output to only selected individual loggers.

    .. option:: --log-config FILE

       Finetune the log level of individual loggers. This option only affects console logging, not file logging. An example is given below. When this option is not specified, all loggers are active.

    .. option:: --no-color

       Add this flag to disable ANSI color codes used in console logging.

    .. option:: --no-stream-redirect

       Add this flag to prevent Yamcs from redirecting stdout/stderr output via the logging system.

    .. option:: --etc-dir DIR

       Path to config directory. This defaults to the etc directory relative to the working directory.

    .. option:: --data-dir DIR

       Path to data directory. When unspecified the location is read from the ``yamcs.yaml`` configuration file.

    .. option:: --check

       Run syntax tests on configuration files and quit.

    .. option:: -v, --version

       Print version information and quit.

    .. option:: -h, --help

       Show usage.


**LOG CONFIG EXAMPLE**

The file specified with the option ``--log-config`` must be in properties format, where keys represent a logger, and values represent the verbosity level of that logger. Unmentioned loggers are considered to be off (level = 0). Example:

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

Note that the effective log level of any specified logger is always ceiled to that of the ``--log`` option.
