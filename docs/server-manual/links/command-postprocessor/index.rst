Command Postprocessor
=====================

When a data link is about to send an outgoing command, it is first provided to a so called *postprocessor*.

A postprocessor has the capability to modify the command binary, and so is used to set sequence counts, calculating checksums and any other bit manipulation that may need to occur.

.. toctree::
    :maxdepth: 1
    :caption: Command Postprocessor Implementations

    cfs
    csp
    pus
    generic
