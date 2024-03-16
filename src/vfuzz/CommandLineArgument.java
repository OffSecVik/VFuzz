package vfuzz;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

public class CommandLineArgument {
    private String name;
    private String alias;
    private String configName; // Hinzugefügt, um den Konfigurationsnamen zu speichern
    private BiConsumer<ConfigManager, String> action;
    private Predicate<String> validator;
    private String description;
    private boolean isOptional;
    private String defaultValue;

    public CommandLineArgument(String name, String alias, String configName, BiConsumer<ConfigManager, String> action, Predicate<String> validator, String description, boolean isOptional, String defaultValue) {
        this.name = name;
        this.alias = alias;
        this.configName = configName; // Speichere den Konfigurationsnamen
        this.action = action;
        this.validator = validator;
        this.description = description;
        this.isOptional = isOptional;
        this.defaultValue = defaultValue;
    }

    public String getName() {
        return name;
    }

    public String getAlias() {
        return alias;
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

    public boolean validate(String argValue) {
        return validator.test(argValue);
    }

    public void executeAction(ConfigManager configManager, String argValue) {
        if (validator.test(argValue)) {
            action.accept(configManager, argValue);
        } else {
            System.out.println("Validation failed for argument: " + name + " with value: " + argValue);
            // Optional: Hier könntest du entscheiden, ob bei Fehlschlag der Validierung der Standardwert verwendet werden soll
        }
    }

    public void applyDefaultValue(ConfigManager configManager) {
        if (defaultValue != null) {
            action.accept(configManager, defaultValue);
        }
    }
}