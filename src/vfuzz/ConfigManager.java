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

    public boolean processArgument(String arg, String value) {
        CommandLineArgument cmdArg = arguments.get(arg);
        if (cmdArg != null) {
            if (!cmdArg.validate(value)) {
                System.out.println("Validation failed for argument: " + arg + " with value: " + value);
                return false;
            }
            if (configValues.containsKey(cmdArg.getName())) {
                System.out.println("Warning: Argument '" + arg + "' was specified multiple times.");
                return false;
            }
            cmdArg.executeAction(this, value);
            configValues.put(cmdArg.getName(), value);
            providedArgs.add(arg); // Mark the argument as provided
            return true;
        }
        return false;
    }

    public void applyDefaultValues() {
        arguments.values().stream().distinct().forEach(arg -> {
            if (arg.isOptional() && !providedArgs.contains(arg.getName())) {
                String defaultValue = arg.getDefaultValue();
                if (defaultValue != null) {
                    // Verwende defaultValue als Wert in der Aktion, wenn das Argument nicht angegeben wurde.
                    arg.executeAction(this, defaultValue);
                }
            }
        });

        setConfigValue("rateLimit", "0"); //TODO: Find a better solution, maybe eliminate the enabled Config and just get the Value of rateLimit to check
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