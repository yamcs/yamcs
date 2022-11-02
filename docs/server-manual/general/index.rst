General Information
===================


Yamcs Server, or short Yamcs, is a central component for monitoring and controlling remote devices. Yamcs stores and processes packets, and provides an interface for end-user applications to subscribe to realtime or archived data. Typical use cases for such applications include telemetry displays and commanding tools.

.. image:: _images/typical-deployment.png
    :alt: Typical Deployment
    :align: center

Yamcs ships with an embedded web server for administering the server, the mission databases or for basic monitoring tasks. For more advanced requirements, Yamcs exposes its functionality over a well-documented HTTP-based API.

Yamcs is implemented entirely in Java, but it does rely on an external storage engine for actual data archiving. Currently the storage engine is `RocksDB <http://rocksdb.org/>`_. The preferred target platform is Linux x64, but Yamcs can also be made to run on Mac OS X and Windows.

.. toctree::
    :maxdepth: 1
    :caption: Table of Contents

    model
    architecture
    time
