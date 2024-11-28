package vfuzz.app;

import vfuzz.config.ConfigAccessor;
import vfuzz.config.ConfigurationPrinter;
import vfuzz.config.ConfigManager;
import vfuzz.core.ArgParse;
import vfuzz.core.CommandLineArgument;
import vfuzz.core.ThreadOrchestrator;
import vfuzz.logging.Color;
import vfuzz.logging.Metrics;
import vfuzz.network.WebRequester;
import vfuzz.network.strategy.requestmode.RequestMode;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * The {@code VFuzz} class serves as the main entry point for the VFuzz application.
 *
 * <p>This class is responsible for initializing the application, processing
 * command-line arguments, and starting the fuzzing process. It acts as a
 * coordinator for the various components of the VFuzz system.
 *
 * <p>The application operates as follows:
 * <ul>
 *     <li>Registers and processes command-line arguments using {@link ArgParse} and {@link ConfigManager}.</li>
 *     <li>Validates that all required arguments are provided, displaying help if the "--help" flag is passed.</li>
 *     <li>Prints the current configuration to the console for verification using {@link ConfigurationPrinter}.</li>
 *     <li>Initializes essential components such as the {@link ThreadOrchestrator} and {@link WebRequester}.</li>
 *     <li>Starts metrics collection via {@link Metrics} for monitoring performance.</li>
 *     <li>Suppresses unnecessary logging for cleaner output during execution.</li>
 *     <li>Begins the fuzzing process based on the provided wordlist and configuration.</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * java -jar vfuzz.jar --url http://example.com --wordlist /path/to/wordlist --threads 10
 * }</pre>
 *
 * <p>Features:
 * <ul>
 *     <li>Multi-threaded fuzzing with customizable thread counts.</li>
 *     <li>Support for various HTTP request modes and methods.</li>
 *     <li>Built-in metrics collection for analyzing fuzzing performance.</li>
 *     <li>Dynamic configuration management with optional and required arguments.</li>
 * </ul>
 *
 * @see ArgParse
 * @see ConfigManager
 * @see ConfigurationPrinter
 * @see ThreadOrchestrator
 * @see WebRequester
 * @see Metrics
 */
public class VFuzz {
    public static void main(String[] args) {

        // Initialize the ConfigManager singleton to process configuration
        ConfigManager configManager = ConfigManager.getInstance();

        // Register command-line arguments using ArgParse
        ArgParse.registerArguments();

        // Process command-line arguments and map them to the configuration
        configManager.processArguments(args);

        // Display help information if the "--help" argument is passed
        if (configManager.getConfigValue("help").equals("true")) {
            for (CommandLineArgument arg : configManager.getRegisteredArguments()) {
                String argName = "  " + Color.CYAN + arg.getName() + Color.RESET;
                argName = arg.getAlias().isEmpty() ? argName + "\n" : argName + ", " + Color.CYAN_BOLD + arg.getAlias() + Color.RESET + "\n";
                System.out.print(argName);
                System.out.printf("    " + Color.GREEN + "%s" + Color.RESET + "\n", arg.getDescription());
                System.out.printf("    " + Color.PURPLE + "Default: " + Color.RESET + "%s\n\n",
                        arg.getDefaultValue() != null ? arg.getDefaultValue() : "none");
            }
            System.exit(0); // Exit the application after displaying help menu
        }

        // Verify that all required arguments have been provided
        configManager.verifyRequiredArguments(); // TODO: Check if work!

        // Print the current configuration to the console for verification
        ConfigurationPrinter.printConfiguration();

        // Retrieve the thread count and wordlist path from the configuration
        int threadCount = ConfigAccessor.getConfigValue("threadCount", Integer.class);
        String wordlistPath = ConfigAccessor.getConfigValue("wordlistPath", String.class);

        // Initialize the ThreadOrchestrator for managing fuzzing threads
        ThreadOrchestrator orchestrator = new ThreadOrchestrator(wordlistPath, threadCount);

        // Initialize WebRequester (static initializer)
        if (!(ConfigAccessor.getConfigValue("requestMode", RequestMode.class) == RequestMode.SUBDOMAIN)) {
            WebRequester.initialize();
        }

        // Start collecting metrics for performance analysis
        Metrics.startMetrics();

        // suppress logging
        System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
        Logger logger = Logger.getLogger("org.apache.http.client.protocol.ResponseProcessCookies");
        logger.setLevel(Level.OFF);

        // Start the fuzzing process
        orchestrator.startFuzzing();
    }
}