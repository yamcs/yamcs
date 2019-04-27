Index Server
============

Supports retrieval of archive indexes and tags.


Class Name
----------

:javadoc:`org.yamcs.archive.IndexServer`


Configuration
-------------

This service is defined in ``etc/yamcs.(instance).yaml``. Example from a typical deployment:

.. code-block:: yaml
    :caption: yamcs.simulator.yaml

    services:
      - class: org.yamcs.archive.IndexServer


Configuration Options
---------------------

tmIndexer (string)
    Class name of a :javadoc:`~org.yamcs.archive.TmIndex` implementation. Default is :javadoc:`org.yamcs.archive.CcsdsTmIndex` which applies CCSDS conventions.
