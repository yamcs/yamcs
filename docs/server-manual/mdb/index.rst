Mission Database
================

The Mission Database describes the telemetry and commands that are processed by Yamcs. It tells Yamcs how to decode packets or how to encode telecommands.

The database organizes TM/TC definitions by *space system*. A space system may contain other sub-space systems, thereby structuring the definitions in logical groups. Space systems have a name and can be uniquely identified via UNIX-like paths starting from the root of the space system hierarchy. For example: ``/BogusSAT/SC001/BusElectronics`` could be the name of a sub-space system under ``/BogusSAT/SC001``. The root space system is ``/``.

The terminology used in the Yamcs Mission Database is very close to the terminology used in the XTCE exchange format. XTCE prescribes a useful set of building blocks: space systems, containers, parameters, commands, algorithms, etc.

Generally, the Mission DaStarting is read-only. Until version 5.8.8, Yamcs allowed overriding some aspects of the MDB: calibrators and alarms for parameters and algorithms. Those changes were not permanent and they applied only to one processor.

Starting with Yamcs 5.8.8., Yamcs allows designating some sub-trees of the MissionDatabase as read-write and allows adding/changing objects under those sub-systems. In Yamcs 5.8.8. it is possible to add/change Subsystems, Parameters and Parameter Types. In future versions this may be extended to other objects (containers, commands...). Yamcs will also persist the corresponding MDB tree on disk (in XTCE format) thus that the MDB will not lose the information when Yamcs restarts.


.. toctree::
    :maxdepth: 1
    :caption: Table of Contents

    data-types
    parameter-definitions
    container-definitions
    alarm-definitions
    algorithm-definitions
    command-definitions
    loaders/index
