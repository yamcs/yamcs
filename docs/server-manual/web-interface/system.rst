System
======

The System module within the Yamcs web interface provides a set of administrative views on Yamcs.

The System Module is always visited for a specific Yamcs instance, although some of the information may be global to Yamcs.


Dashboard
---------

This provides a quick glance at the running system. It shows a quick graph of JVM system parameters and provides some basic server information such as the version number of Yamcs.


Links
-----

Shows a live view of the data links for this instance. Link can be managed directly from this page.


Services
--------

Shows the services for this instance. The lifecycle of these services can be managed directly from this page.


Processors
----------

Shows a live view of the processors for this instance. This includes both persistent and non-persistent processors. Each processor has a detail page that allows seeing some statistics on the incoming packets, and that provides management controls over the command queues for this processor.


Clients
-------

Shows the clients connected to this instance.


Tables
------

Shows the archive tables for this instance. Each table has a detail page that shows details about its structure and SQL options and that provides a quick view at the raw data records.


Streams
-------

Shows the streams for this instance. Each stream has a detail page that shows details about its SQL definition.
