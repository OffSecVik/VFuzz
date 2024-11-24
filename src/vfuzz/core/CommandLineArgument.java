package vfuzz.core;

import vfuzz.config.ConfigManager;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Represents a command-line argument that can be configured for use in the application.
 * It supports validation, default values, optional flags, and execution of associated actions.
 */
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

    /**
     * Constructs a CommandLineArgument with the provided parameters.
     *
     * @param name         The name of the argument, typically used as the primary flag (e.g., --input).
     * @param alias        An alternative name or alias for the argument (e.g., -i).
     * @param configName   The corresponding configuration name in the ConfigManager.
     * @param action       A BiConsumer that defines what action to perform when the argument is provided.
     * @param validator    A Predicate that validates the argument value.
     * @param description  A brief description of the argument's purpose, useful for generating help messages.
     * @param isOptional   Indicates whether the argument is optional.
     * @param defaultValue The default value to use if the argument is not provided.
     * @param isFlag       Indicates whether the argument is a flag (i.e., no value required).
     */
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

    public String getDescription() {
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

    /**
     * Validates the provided argument value using the specified validator.
     *
     * @param argValue The value to validate.
     * @return {@code true} if the value passes validation, {@code false} otherwise.
     */
    public boolean validate(String argValue) {
        return validator.test(argValue);
    }

    /**
     * Executes the action associated with the argument if the value passes validation.
     *
     * @param configManager The ConfigManager to which the action is applied.
     * @param argValue      The argument value to process.
     */
    public void executeAction(ConfigManager configManager, String argValue) {
        if (validator.test(argValue)) {
            action.accept(configManager, argValue);
        } else {
            System.out.println("Validation failed for argument: " + name + " with value: " + argValue);
        }
    }

    /**
     * Applies the default value to the argument if no value is provided.
     *
     * @param configManager The ConfigManager to which the default value is applied.
     */
    public void applyDefaultValue(ConfigManager configManager) {
        if (defaultValue != null) {
            action.accept(configManager, defaultValue);
        }
    }
}