package vfuzz.config;

import vfuzz.operations.Range;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class ConfigAccessor {

    private static final ConfigManager configManager = ConfigManager.getInstance();

    public static <T> T getConfigValue(String key, Class<T> type, AtomicBoolean... isDefault) {
        String value = configManager.getConfigValue(key);
        T result = convertToType(value, type);
        if (isDefault.length > 0 && isDefault[0] != null) {
            isDefault[0].set(configManager.isDefaultValue(key));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static <T> T convertToType(String value, Class<T> type) {
        if (value == null) {
            return null;
        }
        try {
            if (type == Integer.class) {
                return (T) Integer.valueOf(value);
            } else if (type == Boolean.class) {
                return (T) Boolean.valueOf(value);
            } else if (type == Double.class) {
                return (T) Double.valueOf(value);
            } else if (type == Float.class) {
                return (T) Float.valueOf(value);
            } else if (type == Long.class) {
                return (T) Long.valueOf(value);
            } else if (type == Set.class) {
                // Angenommen, es handelt sich um eine Set von Ranges, Parsen der CSV
                return (T) Arrays.stream(value.split(","))
                        .map(String::trim)
                        .map(Range::parseToRange) // Stellt sicher, dass Range eine statische Methode parseToRange hat
                        .collect(Collectors.toSet());
            } else if (type.isEnum()) {
                // Verwendung von Enum.valueOf, um den Enum-Wert zu bekommen
                return (T) Enum.valueOf((Class<Enum>) type, value.toUpperCase());
            } else if (type == String.class) {
                return (T) value;
            }
            throw new IllegalArgumentException("Unsupported type conversion requested.");
        } catch (Exception e) {
            System.err.println("Failed to convert '" + value + "' to " + type.getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }
}
