Command Options
===============

Yamcs supports a programmatic API for activating custom command options. When commands are issued with custom options, these can be interpreted by you own code, typically a TC data link.

Custom command options do not impact the encoding of telecommand packets, rather they are used for passing other instructions, such as at-runtime overriding of link properties.

Custom command options are added system-wide. Registered command options are available in all official clients wherever a command can be configured for sending.

Command options are automatically saved as attributes in Command History, and will also be received by all command/acknowledgment listeners.


Registration
------------

Command options must be registered against :javadoc:`org.yamcs.YamcsServer`. It is not possible to send custom options without the option being registered.

.. code-block:: java

    // Statically retrieve the current Yamcs server object.
    YamcsServer yamcs = YamcsServer.getServer();

    CommandOption option = new CommandOption(
        "cop1Bypass",  // System-wide unique identifier. Also stored in cmdhist.
        "COP-1 Bypass", // Verbose name for display in UI clients.
        CommandOptionType.BOOLEAN, // The expected type for hinting UI clients.
    );

    yamcs.addCommandOption(option);


A registration can only be done once, or else ``addCommandOption()`` will throw an exception. One way of doing so is to put this registration in the static initializer of the components that uses this option (e.g. a command link). Then the command option will only be loaded (and once only) when at least one such link is running.

An alternative method that avoids the use of static initializers, is to implement :javadoc:`org.yamcs.Plugin`, and then put the registration in the ``onLoad`` lifecycle hook. This hook is called once-only when the server is starting up.

.. code-block:: java

    public class MyPlugin implements Plugin {

        public static final CommandOption MY_OPTION = ...;
    
        public void onLoad(YConfiguration config) { // Called on start-up
            YamcsServer yamcs = YamcsServer.getServer();
            yamcs.addCommandOption(MY_OPTION);
        }
    }

.. note::
    Plugins must be packaged in a specific manner, before Yamcs can actually find and load them. This is documented separately.


Types
-----

There is support for these types: ``BOOLEAN``, ``NUMBER``, ``STRING`` and ``TIMESTAMP``. These types are only a hint for use by UI clients. For example, the Yamcs web interface will use these types to determine which UI controls to render in a dynamic form, and how to encode the values for persisting in Command History. The HTTP API will not check which :javadoc:`~org.yamcs.protobuf.Value` types are used. Submitted values are pushed end-to-end in a type-preserving manner.

The effective :javadoc:`~org.yamcs.protobuf.Value` type is intentionally loose, and depends on the client. The Yamcs web interface for example, will use double for submitting the value of any ``NUMBER`` options.


Permissions
-----------

The use of any command option requires the system privilege ``CommandOptions``.
