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
import vfuzz.network.strategy.requestmethod.RequestMethod;
import vfuzz.network.strategy.requestmode.RequestMode;

import java.util.concurrent.CompletableFuture;

/**
 * The {@code VFuzz} class serves as the entry point for the VFuzz application.
 *
 * <p>This class is responsible for initializing and configuring the application,
 * parsing command-line arguments, and starting the fuzzing process.
 *
 * <p>The application operates as follows:
 * <ul>
 *     <li>Registers command-line arguments and processes them using {@link ConfigManager}.</li>
 *     <li>Verifies required arguments and prints usage help if requested via the "--help" argument.</li>
 *     <li>Initializes the necessary components such as the {@link ThreadOrchestrator} for managing fuzzing threads and {@link WebRequester} for handling network requests.</li>
 *     <li>Starts metrics collection and begins the fuzzing process using the wordlist.</li>
 * </ul>
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

        // Start the fuzzing process
        orchestrator.startFuzzing();
    }
}