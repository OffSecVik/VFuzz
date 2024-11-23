/**
 * The ConfigManager class is a singleton responsible for managing configuration values
 * and command-line arguments within the application. It allows registering command-line
 * arguments, storing config values, and handling defaults. The class also processes
 * command-line arguments and ensures required arguments are provided.
 */
package vfuzz.config;

import vfuzz.core.CommandLineArgument;
import vfuzz.logging.Color;
import vfuzz.network.strategy.requestmode.RequestMode;

import java.util.*;

public class ConfigManager {

    private static ConfigManager instance;

    // Maps to store command-line arguments and their corresponding values
    private final Map<String, CommandLineArgument> arguments = new LinkedHashMap<>();
    private final Map<String, String> configValues = new HashMap<>();
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
            defaultValues.put(arg.getConfigName(), configValues.get(arg.getConfigName()));
        }
    }

    /**
     * Checks whether the value for a given configuration key is the default value.
     *
     * @param key the configuration key to check
     * @return true if the value is the default, false otherwise
     */
    public boolean isDefaultValue(String key) {
        return defaultValues.containsKey(key) && Objects.equals(configValues.get(key), defaultValues.get(key));
    }

    /**
     * Processes the given command-line arguments.
     * It identifies registered arguments and executes their actions or sets values accordingly.
     *
     * @param passedArguments the command-line arguments to process
     */
    public void processArguments(String[] passedArguments) {

        if (passedArguments.length == 0) {
            CommandLineArgument cmdArg = findArgumentByString("--help");
            providedArgs.add(cmdArg.getConfigName());
            cmdArg.executeAction(this, "true");
        }

        for (int i = 0; i < passedArguments.length; i++) {
            String argument = passedArguments[i];
            CommandLineArgument cmdArg = findArgumentByString(argument);

            if (cmdArg != null) {
                String value = getValueForArgument(passedArguments, cmdArg, i);
                if (value != null && cmdArg.validate(value)) {
                    cmdArg.executeAction(this, value);
                } else if (!cmdArg.isFlag()) {
                    System.out.println(Color.RED + "Error:" + Color.RED_BRIGHT +  " Argument '" + argument + "' expects a value." + Color.RESET);
                }
            }
        }
        String postRequestData = ConfigAccessor.getConfigValue("postRequestData",String.class);
        String fuzzMarker = ConfigAccessor.getConfigValue("fuzzMarker",String.class);
        if (postRequestData != null && fuzzMarker != null) {
            if ((postRequestData).contains(fuzzMarker)) {
                this.setConfigValue("requestMode", RequestMode.FUZZ.name());
            }
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
    private String getValueForArgument(String[] args, CommandLineArgument cmdArg, int index) {
        if (cmdArg.isFlag()) {
            providedArgs.add(cmdArg.getConfigName());
            return "true";
        }
        if (index + 1 < args.length && !args[index + 1].startsWith("-")) {
            providedArgs.add(cmdArg.getConfigName());
            return args[++index];
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
        configValues.put(key, value);
    }

    /**
     * Retrieves the configuration value for a given key.
     *
     * @param key the configuration key
     * @return the configuration value, or null if the key doesn't exist
     */
    public String getConfigValue(String key) {
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
            if (!providedArgs.contains("domain")) {
                System.out.println("Please provide a domain with \'-d\'");
                System.exit(0);
            }
            if (!providedArgs.contains("wordlist")) {
                System.out.println("Please provide a wordlist with \'-w\'");
                System.exit(0);
            }
            return;
        }

        if (arguments.values().stream().anyMatch(arg -> !arg.isOptional() && !providedArgs.contains(arg.getConfigName()))) {
            printMissingAndExit();
        }
    }
    private void printMissingAndExit() {
        System.err.println("Missing required arguments. Exiting.");
        System.exit(1);
    }

    /**
     * Checks if a command-line argument with the given name is registered.
     *
     * @param argName the name of the argument
     * @return true if the argument is registered, false otherwise
     */
    public boolean isArgumentRegistered(String argName) {
        return arguments.containsKey(argName);
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
