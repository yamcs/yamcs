Web Interface
=============

Yamcs includes a web interface which provides quick access and control over many of its features. The web interface runs on port 8090 and integrates with the security system of Yamcs.

All pages are aware of the privileges of the logged in user and will hide user interface elements that the user has no permission for.

Most pages (the homepage excluding) show data specific to a particular Yamcs instance. The current instance is always indicated in the top bar. To switch to a different location either return to the homepage, or use the quick-switch dialog in the top bar. When switching instances the user is always redirected to the default page for that instance.

.. toctree::
    :maxdepth: 1
    :caption: Table of Contents

    configuration
    links
    algorithms
    telemetry
    events
    alarms
    commanding
    procedures
    activities
    timeline
    mdb
    archive
    admin/index
