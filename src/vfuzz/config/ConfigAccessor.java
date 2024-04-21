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
                return (T) parseRangeSet(value);
            } else if (type.isEnum()) {
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

    // A helper method to parse String into a Set of Range objects, properly typed.
    private static Set<Range> parseRangeSet(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .map(Range::parseToRange)
                .collect(Collectors.toSet());
    }
}