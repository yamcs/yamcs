yamcsadmin users update
=======================

.. program:: yamcsadmin users update

**NAME**

    yamcsadmin users update - Update a user


**SYNOPSIS**

    .. code-block:: text

        yamcsadmin users update [--active true | false]
            [--display-name NAME] [--email EMAIL]
            [--superuser true | false] USERNAME


**POSITIONAL ARGUMENTS**

    .. option:: USERNAME

        The name of the user.


**OPTIONS**

    .. option:: --display-name NAME

        Displayed name of the user.

    .. option:: --email EMAIL

        User email.
    
    .. option:: --active true | false

        Whether the user account is active or not.
    
    .. option:: --superuser

        Whether the user has superuser privileges.
