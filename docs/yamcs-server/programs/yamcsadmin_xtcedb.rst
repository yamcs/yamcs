yamcsadmin xtcedb
=================

**NAME**

    yamcsadmin xtcedb - Provides information about the XTCE database


**SYNOPSIS**

    ``yamcsadmin xtcedb [-f FILE] COMMAND``


**OPTIONS**

    .. program:: yamcsadmin xtcedb

    .. option:: -f FILE

        Use this file instead of default mdb.yaml


**COMMANDS**

    .. describe:: listConfigs

        List the MDB configurations defined in mdb.yaml

    .. describe:: print

        Print the contents of the XTCE DB

    .. describe:: verify

        Verify that the XTCE DB can be loaded


yamcsadmin xtcedb listConfigs
-----------------------------

**NAME**

    yamcsadmin xtcedb listConfigs - List the MDB configurations defined in mdb.yaml


**SYNOPSIS**

    ``yamcsadmin xtcedb listConfigs``


yamcsadmin xtcedb print
-----------------------

**NAME**

    yamcsadmin xtcedb print - Print the contents of the XTCE DB


**SYNOPSIS**

    ``yamcsadmin xtcedb print CONFIG``


**POSITIONAL ARGUMENTS**

    .. program:: yamcsadmin xtcedb print

    .. option:: CONFIG

        Config name.


yamcsadmin xtcedb verify
------------------------

**NAME**

    yamcsadmin xtcedb verify - Verify that the XTCE DB can be loaded


**SYNOPSIS**

    ``yamcsadmin xtcedb verify CONFIG``


**POSITIONAL ARGUMENTS**

    .. program:: yamcsadmin xtcedb verify

    .. option:: CONFIG

        Config name.
