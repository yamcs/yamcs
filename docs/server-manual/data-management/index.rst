Data Management
===============


Yamcs contains a generic data management system that combines two fundamental principles:

* Managing **static tables** of data.
* Managing **continuous streams** of data.

Both concepts are combined in a unifying **Stream SQL** language.

In addition, Yamcs contains a **Parameter Archive** that is specifically optimized for retrieval of parameter values. The Parameter Archive contains derived data and can be rebuilt at any time from the static database tables.

.. toctree::
    :maxdepth: 1
    :caption: Table of Contents

    streams
    archive/index
    parameter-archive/index
    buckets
