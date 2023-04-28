DB Shell
========

This page emulates a shell environment for executing low-level SQL queries on the Yamcs database.

For example:

.. code-block:: text
    :emphasize-lines: 3-13,17-23

    simulator> show tables

    +--------------+
    | name         |
    +--------------+
    |       alarms |
    |      cmdhist |
    | event_alarms |
    |       events |
    |           pp |
    |           tm |
    +--------------+
    6 rows in set

    simulator> select gentime, seqNum, pname from tm limit 2

    +-----------------------------+--------+---------------------------+
    |                     gentime | seqNum |                     pname |
    +-----------------------------+--------+---------------------------+
    | 2021-05-18 09:18:05.040 UTC |    880 | /YSS/SIMULATOR/FlightData |
    | 2021-05-18 09:18:06.040 UTC |    881 | /YSS/SIMULATOR/FlightData |
    +-----------------------------+--------+---------------------------+
    2 rows in set


.. note::

    The Yamcs SQL dialect is currently undocumented, and use of this shell is expected only for debugging or development purposes. Concepts such as packets, parameters and events are intended to be accessed using the high-level HTTP API, instead of SQL.
