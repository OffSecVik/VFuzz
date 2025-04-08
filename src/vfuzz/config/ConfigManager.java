package vfuzz.config;

import vfuzz.core.ArgumentType;
import vfuzz.core.CommandLineArgument;
import vfuzz.logging.Color;
import vfuzz.network.strategy.requestmode.RequestMode;

import java.util.*;

/**
 * The {@code ConfigManager} class is a singleton responsible for managing configuration values
 * and command-line arguments within the application. It facilitates:
 *
 * <ul>
 *     <li>Registration and processing of command-line arguments.</li>
 *     <li>Storage and retrieval of configuration values.</li>
 *     <li>Handling default values for optional arguments.</li>
 *     <li>Validation of required arguments.</li>
 * </ul>
 *
 * <p>The class ensures that all required arguments are provided and provides utility methods
 * for working with command-line arguments and configuration values.
 *
 * <h2>Features:</h2>
 * <ul>
 *     <li>Support for required and optional arguments.</li>
 *     <li>Ability to check if a configuration value is the default.</li>
 *     <li>Dynamic mapping of arguments to configuration values.</li>
 *     <li>Handles aliases for command-line arguments.</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * ConfigManager configManager = ConfigManager.getInstance();
 * configManager.registerArgument(new CommandLineArgument(
 *     "-t", "--threads", "threadCount",
 *     (cm, value) -> cm.setConfigValue("threadCount", value),
 *     Validator::isPositiveInteger,
 *     "Number of threads to use.", true, "1", false
 * ));
 * configManager.processArguments(args);
 * configManager.verifyRequiredArguments();
 * }</pre>
 *
 * @see CommandLineArgument
 */
public class ConfigManager {

    private static ConfigManager instance;

    private final Map<String, CommandLineArgument> arguments = new LinkedHashMap<>();
    private final Map<String, List<String>> configValues = new HashMap<>();
    private final Map<String, String> defaultValues = new HashMap<>();
    private final Set<String> providedArgs = new HashSet<>();

    private ConfigManager() {}

    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    /**
     * Registers a command-line argument to the manager.
     * Adds the argument and its alias (if any) to the internal map.
     * If the argument is optional, applies the default value if not provided.
     *
     * @param arg the CommandLineArgument to register
     */
    public void registerArgument(CommandLineArgument arg) {
        arguments.put(arg.getName(), arg);
        setOptionalDefaultValue(arg);
    }

    /**
     * Sets the default value for an optional argument if it wasn't provided.
     * The default value is applied if the argument or its alias was not used.
     *
     * @param arg the CommandLineArgument whose default value is being set
     */
    private void setOptionalDefaultValue(CommandLineArgument arg) {
        if (arg.isOptional() && !providedArgs.contains(arg.getConfigName())) {
            arg.applyDefaultValue(this);
            defaultValues.put(arg.getConfigName(), getConfigValue(arg.getConfigName()));
        }
    }

    /**
     * Checks whether the value for a given configuration key is the default value.
     *
     * @param key the configuration key to check
     * @return true if the value is the default, false otherwise
     */
    public boolean isDefaultValue(String key) {
        return defaultValues.containsKey(key) && Objects.equals(getConfigValue(key), defaultValues.get(key));
    }

    /**
     * Processes the given command-line arguments.
     * It identifies registered arguments and executes their actions or sets values accordingly.
     *
     * @param passedArguments the command-line arguments to process
     */
    public void processArguments(String[] passedArguments) {

        if (passedArguments.length == 0) {
            CommandLineArgument help = findArgumentByString("--help");
            providedArgs.add(help.getConfigName());
            help.executeAction(this, "true");
            return;
        }

        for (int i = 0; i < passedArguments.length; i++) {
            String argument = passedArguments[i];
            CommandLineArgument cmdArg = findArgumentByString(argument);

            if (cmdArg != null) {
                List<String> values = getValueForArgument(passedArguments, cmdArg, i);
                boolean validationFailed = false;
                if (values != null) {
                    for (String value : values) {
                        if (!cmdArg.validate(value)) {
                            validationFailed = true;
                        }
                    }
                    if (!validationFailed) {
                        cmdArg.executeAction(this, values);
                    }
                } else if (!cmdArg.isFlag()) {
                    System.out.println(Color.RED + "Error:" + Color.RED_BRIGHT +  " Argument '" + argument + "' expects a value." + Color.RESET);
                    System.exit(0);
                }
            }
        }

        // set mode to FUZZ in case there was a FUZZ marker in POST data
        String postRequestData = ConfigAccessor.getConfigValue("postRequestData",String.class);
        String fuzzMarker = ConfigAccessor.getConfigValue("fuzzMarker",String.class);
        if (postRequestData != null && fuzzMarker != null) {
            if ((postRequestData).contains(fuzzMarker)) {
                this.setConfigValue("requestMode", RequestMode.FUZZ.name());
            }
        }

        // Handle availability of recursive mode
        if (ConfigAccessor.getConfigValue("recursionEnabled", Boolean.class)
            && ConfigAccessor.getConfigValue("requestMode", RequestMode.class) == RequestMode.VHOST) {
            System.out.println(Color.YELLOW + "Note: recursive mode is not available for vhost fuzzing." + Color.RESET);
            CommandLineArgument recursion = findArgumentByString("--recursive");
            providedArgs.remove(recursion.getConfigName());
            providedArgs.add(recursion.getConfigName());
            recursion.executeAction(this, "false");
        }

        if (ConfigAccessor.getConfigValue("recursionEnabled", Boolean.class)
                && ConfigAccessor.getConfigValue("requestMode", RequestMode.class) == RequestMode.SUBDOMAIN) {
            System.out.println(Color.YELLOW + "Note: recursive mode is not available for subdomain fuzzing." + Color.RESET);
            CommandLineArgument recursion = findArgumentByString("--recursive");
            providedArgs.remove(recursion.getConfigName());
            providedArgs.add(recursion.getConfigName());
            recursion.executeAction(this, "false");
        }

        if (ConfigAccessor.getConfigValue("recursionEnabled", Boolean.class)
                && ConfigAccessor.getConfigValue("requestFileMode", RequestMode.class) != null) {
            System.out.println(Color.YELLOW + "Note: recursive mode is not available for file fuzzing." + Color.RESET);
            CommandLineArgument recursion = findArgumentByString("--recursive");
            providedArgs.remove(recursion.getConfigName());
            providedArgs.add(recursion.getConfigName());
            recursion.executeAction(this, "false");
        }

        // handle availability of file extension fuzzing
        if ((ConfigAccessor.getConfigValue("fileExtensions", String.class) != null)
                && !((ConfigAccessor.getConfigValue("requestMode", RequestMode.class) == RequestMode.FUZZ
                || ConfigAccessor.getConfigValue("requestMode", RequestMode.class) == RequestMode.STANDARD)))
        {
            System.out.println(Color.YELLOW + "Note: fuzzing for file extensions is only available in standard or FUZZ mode." + Color.RESET);
            CommandLineArgument fileExtensions = findArgumentByString("-x");
            providedArgs.remove(fileExtensions.getConfigName());
        }

        // handle supplying too many wordlists when not in FUZZ mode
        if (ConfigAccessor.getConfigValue("requestMode", RequestMode.class) != RequestMode.FUZZ
                && ConfigAccessor.getConfigValues("wordlistPath", String.class).size() > 1)
        {
            System.out.println(Color.YELLOW + "Note: supplied too many wordlists, using only the first one." + Color.RESET);
            CommandLineArgument wordlists = findArgumentByString("-w");
            List<String> firstWordlistProvided = new ArrayList<>();
            firstWordlistProvided.add(ConfigAccessor.getConfigValues("wordlistPath", String.class).get(0));
            providedArgs.remove(wordlists.getConfigName());
            providedArgs.add(wordlists.getConfigName());
            wordlists.executeAction(this, firstWordlistProvided);
        }
    }

    /**
     * Finds a command-line argument by a string.
     *
     * @param s the search string
     * @return the CommandLineArgument if found, otherwise null
     */
    private CommandLineArgument findArgumentByString(String s) {
        return arguments.values().stream()
                .filter(a -> a.getName().equals(s) || a.getAlias().equals(s))
                .findFirst()
                .orElse(null);
    }

    /**
     * Retrieves the value associated with a command-line argument.
     * If the argument is a flag, it returns "true".
     * If the argument expects a value, it fetches the next command-line value.
     *
     * @param args the array of command-line arguments
     * @param cmdArg the CommandLineArgument being processed
     * @param index the current index in the command-line arguments
     * @return the value for the argument, or null if no valid value is found
     */
    private List<String> getValueForArgument(String[] args, CommandLineArgument cmdArg, int index) {
        List<String> values = new ArrayList<>();
        if (cmdArg.isFlag()) {
            providedArgs.add(cmdArg.getConfigName());
            values.add("true");
            return values;
        }
        if (cmdArg.getArgumentType() == ArgumentType.SINGLE_VALUE) {
            if (index + 1 < args.length && !args[index + 1].startsWith("-")) {
                providedArgs.add(cmdArg.getConfigName());
                values.add(args[++index]);
                return values;
            }
        }
        if (cmdArg.getArgumentType() == ArgumentType.MULTI_VALUE) {
            providedArgs.add(cmdArg.getConfigName());
            while (index + 1  < args.length && !args[index + 1].startsWith("-")) {
                values.add(args[++index]);
            }
            return values;
        }
        return null;
    }

    /**
     * Sets a configuration value for a specific key.
     *
     * @param key the configuration key
     * @param value the value to be set
     */
    public void setConfigValue(String key, String value) {
        if (!configValues.containsKey(key)) {
            configValues.put(key, new ArrayList<>());
        }
        configValues.get(key).clear();
        configValues.get(key).add(value);
    }

    public void setConfigValue(String key, List<String> value) {
        if (!configValues.containsKey(key)) {
            configValues.put(key, value);
        } else {
            configValues.get(key).clear();
            configValues.get(key).addAll(value);
        }
    }

    /**
     * Retrieves the configuration value for a given key.
     *
     * @param key the configuration key
     * @return the configuration value, or null if the key doesn't exist
     */
    public String getConfigValue(String key) {
        if (configValues.containsKey(key)) {
            return configValues.get(key).get(0);
        }
        return null;
    }

    public List<String> getConfigValues(String key) {
        return configValues.get(key);
    }

    /**
     * Returns a list of all registered command-line arguments.
     *
     * @return a List of registered CommandLineArgument objects
     */
    public List<CommandLineArgument> getRegisteredArguments() {
        return new ArrayList<>(arguments.values());
    }

    /**
     * Verifies that all required arguments are provided.
     * If any required argument is missing, the program exits with an error message.
     */
    public void verifyRequiredArguments() {
        if (providedArgs.stream().anyMatch(a -> a.equals("help"))) {
            return;
        }
        if (ConfigAccessor.getConfigValue("requestMode", RequestMode.class) == RequestMode.SUBDOMAIN) {
            if (!providedArgs.contains("domainName")) {
                System.out.println("Please provide a domain with '-D'");
                System.exit(0);
            }
            if (!providedArgs.contains("wordlistPath")) {
                System.out.println("Please provide a wordlist with '-w'");
                System.exit(0);
            }
            return;
        }

        arguments.values().stream()
                .filter(arg -> !arg.isOptional() && !providedArgs.contains(arg.getConfigName()))
                .findFirst()
                .ifPresent(missingArg -> {
                    System.err.println("Missing required argument: " + missingArg.getName());
                    System.exit(0);
                });
    }


    /**
     * Unregisters a command-line argument by its name.
     * If the argument has an alias, it removes that as well.
     *
     * @param argName the name of the argument to unregister
     */
    @SuppressWarnings("unused")
    public void unregisterArgument(String argName) {
        CommandLineArgument arg = arguments.remove(argName);
        if (arg != null && !arg.getAlias().isEmpty()) {
            arguments.remove(arg.getAlias());
        }
    }

    /**
     * Clears all stored configuration values.
     */
    @SuppressWarnings("unused")
    public void clearConfigValues() {
        configValues.clear();
    }

    /**
     * Retrieves the set of all registered argument names.
     *
     * @return a Set of registered argument names
     */
    @SuppressWarnings("unused")
    public Set<String> getRegisteredArgumentNames() {
        return arguments.keySet();
    }
}
