Configuration
=============

Configuration Options
---------------------

tag (string)
    Short descriptor string of this Yamcs server. If present this is shown in the top bar. The primary motiviation for this option is to be able to distinguish between multiple Yamcs servers in distributed deployments.

displayPath (string)
    Filesystem path where to find display resources. If this is not specified, Yamcs stores displays in a regular bucket (in binary form). Using the filesystem allows to manage displays outside of Yamcs; for example with a versioning system.

stackPath (string)
    Filesystem path where to find command stacks. If this is not specified, Yamcs stores stacks in a regular bucket (in binary form). Using the filesystem allows to manage stacks outside of Yamcs; for example with a versioning system.

staticRoot (string)
    Filesystem path where to locate the web files for the Yamcs Web Interface (\*.js, \*.css, \*.html, ...). If not specified, Yamcs will search the classpath for these resources (preferred).

    It should only be necessary to use this option when doing development work on the Yamcs Web Interface. It allows to run ``npm`` in watch mode for a save-and-refresh development cycle.

twoStageCommanding (boolean)
    Indicates whether issuing commands should be protected from accidental clicks. If ``true`` issuing a command will require two clicks at least (arm-and-issue). This feature is primarily intended for operational environments.
    
    Default: ``false``.
