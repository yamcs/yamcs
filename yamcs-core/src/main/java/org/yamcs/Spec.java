package org.yamcs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Specifies the valid structure of a {@link YConfiguration} instance. While not strictly 'validation', the spec also
 * allows defining additional metadata like the 'default' keyword which is used in the merged result of a validation.
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

    private Map<String, Option> options = new HashMap<>();
    private Map<String, String> aliases = new HashMap<>();

    private boolean allowUnknownKeys = false;
    private List<List<String>> requiredOneOfGroups = new ArrayList<>(0);
    private List<List<String>> requireTogetherGroups = new ArrayList<>(0);
    private List<List<String>> mutuallyExclusiveGroups = new ArrayList<>(0);
    private List<WhenCondition> whenConditions = new ArrayList<>(0);

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
        Option option = new Option(this, name, type);
        options.put(name, option);
        return option;
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
     * Add a condition that is only verified when <tt>key.equals(value)</tt>
     * 
     * @param key
     *            the name of an option
     * @param value
     *            the value that triggers the conditional check
     * @return an instance of {@link WhenCondition} for further configuration options
     */
    public WhenCondition when(String key, Object value) {
        verifyKeys(key);
        WhenCondition whenCondition = new WhenCondition(this, key, value);
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
        ValidationContext ctx = new ValidationContext();
        ctx.path = args.getPath();
        Map<String, Object> result = doValidate(ctx, args.getRoot(), "");
        YConfiguration wrapped = YConfiguration.wrap(result);
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
        return doValidate(new ValidationContext(), args, "");
    }

    private Map<String, Object> doValidate(ValidationContext ctx, Map<String, Object> args, String parent)
            throws ValidationException {

        for (List<String> group : requiredOneOfGroups) {
            if (count(args, group) == 0) {
                String msg = "One of the following is required: " + group;
                if (!"".equals(parent)) {
                    msg += " at " + parent;
                }
                throw new ValidationException(ctx, msg);
            }
        }

        for (List<String> group : mutuallyExclusiveGroups) {
            if (count(args, group) > 1) {
                String msg = "The following arguments are mutually exclusive: " + group;
                if (!"".equals(parent)) {
                    msg += " at " + parent;
                }
                throw new ValidationException(ctx, msg);
            }
        }

        for (List<String> group : requireTogetherGroups) {
            int n = count(args, group);
            if (n > 0 && n != group.size()) {
                String msg = "The following arguments are required together: " + group;
                if (!"".equals(parent)) {
                    msg += " at " + parent;
                }
                throw new ValidationException(ctx, msg);
            }
        }

        for (WhenCondition whenCondition : whenConditions) {
            Object arg = args.get(whenCondition.key);
            if (arg != null && arg.equals(whenCondition.value)) {
                List<String> missing = whenCondition.requiredKeys.stream()
                        .filter(key -> !args.containsKey(key))
                        .collect(Collectors.toList());
                if (!missing.isEmpty()) {
                    String path = "".equals(parent) ? whenCondition.key : (parent + "->" + whenCondition.key);
                    throw new ValidationException(ctx, String.format(
                            "%s is %s but the following arguments are missing: %s",
                            path, whenCondition.value, missing));
                }
            }
        }

        // Build a new set of args where defaults have been entered
        // Makes this a linked hashmap to keep the defined order
        Map<String, Object> result = new LinkedHashMap<>();

        // Check the provided arguments
        for (Entry<String, Object> entry : args.entrySet()) {
            String argName = entry.getKey();
            String path = "".equals(parent) ? argName : (parent + "->" + argName);

            Option option = getOption(argName);
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
                Object arg = entry.getValue();
                Object resultArg = option.validate(ctx, arg, path);
                result.put(option.name, resultArg);
            }
        }

        for (Option option : options.values()) {
            boolean specified = args.containsKey(option.name);
            for (Entry<String, String> alias : aliases.entrySet()) {
                if (alias.getValue().equals(option.name) && args.containsKey(alias.getKey())) {
                    specified = true;
                }
            }

            if (!specified) {
                if (option.required) {
                    String path = "".equals(parent) ? option.name : parent + "->" + option.name;
                    throw new ValidationException(ctx, "Missing required argument " + path);
                }

                Object defaultValue = option.getDefaultValue();
                if (defaultValue != null) {
                    result.put(option.name, defaultValue);
                }
            }
        }

        return result;
    }

    private Option getOption(String key) {
        key = aliases.getOrDefault(key, key);
        return options.get(key);
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
     * Returns a copy of the given arguments but with all secret arguments masked as <tt>*****</tt>.
     * <p>
     * This method does not validate the arguments, however it will throw random exceptions if the input does not match
     * the expected structure. It is therefore best to validate the arguments before passing them.
     */
    public Map<String, Object> maskSecrets(Map<String, Object> unsafeArgs) {
        return makeSafe(unsafeArgs, true);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> makeSafe(Map<String, Object> unsafeArgs, boolean mask) {
        Map<String, Object> safeArgs = new LinkedHashMap<>();
        for (Entry<String, Object> arg : unsafeArgs.entrySet()) {
            Option option = getOption(arg.getKey());
            if (option == null) {
                // No exception. Often this method is called while we are already
                // handling another exception.
                safeArgs.put(arg.getKey(), arg.getValue());
                continue;
            }

            OptionType type = option.type;
            Object argValue = arg.getValue();
            if (type == OptionType.LIST_OR_ELEMENT && !(argValue instanceof List)) {
                type = OptionType.LIST;
                argValue = Arrays.asList(argValue);
            }

            if (option.secret) {
                if (mask) {
                    safeArgs.put(arg.getKey(), "*****");
                }
            } else if (type == OptionType.MAP) {
                Map<String, Object> map = (Map<String, Object>) argValue;
                Map<String, Object> safeMap = option.spec.makeSafe(map, mask);
                safeArgs.put(arg.getKey(), safeMap);
            } else if (type == OptionType.LIST) {
                List<Object> list = (List<Object>) argValue;
                List<Object> safeList = new ArrayList<>();
                for (Object element : list) {
                    if (option.elementType == OptionType.MAP) {
                        Map<String, Object> mapElement = (Map<String, Object>) element;
                        Map<String, Object> safeMapElement = option.spec.makeSafe(mapElement, mask);
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
        for (String key : keys) {
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

            OptionType argType = forArgument(arg);
            if (this == argType) {
                return arg;
            } else if (this == LIST_OR_ELEMENT) {
                if (argType == LIST) {
                    return arg;
                } else {
                    Object elementArg = elementType.convertArgument(ctx, path, arg, null);
                    return Arrays.asList(elementArg);
                }
            } else if (this == FLOAT) {
                if (arg instanceof Integer) {
                    return new Double((Integer) arg);
                } else if (arg instanceof Long) {
                    return new Double((Long) arg);
                }
            }
            throw new ValidationException(ctx, String.format(
                    "%s is of type %s, but should be %s instead",
                    path, argType, this));
        }

        static OptionType forArgument(Object arg) {
            if (arg instanceof String) {
                return STRING;
            } else if (arg instanceof Boolean) {
                return BOOLEAN;
            } else if (arg instanceof Integer) {
                return INTEGER;
            } else if (arg instanceof Float || arg instanceof Double) {
                return FLOAT;
            } else if (arg instanceof List) {
                return LIST;
            } else if (arg instanceof Map) {
                return MAP;
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
            for (String key : keys) {
                requiredKeys.add(key);
            }
            return this;
        }
    }

    public static final class Option {

        private final Spec parentSpec;
        private final String name;
        private final OptionType type;
        private boolean required;
        private boolean secret;
        private Object defaultValue;
        private OptionType elementType;
        private String deprecationMessage;
        private List<Object> choices;
        private Spec spec;
        private boolean applySpecDefaults;

        public Option(Spec parentSpec, String name, OptionType type) {
            this.parentSpec = parentSpec;
            this.name = name;
            this.type = type;
        }

        /**
         * Set whether this option is required.
         */
        public Option withRequired(boolean required) {
            this.required = required;
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
            this.defaultValue = defaultValue;
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
         * Add aliases for this option. During validation the alias will be converted to the real option name.
         */
        public Option withAliases(String... aliases) {
            for (String alias : aliases) {
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
         * In case the {@link #type} is set to {@link OptionType#MAP}, setting this property to <tt>true</tt> will cause
         * defaults within elements of that type to be applied even if the option itself is not defined.
         * <p>
         * Note that this is not a recursive property. You need to specify at every level if so required.
         */
        public Option withApplySpecDefaults(boolean applySpecDefaults) {
            this.applySpecDefaults = applySpecDefaults;
            return this;
        }

        @SuppressWarnings("unchecked")
        private Object validate(ValidationContext ctx, Object arg, String path) throws ValidationException {
            if (deprecationMessage != null) {
                log.warn("Argument {} has been deprecated: {}", path, deprecationMessage);
            }
            arg = type.convertArgument(ctx, path, arg, elementType);

            if (choices != null && !choices.contains(arg)) {
                throw new ValidationException(ctx, String.format(
                        "%s should be one of %s", name, choices));
            }

            if (type == OptionType.LIST || type == OptionType.LIST_OR_ELEMENT) {
                List<Object> resultList = new ArrayList<>();
                ListIterator<Object> it = ((List<Object>) arg).listIterator();
                while (it.hasNext()) {
                    String elPath = path + "[" + it.nextIndex() + "]";
                    Object argElement = it.next();
                    argElement = elementType.convertArgument(ctx, elPath, argElement, null);

                    if (elementType == OptionType.LIST) {
                        throw new UnsupportedOperationException("List of lists cannot be validated");
                    } else if (elementType == OptionType.MAP) {
                        Map<String, Object> m = (Map<String, Object>) argElement;
                        Object resultArg = spec.doValidate(ctx, m, elPath);
                        resultList.add(resultArg);
                    } else {
                        resultList.add(argElement);
                    }
                }
                return resultList;
            } else if (type == OptionType.MAP) {
                return spec.doValidate(ctx, (Map<String, Object>) arg, path);
            } else {
                return arg;
            }
        }

        private Object getDefaultValue() {
            if (defaultValue != null) {
                if (type == OptionType.LIST_OR_ELEMENT && !(defaultValue instanceof List)) {
                    return Arrays.asList(defaultValue);
                }
                return defaultValue;
            }
            if (applySpecDefaults) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (Option option : spec.options.values()) {
                    Object subDefaultValue = option.getDefaultValue();
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
     * Extra information to be attached to any generated {@link ValidationException}
     */
    public static final class ValidationContext {

        private String path;

        public String getPath() {
            return path;
        }
    }
}
