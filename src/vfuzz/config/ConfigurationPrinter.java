package vfuzz.config;

import vfuzz.logging.Color;
import vfuzz.network.strategy.requestmethod.RequestMethod;
import vfuzz.network.strategy.requestmode.RequestMode;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConfigurationPrinter {
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
        System.out.println(getConfigDisplayString("metricsEnabled", Boolean.class, isDefault, "Metrics Enabled"));
        System.out.println(getConfigDisplayString("debugEnabled", Boolean.class, isDefault, "Debug Enabled"));
        System.out.println(getConfigDisplayString("recursionEnabled", Boolean.class, isDefault, "Recursion Enabled"));
        System.out.println(getConfigDisplayString("userAgent", String.class, isDefault, "User Agent"));
        System.out.println(getConfigDisplayString("requestFileFuzzing", Boolean.class, isDefault, "Request File Fuzzing"));
        System.out.println(getConfigDisplayString("requestFilePath", String.class, isDefault, "Request File Path"));
        System.out.println(getConfigDisplayString("headers", Set.class, isDefault, "Headers"));
        System.out.println(getConfigDisplayString("cookies", String.class, isDefault, "Cookies"));
        System.out.println(getConfigDisplayString("postRequestData", String.class, isDefault, "Post Data"));
        System.out.println(getConfigDisplayString("fuzzMarker", String.class, isDefault, "Fuzz Marker"));
        System.out.println(getConfigDisplayString("followRedirects", Boolean.class, isDefault, "Follow redirects"));
        System.out.println(getConfigDisplayString("randomAgent", Boolean.class, isDefault, "Random Agent"));
    }

    private static <T> String getConfigDisplayString(String key, Class<T> type, AtomicBoolean isDefault, String label) {
        T value = ConfigAccessor.getConfigValue(key, type, isDefault);
        String color = isDefault.get() ? Color.GRAY : Color.BLUE;
        return color + label + ": " + value + Color.RESET;
    }
}
