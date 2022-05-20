yamcsadmin users check-password
===============================

.. program:: yamcsadmin users check-password

**NAME**

    yamcsadmin users check-password - Check a user's password


**SYNOPSIS**

    ``yamcsadmin users check-password USERNAME``


**DESCRIPTION**

    Prompts to enter the user's current password. The command will print if the provided password is correct or not.

    The command may be used in non-interactive mode by setting the password with the environment variable ``YAMCSADMIN_PASSWORD``.


**POSITIONAL ARGUMENTS**

    .. option:: USERNAME

        The name of the user.
