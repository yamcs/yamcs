Mission Database
================

.. toctree::
    :maxdepth: 1
    :caption: Table of Contents

    parameter-definitions
    container-definitions
    alarm-definitions
    algorithm-definitions
    command-definitions
    loaders/index

The Mission Database describes the telemetry and commands that are processed by Yamcs. It tells Yamcs how to decode packets or how to encode telecommands.

The database organizes TM/TC definitions by **space system**. A space system may contain other sub-space systems, thereby structuring the definitions in logical groups. Space systems have a name and can be uniquely identified via UNIX-like paths starting from the root of the space system hierarchy. For example: ``/BogusSAT/SC001/BusElectronics`` could be the name of a sub-space system under ``/BogusSAT/SC001``. The root space system is ``/``.

The terminology used in the Yamcs Mission Database is very close to the terminology used in the XTCE exchange format. XTCE prescribes a useful set of building blocks: space systems, containers, parameters, commands, algorithms, etc.
