package vfuzz.config;

import vfuzz.logging.Color;
import vfuzz.network.strategy.requestmethod.RequestMethod;
import vfuzz.network.strategy.requestmode.RequestMode;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The ConfigurationPrinter class provides functionality to print the current configuration
 * settings to the console. It retrieves configuration values using the {@link ConfigAccessor} class,
 * formats them, and prints them with appropriate colors. Each configuration value is displayed with
 * a label, and if the value is a default, it is displayed in a different color.
 *
 * @see ConfigAccessor
 * @see Color
 */
public class ConfigurationPrinter {

    /**
     * Prints the current configuration settings to the console.
     * For each configuration key, it retrieves the value using {@link ConfigAccessor#getConfigValue},
     * checks whether the value is the default, and formats it with a label and color.
     */
    public static void printConfiguration() {
        AtomicBoolean isDefault = new AtomicBoolean();

        System.out.println("Your current Arguments:");

        System.out.println(getConfigDisplayString("threadCount", Integer.class, isDefault, "Thread Count"));
        System.out.println(getConfigDisplayString("wordlistPath", String.class, isDefault, "Wordlist Path"));
        System.out.println(getConfigDisplayString("url", String.class, isDefault, "URL"));
        System.out.println(getConfigDisplayString("excludedStatusCodes", Set.class, isDefault, "Excluded Status Codes"));
        System.out.println(getConfigDisplayString("excludeLength", Set.class, isDefault, "Excluded Lengths"));
        System.out.println(getConfigDisplayString("requestMode", RequestMode.class, isDefault, "Request Mode"));
        System.out.println(getConfigDisplayString("requestMethod", RequestMethod.class, isDefault, "Request Method"));
        System.out.println(getConfigDisplayString("maxRetries", Integer.class, isDefault, "Max Retries"));
        System.out.println(getConfigDisplayString("rateLimit", Integer.class, isDefault, "Rate Limit"));
        System.out.println(getConfigDisplayString("recursionEnabled", Boolean.class, isDefault, "Recursion Enabled"));
        System.out.println(getConfigDisplayString("userAgent", String.class, isDefault, "User Agent"));
        System.out.println(getConfigDisplayString("requestFileFuzzing", Boolean.class, isDefault, "Request File Fuzzing"));
        String requestFile = ConfigAccessor.getConfigValue("requestFilePath", String.class);
        if (requestFile != null) { // patchwork
            System.out.println(getConfigDisplayString("requestFilePath", String.class, isDefault, "Request File Path"));
        }
        System.out.println(getConfigDisplayString("headers", String.class, isDefault, "Headers"));
        System.out.println(getConfigDisplayString("cookies", String.class, isDefault, "Cookies"));
        System.out.println(getConfigDisplayString("postRequestData", String.class, isDefault, "Post Data"));
        System.out.println(getConfigDisplayString("fuzzMarker", String.class, isDefault, "Fuzz Marker"));
        System.out.println(getConfigDisplayString("followRedirects", Boolean.class, isDefault, "Follow redirects"));
        System.out.println(getConfigDisplayString("randomAgent", Boolean.class, isDefault, "Random Agent"));
        System.out.println(); // need this line for TerminalOutput to override it later...
    }

    /**
     * Retrieves the configuration value for the specified key and converts it to the given type.
     * It then formats the result as a display string with an associated label and color based on
     * whether the value is a default value or not.
     *
     * @param key The configuration key to retrieve the value for.
     * @param type The type of the configuration value.
     * @param isDefault An {@link AtomicBoolean} indicating if the value is a default value.
     * @param label The label to display alongside the configuration value.
     * @param <T> The type of the configuration value.
     * @return A string formatted with the label, configuration value, and color.
     */
    private static <T> String getConfigDisplayString(String key, Class<T> type, AtomicBoolean isDefault, String label) {
        T value = ConfigAccessor.getConfigValue(key, type, isDefault);
        String color = isDefault.get() ? Color.GRAY : Color.BLUE;
        return color + label + ": " + value + Color.RESET;
    }
}
