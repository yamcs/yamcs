Data Links
==========

.. toctree::
    :maxdepth: 1
    :caption: Table of Contents

    artemis-parameter-data-link
    artemis-tm-data-link
    file-polling-tm-data-link
    tcp-tc-data-link
    tcp-tm-data-link
    tse-data-link
    udp-parameter-data-link
    udp-tm-data-link

Data Links represent special components that communicate with the external world. There are three types of Data Links: TM, TC and PP (processed parameters). TM and PP receive telemetry packets or parameters and inject them into the realtime or dump TM or PP streams. The TC data links subscribe to the realtime TC stream and send data to the external systems.

Data Links can report on their status and can also be controlled by an operator to connect or disconnect from their data source.
