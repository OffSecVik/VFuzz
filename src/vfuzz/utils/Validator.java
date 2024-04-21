package vfuzz.utils;

import java.io.File;

public class Validator {
// --Commented out by Inspection START (21.04.2024 16:31):
//    /**
//     * Checks if the given string can be parsed as an integer.
//     * This method is useful for validating user input before converting it to an integer.
//     *
//     * @param value The string to validate.
//     * @return {@code true} if the string can be parsed as an integer, {@code false} otherwise.
//     */
//    public static boolean isInteger(String value) {
//        try {
//            Integer.parseInt(value);
//            return true;
//        } catch (NumberFormatException e) {
//            return false;
//        }
//    }
// --Commented out by Inspection STOP (21.04.2024 16:31)

    /**
     * Checks if the given string represents an integer within a specified range.
     * This method is commonly used to ensure that numerical inputs fall within acceptable parameters.
     *
     * @param value The string to validate.
     * @param min The minimum value in the range (inclusive).
     * @param max The maximum value in the range (inclusive).
     * @return {@code true} if the string is an integer within the specified range, {@code false} otherwise.
     */
    public static boolean isIntegerInRange(String value, int min, int max) {
        try {
            int val = Integer.parseInt(value);
            return val >= min && val <= max;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Validates that a given string is not empty after trimming whitespace.
     * This is useful for ensuring that input fields that require non-blank entries are correctly filled.
     *
     * @param value The string to validate.
     * @return {@code true} if the string is not empty, {@code false} otherwise.
     */
    public static boolean isNotEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * Validates a URL to ensure it starts with "http://" or "https://", adjusting the URL if necessary.
     * This method can be used to normalize URLs provided by users to ensure they are in a standard format.
     *
     * @param url The URL to validate and process.
     * @return {@code true} if the URL is valid, {@code false} otherwise after attempting to prepend "http://".
     */
    public static boolean isValidUrlWithProcessing(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        return url.matches("^(http://|https://).*");
    }

    /**
     * Validates that a header string is correctly formatted with a colon separating the name and value.
     * This method ensures that HTTP headers are in the correct "Name: Value" format.
     *
     * @param header The header string to validate.
     * @return {@code true} if the header is correctly formatted, {@code false} otherwise.
     */
    public static boolean isValidHeader(String header) {
        return header.contains(":") && !header.startsWith(":") && !header.endsWith(":");
    }

    /**
     * Checks if a specified file path points to an existing and valid file and not a directory.
     * This is commonly used for file operations to ensure that the file path provided by the user is accessible.
     *
     * @param filePath The file path to validate.
     * @return {@code true} if the file exists and is not a directory, {@code false} otherwise.
     */
    public static boolean isValidFile(String filePath) {
        File file = new File(filePath);
        return file.exists() && !file.isDirectory();
    }

    /**
     * Validates that a string containing status codes or ranges is correctly formatted.
     * This method checks that the string contains valid integers or ranges separated by commas,
     * which is useful for filtering or processing lists of numerical codes.
     *
     * @param csv The CSV string of status codes or ranges to validate.
     * @return {@code true} if the string is valid, {@code false} otherwise.
     */
    public static boolean isValidStatusCodeCsv(String csv) {
        return csv.matches("^\\d+(\\-\\d+)?(,\\d+(\\-\\d+)?)*$");
    }
}