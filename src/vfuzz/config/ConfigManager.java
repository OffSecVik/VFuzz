package vfuzz.config;

import vfuzz.core.CommandLineArgument;

import java.util.*;

public class ConfigManager {
    private static ConfigManager instance;
    private final Map<String, CommandLineArgument> arguments = new HashMap<>();
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

    public void registerArgument(CommandLineArgument arg) {
        arguments.put(arg.getName(), arg);
        if (!arg.getAlias().isEmpty()) {
            arguments.put(arg.getAlias(), arg);
        }
        setOptionalDefaultValue(arg);
    }

    private void setOptionalDefaultValue(CommandLineArgument arg) {
        if (arg.isOptional() && !providedArgs.contains(arg.getName()) && !providedArgs.contains(arg.getAlias())) {
            arg.applyDefaultValue(this);
            defaultValues.put(arg.getConfigName(), configValues.get(arg.getConfigName()));
        }
    }

    public boolean isDefaultValue(String key) {
        return defaultValues.containsKey(key) && Objects.equals(configValues.get(key), defaultValues.get(key));
    }

    public void processArguments(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            CommandLineArgument cmdArg = arguments.getOrDefault(arg, findArgumentByAlias(arg));

            if (cmdArg != null) {
                String value = getValueForArgument(args, cmdArg, i);
                if (value != null && cmdArg.validate(value)) {
                    cmdArg.executeAction(this, value);
                } else if (!cmdArg.isFlag()) {
                    System.out.println("Error: Argument '" + arg + "' expects a value.");
                }
            }
        }
    }

    private CommandLineArgument findArgumentByAlias(String arg) {
        return arguments.values().stream()
                .filter(a -> arg.equals(a.getAlias()))
                .findFirst()
                .orElse(null);
    }

    private String getValueForArgument(String[] args, CommandLineArgument cmdArg, int index) {
        if (cmdArg.isFlag()) {
            providedArgs.add(cmdArg.getName());
            return "true";
        }
        if (index + 1 < args.length && !args[index + 1].startsWith("-")) {
            providedArgs.add(cmdArg.getName());
            return args[++index];
        }
        return null;
    }

    public void setConfigValue(String key, String value) {
        configValues.put(key, value);
    }

    public String getConfigValue(String key) {
        return configValues.get(key);
    }

    public List<CommandLineArgument> getRegisteredArguments() {
        return new ArrayList<>(arguments.values());
    }

    @SuppressWarnings("unused")
    public void verifyRequiredArguments() {
        if (arguments.values().stream().anyMatch(arg -> arg.getName().equals("--help"))) {
            return;
        }
        if (arguments.values().stream().anyMatch(arg -> !arg.isOptional() && !providedArgs.contains(arg.getName()) && !providedArgs.contains(arg.getAlias()))) {
            System.err.println("Missing required arguments. Exiting.");
            System.exit(1);
        }
    }

    @SuppressWarnings("unused")
    public boolean isArgumentRegistered(String argName) {
        return arguments.containsKey(argName);
    }

    @SuppressWarnings("unused")
    public void unregisterArgument(String argName) {
        CommandLineArgument arg = arguments.remove(argName);
        if (arg != null && !arg.getAlias().isEmpty()) {
            arguments.remove(arg.getAlias());
        }
    }

    @SuppressWarnings("unused")
    public void clearConfigValues() {
        configValues.clear();
    }

    @SuppressWarnings("unused")
    public Set<String> getRegisteredArgumentNames() {
        return arguments.keySet();
    }
}
