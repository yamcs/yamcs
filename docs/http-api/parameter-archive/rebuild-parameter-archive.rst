Rebuild Parameter Archive
=========================

Rebuild (backfill) parts of the parameter archive::

    POST /api/archive/:instance/parameterArchive/rebuild

The back filler has to be enabled for this purpose. The back filling process does not remove data but just overwrites it. That means that if the parameter replay returns less parameters than originally stored in the archive, the old parameters will still be found in the archive.

It also means that if a replay returns the parameter of a different type than originally stored, the old ones will still be stored. This is because the parameter archive treats parameter with the same name but different type as different parameters. Each of them is given an id and the id is stored in the archive.

.. seealso::

    :doc:`delete-partitions`
        For removing data from the archive. Currently this can be done only for entire partitions. A partition is approximatively 25 days (2\ :sup:`31` milliseconds).


.. rubric:: Parameters

start (string)
    Start rebuilding from here. Specify a date string in ISO 8601 format.

stop (string)
    Rebuild until here. Specify a date string in ISO 8601 format.


.. note::

    The archive is build in segments of approximatively 70 minutes, therefore the real start will be before the specified start and the real stop will be after the specified stop.
