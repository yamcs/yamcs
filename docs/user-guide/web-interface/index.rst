Web Interface
=============

Yamcs includes a web interface which provides quick access and control over many of its features. The web interface runs on port 8090 and integrates with the security system of Yamcs.

The web interface is separated in three different modules:

* :doc:`monitor` provides typical monitoring capabilities (displays, events, ...)
* :doc:`mdb` provides an overview of the Mission Database (parameters, containers, ...)
* :doc:`system` provides administrative controls over Yamcs (tables, services, ...).

All modules are aware of the privileges of the logged in user and will hide user interface elements that the user has no permission for. For normal operations access to the Monitor and MDB section should be sufficient.

Most pages (the homepage excluding) show data specific to a particular Yamcs instance. The current instance is always indicated in the top bar. To switch to a different location either return to the homepage, or use the quick-switch dialog in the top bar. When switching instances the user is always redirect to the default page for that instance (i.e. the Display overview).


.. toctree::
    :hidden:

    monitor
    mdb
    system
