Algorithm Manager
=================

Executes algorithms and provides output parameters.


Class Name
----------

:javadoc:`org.yamcs.algorithms.AlgorithmManager`


Configuration
-------------

This service is defined in :file:`etc/processor.yaml`. Example:

.. code-block:: yaml

    realtime:
      services:
        - class: org.yamcs.algorithms.AlgorithmManager
          args:
            libraries:
              JavaScript:
                - "mdb/mylib.js"


Configuration Options
---------------------

libraries (map)
    Libraries to be included in algorithms. The map points from the scripting language to a list of file paths.
