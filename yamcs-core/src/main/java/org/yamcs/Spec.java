package org.yamcs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Specifies the valid structure of a {@link YConfiguration} instance.
 * <p>
 * While not strictly 'validation', the spec also allows defining additional metadata like the 'default' keyword which
 * is used in the merged result of a validation.
 * <p>
 * Furthermore, a spec validation applies a limited set of type transformations.
 */
public class Spec {

    private static final Logger log = LoggerFactory.getLogger(Spec.class);

    /**
     * Spec implementation that allows any key.
     */
    public static final Spec ANY = new Spec();
    static {
        ANY.allowUnknownKeys = true;
    }

    private static final Spec OPTION_DESCRIPTOR = new Spec();
    static {
        OPTION_DESCRIPTOR.addOption("title", OptionType.STRING).withRequired(true);
        OPTION_DESCRIPTOR.addOption("description", OptionType.LIST_OR_ELEMENT)
                .withElementType(OptionType.STRING);
        OPTION_DESCRIPTOR.addOption("type", OptionType.STRING)
                .withRequired(true)
                .withChoices(OptionType.class);
        OPTION_DESCRIPTOR.addOption("required", OptionType.BOOLEAN).withDefault(false);
        OPTION_DESCRIPTOR.addOption("secret", OptionType.BOOLEAN).withDefault(false);
        OPTION_DESCRIPTOR.addOption("hidden", OptionType.BOOLEAN).withDefault(false);
        OPTION_DESCRIPTOR.addOption("default", OptionType.ANY);
        OPTION_DESCRIPTOR.addOption("versionAdded", OptionType.STRING);
        OPTION_DESCRIPTOR.addOption("deprecationMessage", OptionType.STRING);
        OPTION_DESCRIPTOR.addOption("elementType", OptionType.STRING)
                .withChoices(OptionType.class);
        OPTION_DESCRIPTOR.addOption("choices", OptionType.LIST)
                .withElementType(OptionType.ANY);
        OPTION_DESCRIPTOR.addOption("suboptions", OptionType.MAP)
                .withSpec(ANY);
        OPTION_DESCRIPTOR.addOption("applySpecDefaults", OptionType.BOOLEAN)
                .withDefault(false);
    }

    private Map<String, Option> options = new LinkedHashMap<>();
    private Map<String, String> aliases = new HashMap<>();

    private boolean allowUnknownKeys = false;
    private List<List<String>> requiredOneOfGroups = new ArrayList<>(0);
    private List<List<String>> requireTogetherGroups = new ArrayList<>(0);
    private List<List<String>> mutuallyExclusiveGroups = new ArrayList<>(0);
    private List<WhenCondition> whenConditions = new ArrayList<>(0);

    /**
     * Returns true if this spec contains the specified option.
     */
    public boolean containsOption(String name) {
        return options.containsKey(name);
    }

    /**
     * Add an {@link Option} to this spec.
     * 
     * @throws IllegalArgumentException
     *             if an option with this name is already defined.
     */
    public Option addOption(String name, OptionType type) {
        if (options.containsKey(name) || aliases.containsKey(name)) {
            throw new IllegalArgumentException("Option '" + name + "' is already defined");
        }
        var option = new Option(this, name, type);
        options.put(name, option);
        return option;
    }

    /**
     * Remove an {@link Option} from this spec.
     */
    public void removeOption(String name) {
        options.remove(name);
    }

    public void allowUnknownKeys(boolean allowUnknownKeys) {
        this.allowUnknownKeys = allowUnknownKeys;
    }

    /**
     * Specify a set of keys of which at least one must be specified. Note that this not enforce that only one is
     * specified. You can combine this check with {@link #mutuallyExclusive(String...)} if that is required.
     */
    public void requireOneOf(String... keys) {
        verifyKeys(keys);
        requiredOneOfGroups.add(Arrays.asList(keys));
    }

    /**
     * Specify a set of keys that must appear together. This check only applies as soon as at least one of these keys
     * has been specified.
     */
    public void requireTogether(String... keys) {
        verifyKeys(keys);
        requireTogetherGroups.add(Arrays.asList(keys));
    }

    /**
     * Specify a set of keys that are mutually exclusive. i.e. at most one of them may be specified.
     */
    public void mutuallyExclusive(String... keys) {
        verifyKeys(keys);
        mutuallyExclusiveGroups.add(Arrays.asList(keys));
    }

    /**
     * Add a condition that is only verified when {@code key.equals(value)}
     * 
     * @param key
     *            the name of an option
     * @param value
     *            the value that triggers the conditional check
     * @return an instance of {@link WhenCondition} for further configuration options
     */
    public WhenCondition when(String key, Object value) {
        verifyKeys(key);
        var whenCondition = new WhenCondition(this, key, value);
        whenConditions.add(whenCondition);
        return whenCondition;
    }

    /**
     * Validate the given arguments according to this spec.
     * 
     * @param args
     *            the arguments to validate.
     * @return the validation result where defaults have been added to the input arguments
     * @throws ValidationException
     *             when the specified arguments did not match this specification
     */
    public YConfiguration validate(YConfiguration args) throws ValidationException {
        var ctx = new ValidationContext(args.getPath());
        var result = doValidate(ctx, args.getRoot(), "", false);
        var wrapped = YConfiguration.wrap(result);
        wrapped.parent = args.parent;
        wrapped.parentKey = args.parentKey;
        wrapped.rootLocation = args.rootLocation;
        return wrapped;
    }

    /**
     * Validate the given arguments according to this spec.
     * 
     * @param args
     *            the arguments to validate, keyed by argument name.
     * @return the validation result where defaults have been added to the input arguments
     * @throws ValidationException
     *             when the specified arguments did not match this specification
     */
    public Map<String, Object> validate(Map<String, Object> args) throws ValidationException {
        return doValidate(new ValidationContext(""), args, "", false);
    }

    private Map<String, Object> doValidate(ValidationContext ctx, Map<String, Object> args, String parent,
            boolean suppressWarnings)
            throws ValidationException {

        for (var group : requiredOneOfGroups) {
            if (count(args, group) == 0) {
                var msg = "One of the following is required: " + group;
                if (!"".equals(parent)) {
                    msg += " at " + parent;
                }
                throw new ValidationException(ctx, msg);
            }
        }

        for (var group : mutuallyExclusiveGroups) {
            if (count(args, group) > 1) {
                var msg = "The following arguments are mutually exclusive: " + group;
                if (!"".equals(parent)) {
                    msg += " at " + parent;
                }
                throw new ValidationException(ctx, msg);
            }
        }

        for (var group : requireTogetherGroups) {
            int n = count(args, group);
            if (n > 0 && n != group.size()) {
                var msg = "The following arguments are required together: " + group;
                if (!"".equals(parent)) {
                    msg += " at " + parent;
                }
                throw new ValidationException(ctx, msg);
            }
        }

        for (var whenCondition : whenConditions) {
            var arg = args.get(whenCondition.key);
            if (arg != null && arg.equals(whenCondition.value)) {
                var missing = whenCondition.requiredKeys.stream()
                        .filter(key -> !args.containsKey(key))
                        .collect(Collectors.toList());
                if (!missing.isEmpty()) {
                    var path = "".equals(parent) ? whenCondition.key : (parent + "->" + whenCondition.key);
                    throw new ValidationException(ctx, String.format(
                            "%s is %s but the following arguments are missing: %s",
                            path, whenCondition.value, missing));
                }
            }
        }

        // Build a new set of args where defaults have been entered
        // Makes this a linked hashmap to keep the defined order
        var result = new LinkedHashMap<String, Object>();

        // Check the provided arguments
        for (var entry : args.entrySet()) {
            var argName = entry.getKey();
            var path = "".equals(parent) ? argName : (parent + "->" + argName);

            var option = getOption(argName);
            if (option == null) {
                if (allowUnknownKeys) {
                    result.put(argName, entry.getValue());
                } else {
                    throw new ValidationException(ctx, "Unknown argument " + path);
                }
            } else if (result.containsKey(option.name)) {
                throw new ValidationException(ctx,
                        String.format("Argument '%s' already specified. Check for aliases.", option.name));
            } else {
                var arg = entry.getValue();
                var resultArg = option.validate(ctx, arg, path, suppressWarnings);
                result.put(option.name, resultArg);
            }
        }

        for (var option : options.values()) {
            var specified = args.containsKey(option.name);
            for (var alias : aliases.entrySet()) {
                if (alias.getValue().equals(option.name) && args.containsKey(alias.getKey())) {
                    specified = true;
                }
            }

            if (!specified) {
                var path = "".equals(parent) ? option.name : parent + "->" + option.name;
                if (option.required) {
                    throw new ValidationException(ctx, "Missing required argument " + path);
                }

                var defaultValue = option.validate(ctx, option.computeDefaultValue(), path,
                        true /* suppressWarnings */);
                if (defaultValue != null) {
                    result.put(option.name, defaultValue);
                }
            }
        }

        return result;
    }

    public Collection<Option> getOptions() {
        return options.values();
    }

    public boolean isAllowUnknownKeys() {
        return allowUnknownKeys;
    }

    public List<List<String>> getRequiredOneOfGroups() {
        return requiredOneOfGroups;
    }

    public List<List<String>> getRequireTogetherGroups() {
        return requireTogetherGroups;
    }

    public List<WhenCondition> getWhenConditions() {
        return whenConditions;
    }

    public Option getOption(String key) {
        key = aliases.getOrDefault(key, key);
        return options.get(key);
    }

    public List<String> getAliases(Option option) {
        return aliases.entrySet().stream()
                .filter(entry -> option.name.equals(entry.getValue()))
                .map(entry -> entry.getKey())
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Returns a copy of the given arguments but with all secret arguments recursively removed.
     * <p>
     * This method does not validate the arguments, however it will throw random exceptions if the input does not match
     * the expected structure. It is therefore best to validate the arguments before passing them.
     */
    public Map<String, Object> removeSecrets(Map<String, Object> unsafeArgs) {
        return makeSafe(unsafeArgs, false);
    }

    /**
     * Returns a copy of the given arguments but with all secret arguments masked as {@code *****}.
     * <p>
     * This method does not validate the arguments, however it will throw random exceptions if the input does not match
     * the expected structure. It is therefore best to validate the arguments before passing them.
     */
    public Map<String, Object> maskSecrets(Map<String, Object> unsafeArgs) {
        return makeSafe(unsafeArgs, true);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> makeSafe(Map<String, Object> unsafeArgs, boolean mask) {
        var safeArgs = new LinkedHashMap<String, Object>();
        for (var arg : unsafeArgs.entrySet()) {
            var option = getOption(arg.getKey());
            if (option == null) {
                // No exception. Often this method is called while we are already
                // handling another exception.
                safeArgs.put(arg.getKey(), arg.getValue());
                continue;
            }

            var type = option.type;
            var argValue = arg.getValue();
            if (type == OptionType.LIST_OR_ELEMENT && !(argValue instanceof List)) {
                type = OptionType.LIST;
                argValue = Arrays.asList(argValue);
            }

            if (option.secret) {
                if (mask) {
                    safeArgs.put(arg.getKey(), "*****");
                }
            } else if (type == OptionType.MAP) {
                var map = (Map<String, Object>) argValue;
                var safeMap = option.spec.makeSafe(map, mask);
                safeArgs.put(arg.getKey(), safeMap);
            } else if (type == OptionType.LIST) {
                var list = (List<Object>) argValue;
                var safeList = new ArrayList<>();
                for (var element : list) {
                    if (option.elementType == OptionType.MAP) {
                        var mapElement = (Map<String, Object>) element;
                        var safeMapElement = option.spec.makeSafe(mapElement, mask);
                        if (!safeMapElement.isEmpty()) {
                            safeList.add(safeMapElement);
                        }
                    } else {
                        safeList.add(element);
                    }
                }
                safeArgs.put(arg.getKey(), safeList);
            } else {
                safeArgs.put(arg.getKey(), argValue);
            }
        }
        return safeArgs;
    }

    private void verifyKeys(String... keys) {
        for (var key : keys) {
            if (!options.containsKey(key)) {
                throw new IllegalArgumentException("Unknown option " + key);
            }
        }
    }

    private int count(Map<String, Object> args, List<String> check) {
        return (int) check.stream()
                .filter(args::containsKey)
                .count();
    }

    public static enum OptionType {

        /**
         * Arguments for an ANY option are unvalidated.
         */
        ANY,

        BOOLEAN,
        INTEGER,
        FLOAT,
        LIST,

        /**
         * This option converts arguments automatically to a list if the argument is not a list.
         */
        LIST_OR_ELEMENT,

        MAP,
        STRING;

        Object convertArgument(ValidationContext ctx, String path, Object arg, OptionType elementType)
                throws ValidationException {
            if (this == ANY) {
                return arg;
            }

            if (arg == null) {
                return null;
            }

            if (arg instanceof String) {
                try {
                    arg = YConfiguration.expandString(null, (String) arg);
                } catch (ConfigurationException e) {
                    throw new ValidationException(ctx, String.format("%s: %s", path, e));
                }
            }

            var argType = forArgument(arg);
            if (this == argType) {
                return arg;
            } else if (this == LIST_OR_ELEMENT) {
                if (argType == LIST) {
                    return arg;
                } else {
                    var elementArg = elementType.convertArgument(ctx, path, arg, null);
                    return Arrays.asList(elementArg);
                }
            } else if (this == INTEGER) {
                if (arg instanceof String) {
                    var stringValue = (String) arg;
                    try {
                        return Integer.parseInt(stringValue);
                    } catch (NumberFormatException e) {
                        try {
                            return Long.parseLong(stringValue);
                        } catch (NumberFormatException e2) {
                            throw new ValidationException(
                                    ctx, String.format("%s: invalid integer '%s'", path, stringValue));
                        }
                    }
                } else if ((arg instanceof Float) && (((Float) arg) % 1) == 0) {
                    return ((Float) arg).intValue();
                } else if ((arg instanceof Double) && ((Double) arg % 1) == 0) {
                    return ((Double) arg).intValue();
                }
            } else if (this == FLOAT) {
                if (arg instanceof Integer) {
                    return Double.valueOf((Integer) arg);
                } else if (arg instanceof Long) {
                    return Double.valueOf((Long) arg);
                } else if (arg instanceof String) {
                    var stringValue = (String) arg;
                    try {
                        return Double.parseDouble(stringValue);
                    } catch (NumberFormatException e) {
                        throw new ValidationException(
                                ctx, String.format("%s: invalid float '%s'", path, stringValue));
                    }
                }
            } else if (this == BOOLEAN) {
                if (arg instanceof String) {
                    var stringValue = (String) arg;
                    switch (stringValue) {
                    case "yes":
                    case "true":
                    case "on":
                        return true;
                    case "no":
                    case "false":
                    case "off":
                        return false;
                    default:
                        // Fall
                    }
                }
            }
            throw new ValidationException(ctx, String.format(
                    "%s is of type %s, but should be %s instead",
                    path, argType, this));
        }

        static OptionType forArgument(Object arg) {
            if (arg instanceof String || arg instanceof Enum) {
                return STRING;
            } else if (arg instanceof Boolean) {
                return BOOLEAN;
            } else if (arg instanceof Integer || arg instanceof Long) {
                return INTEGER;
            } else if (arg instanceof Float || arg instanceof Double) {
                return FLOAT;
            } else if (arg instanceof List) {
                return LIST;
            } else if (arg instanceof Map) {
                return MAP;
            } else if (arg == null) {
                throw new IllegalArgumentException("Cannot derive type for null argument");
            } else {
                throw new IllegalArgumentException(
                        "Cannot derive type for argument of class " + arg.getClass().getName());
            }
        }
    }

    public static final class WhenCondition {

        private Spec spec;
        private String key;
        private Object value;
        private List<String> requiredKeys = new ArrayList<>();

        public WhenCondition(Spec spec, String key, Object value) {
            this.spec = spec;
            this.key = key;
            this.value = value;
        }

        public WhenCondition requireAll(String... keys) {
            spec.verifyKeys(keys);
            for (var key : keys) {
                requiredKeys.add(key);
            }
            return this;
        }

        public String getKey() {
            return key;
        }

        public Object getValue() {
            return value;
        }

        public List<String> getRequiredKeys() {
            return requiredKeys;
        }
    }

    public static final class Option {

        private final Spec parentSpec;
        private final String name;
        private final OptionType type;
        private String title;
        private List<String> description;
        private boolean required;
        private boolean secret;
        private boolean hidden;
        private Object defaultValue;
        private OptionType elementType;
        private String versionAdded;
        private String deprecationMessage;
        private List<Object> choices;
        private Spec spec;
        private boolean applySpecDefaults;

        public Option(Spec parentSpec, String name, OptionType type) {
            this.parentSpec = parentSpec;
            this.name = name;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public OptionType getType() {
            return type;
        }

        public boolean isRequired() {
            return required;
        }

        public boolean isHidden() {
            return hidden;
        }

        public boolean isSecret() {
            return secret;
        }

        public OptionType getElementType() {
            return elementType;
        }

        public String getTitle() {
            return title;
        }

        public List<String> getDescription() {
            return description;
        }

        public String getVersionAdded() {
            return versionAdded;
        }

        public String getDeprecationMessage() {
            return deprecationMessage;
        }

        public List<Object> getChoices() {
            return choices;
        }

        public Spec getSpec() {
            return spec;
        }

        public boolean isApplySpecDefaults() {
            return applySpecDefaults;
        }

        public Object getDefaultValue() {
            return defaultValue;
        }

        public Option withTitle(String title) {
            this.title = title;
            return this;
        }

        public Option withDescription(String... description) {
            this.description = Arrays.asList(description);
            return this;
        }

        /**
         * Set whether this option is required.
         */
        public Option withRequired(boolean required) {
            this.required = required;
            return this;
        }

        /**
         * Hint that this option should be hidden from UIs.
         */
        public Option withHidden(boolean hidden) {
            this.hidden = hidden;
            return this;
        }

        /**
         * Set whether this option is secret.
         * 
         * Secret options are not printed in log files.
         */
        public Option withSecret(boolean secret) {
            this.secret = secret;
            return this;
        }

        /**
         * Sets the default value. This is used only if the option is not required.
         */
        public Option withDefault(Object defaultValue) {
            if (defaultValue instanceof Enum) {
                this.defaultValue = ((Enum<?>) defaultValue).name();
            } else {
                this.defaultValue = defaultValue;
            }
            return this;
        }

        /**
         * In case the {@link #type} is set to {@link OptionType#LIST} or {@link OptionType#LIST_OR_ELEMENT} the element
         * type indicates the type of each element of that list.
         */
        public Option withElementType(OptionType elementType) {
            if (type != OptionType.LIST && type != OptionType.LIST_OR_ELEMENT) {
                throw new IllegalArgumentException("Element type can only be set on LIST or LIST_OR_ELEMENT");
            }
            this.elementType = elementType;
            return this;
        }

        /**
         * Which version of the software this specific option was added. For example: "1.2.3". In plugins, this must be
         * the plugin version, not the Yamcs version.
         */
        public Option withVersionAdded(String versionAdded) {
            this.versionAdded = versionAdded;
            return this;
        }

        /**
         * Attach a deprecation message to this option.
         */
        public Option withDeprecationMessage(String deprecationMessage) {
            this.deprecationMessage = deprecationMessage;
            return this;
        }

        /**
         * Sets the allowed values of this option.
         */
        public Option withChoices(Object... choices) {
            this.choices = Arrays.asList(choices);
            return this;
        }

        /**
         * Sets the allowed values of this option based on the states of an Enum.
         */
        public <T extends Enum<T>> Option withChoices(Class<T> enumClass) {
            return withChoices(EnumSet.allOf(enumClass).stream()
                    .map(Enum::name)
                    .toArray());
        }

        /**
         * Add aliases for this option. During validation the alias will be converted to the real option name.
         */
        public Option withAliases(String... aliases) {
            for (var alias : aliases) {
                if (parentSpec.options.containsKey(alias)) {
                    throw new IllegalArgumentException("Option '" + alias + "' is already defined");
                }
                parentSpec.aliases.put(alias, name);
            }
            return this;
        }

        /**
         * In case the {@link #type} or the {@link #elementType} is set to {@link OptionType#MAP} this specifies the
         * options within that map.
         */
        public Option withSpec(Spec spec) {
            this.spec = spec;
            return this;
        }

        /**
         * In case the {@link #type} is set to {@link OptionType#MAP}, setting this property to {@code true} will cause
         * defaults within elements of that type to be applied even if the option itself is not defined.
         * <p>
         * Note that this is not a recursive property. You need to specify at every level if so required.
         */
        public Option withApplySpecDefaults(boolean applySpecDefaults) {
            this.applySpecDefaults = applySpecDefaults;
            return this;
        }

        @SuppressWarnings("unchecked")
        private Object validate(ValidationContext ctx, Object arg, String path, boolean suppressWarnings)
                throws ValidationException {
            if (deprecationMessage != null && !suppressWarnings) {
                log.warn("Argument {} has been deprecated: {}", path, deprecationMessage);
            }
            if (arg == null) {
                return null;
            }

            arg = type.convertArgument(ctx, path, arg, elementType);

            if (choices != null && !choices.contains(arg)) {
                throw new ValidationException(ctx, String.format(
                        "%s should be one of %s", name, choices));
            }

            if (type == OptionType.MAP || ((type == OptionType.LIST || type == OptionType.LIST_OR_ELEMENT)
                    && elementType == OptionType.MAP)) {
                if (spec == null) {
                    throw new ValidationException(ctx, String.format(
                            "%s cannot be validated since it does not have a specification.", path));
                }
            }

            if (type == OptionType.LIST || type == OptionType.LIST_OR_ELEMENT) {
                var resultList = new ArrayList<>();
                var it = ((List<Object>) arg).listIterator();
                while (it.hasNext()) {
                    var elPath = path + "[" + it.nextIndex() + "]";
                    var argElement = it.next();
                    argElement = elementType.convertArgument(ctx, elPath, argElement, null);

                    if (elementType == OptionType.LIST) {
                        throw new UnsupportedOperationException("List of lists cannot be validated");
                    } else if (elementType == OptionType.MAP) {
                        var m = (Map<String, Object>) argElement;
                        var resultArg = spec.doValidate(ctx, m, elPath, suppressWarnings);
                        resultList.add(resultArg);
                    } else {
                        resultList.add(argElement);
                    }
                }
                return resultList;
            } else if (type == OptionType.MAP) {
                return spec.doValidate(ctx, (Map<String, Object>) arg, path, suppressWarnings);
            } else {
                return arg;
            }
        }

        private Object computeDefaultValue() {
            if (defaultValue != null) {
                if (type == OptionType.LIST_OR_ELEMENT && !(defaultValue instanceof List)) {
                    return Arrays.asList(defaultValue);
                }
                return defaultValue;
            }
            if (applySpecDefaults) {
                var result = new LinkedHashMap<String, Object>();
                for (var option : spec.options.values()) {
                    var subDefaultValue = option.computeDefaultValue();
                    if (subDefaultValue != null) {
                        result.put(option.name, subDefaultValue);
                    }
                }
                return result;
            }
            return null;
        }
    }

    /**
     * A specialized {@link Spec} that also has a name.
     * 
     * The intended usage is when a {@link Spec} is defined for the value of a mapping key, and this mapping key is also
     * to be specified.
     */
    public static final class NamedSpec extends Spec {

        private String name;

        public NamedSpec(String name) {
            this.name = Objects.requireNonNull(name);
        }

        public String getName() {
            return name;
        }
    }

    /**
     * Creates a spec object based on a option descriptors.
     */
    @SuppressWarnings("unchecked")
    public static Spec fromDescriptor(Map<String, Map<String, Object>> optionDescriptors) throws ValidationException {
        var spec = new Spec();
        for (var entry : optionDescriptors.entrySet()) {
            var optionName = entry.getKey();
            var optionDescriptor = OPTION_DESCRIPTOR.validate(entry.getValue());

            var option = spec.addOption(optionName, OptionType.valueOf((String) optionDescriptor.get("type")))
                    .withTitle((String) optionDescriptor.get("title"))
                    .withDefault(optionDescriptor.get("default"))
                    .withRequired((boolean) optionDescriptor.get("required"))
                    .withHidden((boolean) optionDescriptor.get("hidden"))
                    .withSecret((boolean) optionDescriptor.get("secret"))
                    .withVersionAdded((String) optionDescriptor.get("versionAdded"))
                    .withDeprecationMessage((String) optionDescriptor.get("deprecationMessage"));
            if (optionDescriptor.containsKey("description")) {
                option.withDescription(((List<String>) optionDescriptor.get("description")).toArray(new String[0]));
            }
            if (optionDescriptor.containsKey("elementType")) {
                option.withElementType(OptionType.valueOf((String) optionDescriptor.get("elementType")));
            }
            if (optionDescriptor.containsKey("suboptions")) {
                var suboptionDescriptors = (Map<String, Map<String, Object>>) optionDescriptor.get("suboptions");
                var subspec = fromDescriptor(suboptionDescriptors);
                option.withSpec(subspec);
                option.withApplySpecDefaults((boolean) optionDescriptor.get("applySpecDefaults"));
            }
            if (optionDescriptor.containsKey("choices")) {
                var choices = (List<Object>) optionDescriptor.get("choices");
                option.withChoices(choices.toArray());
            }
        }
        return spec;
    }

    /**
     * Extra information to be attached to any generated {@link ValidationException}
     */
    public static final class ValidationContext {

        private final String path;

        public ValidationContext(String path) {
            this.path = path;
        }

        public String getPath() {
            return path;
        }
    }
}
