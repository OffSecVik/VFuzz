package vfuzz;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ConfigManager {
    private static ConfigManager instance;
    private final Map<String, CommandLineArgument> arguments = new HashMap<>();
    private final Map<String, String> configValues = new HashMap<>();
    private final Set<String> providedArgs = new HashSet<>();

    private ConfigManager() {}

    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    public void registerArgument(CommandLineArgument arg) {
        arguments.put(arg.getName(), arg);
        if (!arg.getAlias().isEmpty()) {
            arguments.put(arg.getAlias(), arg);
        }
        // This makes sure that optional arguments with default values are set immediately,
        // if not overwritten by the command line arguments
        if (arg.isOptional() && !providedArgs.contains(arg.getName()) && !providedArgs.contains(arg.getAlias())) {
            arg.applyDefaultValue(this);
        }
    }

    public void processArguments(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            String value = null;

            // Check whether the next element is a value or represents a new argument
            if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                value = args[++i]; // Take the next value if it exists and is not an argument
            }

            CommandLineArgument cmdArg = arguments.get(arg);
            if (cmdArg != null) {
                if (value == null && !cmdArg.isFlag()) {
                    System.out.println("Error: Argument '" + arg + "' erwartet einen Wert.");
                    continue; // Skip this argument if it expects a value but has none
                }

                value = (value == null) ? "true" : value; // Set "true" for flag arguments without an explicit value
                if (!cmdArg.validate(value)) {
                    System.out.println("Validation failed for argument: " + arg + " with value: " + value);
                    continue;
                }
                cmdArg.executeAction(this, value);
            } else {
                System.out.println("Unbekanntes Argument: " + arg);
            }
        }
    }


    public void applyDefaultValues() {
        arguments.values().stream().distinct().forEach(arg -> {
            // Check whether the argument is optional and has not been provided
            if (arg.isOptional() && !providedArgs.contains(arg.getName())) {
                String defaultValue = arg.getDefaultValue();
                // For flag arguments, check whether a default value exists and only set this if the flag has not already been activated
                if (arg.isFlag()) {
                    if (!"true".equals(configValues.get(arg.getConfigName()))) {
                        // Set the default value for the flag, only if it has not already been set to "true"
                        arg.executeAction(this, defaultValue != null ? defaultValue : "false");
                    }
                } else if (defaultValue != null) {
                    // For non-flag arguments, apply the default value if one is defined
                    arg.executeAction(this, defaultValue);
                }
            }
        });

        // Special treatment for "rateLimit", if required
        // Check whether "rateLimit" has been explicitly set, otherwise set to default value "0"
        if (!providedArgs.contains("--rate-limit")) {
            setConfigValue("rateLimit", "0");
        }
    }


    public void setConfigValue(String key, String value) {
        configValues.put(key, value);
    }

    public String getConfigValue(String key) {
        return configValues.get(key);
    }

    public void verifyRequiredArguments() {
        boolean allRequiredArgsPresent = true;

        for (CommandLineArgument arg : arguments.values()) {
            // Check whether the argument is marked as non-optional and is not contained in the arguments provided.
            if (!arg.isOptional() && !providedArgs.contains(arg.getName()) && !providedArgs.contains(arg.getAlias())) {
                System.err.println("Error: Required argument '" + arg.getName() + "' is missing.");
                allRequiredArgsPresent = false;
            }
        }

        if (!allRequiredArgsPresent) {
            // TODO: Error Handling or should programm always be terminated?
            System.exit(1);
        }
    }


    // Additional (possibly unused) helper methods TODO: Sort out helper methods that will not be used in the future

    public boolean validateConfigValues() {
        // Implementation of the validation logic...
        return true; // placeholder
    }

    public void printAllConfigValues() {
        configValues.forEach((key, value) -> System.out.println(key + ": " + value));
    }

    public boolean isArgumentRegistered(String argName) {
        return arguments.containsKey(argName);
    }

    public void unregisterArgument(String argName) {
        CommandLineArgument arg = arguments.remove(argName);
        if (arg != null && !arg.getAlias().isEmpty()) {
            arguments.remove(arg.getAlias());
        }
    }

    public void clearConfigValues() {
        configValues.clear();
    }

    public Set<String> getRegisteredArgumentNames() {
        return arguments.keySet();
    }
}