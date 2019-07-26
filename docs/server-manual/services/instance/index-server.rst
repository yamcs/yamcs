Index Server
============

Supports retrieval of archive indexes and tags.


Class Name
----------

:javadoc:`org.yamcs.archive.IndexServer`


Configuration
-------------

This service is defined in ``etc/yamcs.(instance).yaml``. Example:

.. code-block:: yaml

    services:
      - class: org.yamcs.archive.IndexServer


Configuration Options
---------------------

tmIndexer (string)
    Class name of a :javadoc:`~org.yamcs.archive.TmIndex` implementation. Default is :javadoc:`org.yamcs.archive.CcsdsTmIndex` which applies CCSDS conventions.

streams (list of strings)
    The streams to index. When unspecified, all ``tm`` streams defined in ``streamConfig`` are indexed.
