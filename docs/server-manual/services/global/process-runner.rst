Process Runner
==============

Runs an external process. If this process exits this Yamcs service stops too unless a ``restart`` option is configured and applicable.

The subprocess inherits environment variables set on Yamcs, and additionally includes the variable ``YAMCS=1``. Further environment variables can be configured.


Class Name
----------

:javadoc:`org.yamcs.ProcessRunner`


Configuration
-------------

This is a global service defined in :file:`etc/yamcs.yaml`. Example:

.. code-block:: yaml

    services:
      - class: org.yamcs.ProcessRunner
        args:
          command: "bin/simulator.sh"


Configuration Options
---------------------

command (string or string[])
    **Required.** Command (and optional arguments) to run.

directory (string)
    Set the working directory of the started subprocess. If unspecified, this defaults to the working directory of Yamcs.

environment (map)
    Pass custom environment variables to the subprocess.

logLevel (string)
    Level at which to log stdout/stderr output. One of ``INFO``, ``DEBUG``, ``TRACE``, ``WARN``, ``ERROR``. Default: ``INFO``

logPrefix (string)
    Prefix to prepend to all logged process output. If unspecified this defaults to ``[COMMAND]``.

restart (string)
    When to start a new process if the original process exits. One of ``always``, ``on-success``, ``on-failure`` or ``never``. Default: ``never``.

successExitCode (integer or integer[])
    Exit codes of the subprocess that are considered successful. This is used to evaluate the appropriate ``restart`` behavior. Default: ``0``.
