yamcsadmin users create
=======================

.. program:: yamcsadmin users create

**NAME**

    yamcsadmin users create - Create a new user


**SYNOPSIS**

    .. code-block:: text

        yamcsadmin users create [--email EMAIL] [--display-name NAME]
            [--inactive] [--superuser] [--no-password] USERNAME


**DESCRIPTION**

    Create a new Yamcs user. This prompts for a password.

    The command may be used in non-interactive mode by setting the password with the environment variable ``YAMCSADMIN_PASSWORD``, or using the option ``--no-password``.


**POSITIONAL ARGUMENTS**

    .. option:: USERNAME

        The name of the new user.


**OPTIONS**

    .. option:: --display-name NAME

        Displayed name of the user.

    .. option:: --email EMAIL

        User email.
    
    .. option:: --inactive

        Add this flag to prevent Yamcs from activating the account.
    
    .. option:: --superuser

        Add this flag to grant this user superuser privileges.
    
    .. option:: --no-password

        Add this flag to indicate that this user should not have a password. This will also bypass the password prompt.
