Empty Node
==========

This loader allows to create an empty node in the space system hierarchy with a given name.

For example this configuration will create two parallel nodes ``/N1`` and ``/N2`` and underneath each of them, load the xls files of the simulator.

.. code-block:: yaml

    mdb:
      - type: "emptyNode"
        spec: "N1"
        subLoaders:
          - type: "sheet"
            spec: "mdb/simulator-ccsds.xls"
            subLoaders:
              - type: "sheet"
                spec: "mdb/landing.xls"
  
      - type: "emptyNode"
        spec: "N2"
        subLoaders:
          - type: "sheet"
            spec: "mdb/simulator-ccsds.xls"
            subLoaders:
              - type: "sheet"
                spec: "mdb/landing.xls"
