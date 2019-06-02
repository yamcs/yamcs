Superuser
=========

A user may have the attribute ``superuser``. Such a user is not subject to privilege checking. Any check of any kind will automatically pass. An example of such a user is the ``System`` user which is used internally by Yamcs on some actions that cannot be tied to a specific user. The ``superuser`` attribute may also be assigned to end users if the AuthModule supports it.
