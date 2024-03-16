package vfuzz;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

public class CommandLineArgument {
    private final String name;
    private final String alias;
    private final String configName;
    private final BiConsumer<ConfigManager, String> action;
    private final Predicate<String> validator;
    private final String description;
    private final boolean isOptional;
    private final String defaultValue;
    private final boolean isFlag;

    public CommandLineArgument(String name, String alias, String configName, BiConsumer<ConfigManager, String> action, Predicate<String> validator, String description, boolean isOptional, String defaultValue, boolean isFlag) {
        this.name = name;
        this.alias = alias;
        this.configName = configName;
        this.action = action;
        this.validator = validator;
        this.description = description;
        this.isOptional = isOptional;
        this.defaultValue = defaultValue;
        this.isFlag = isFlag;
    }

    public String getName() {
        return name;
    }

    public String getAlias() {
        return alias;
    }

    public String getConfigName() {
        return configName;
    }

    public String getDescription() { //TODO: --help
        return description;
    }

    public boolean isOptional() {
        return isOptional;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public boolean isFlag() {
        return isFlag;
    }

    public boolean validate(String argValue) {
        return validator.test(argValue);
    }

    public void executeAction(ConfigManager configManager, String argValue) {
        if (validator.test(argValue)) {
            action.accept(configManager, argValue);
        } else {
            System.out.println("Validation failed for argument: " + name + " with value: " + argValue);
            //TODO: What happens after failed Validation?
        }
    }

    public void applyDefaultValue(ConfigManager configManager) {
        if (defaultValue != null) {
            action.accept(configManager, defaultValue);
        }
    }
}