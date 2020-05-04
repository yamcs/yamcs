Services
========

Yamcs functionality is modularised into different services, representing objects with operational state, with methods to start and stop. Yamcs acts as a container for services, each running in a different thread. Services carry out a specific function. Some services are vital to core functionality, others can be thought of as more optional and give Yamcs its pluggable nature.

Services appear at different conceptual levels:

* **Global services** provide functionality across all instances.
* **Instance services** provide functionality for one specific instance.


.. toctree::
    :maxdepth: 2

    global/index
    instance/index
