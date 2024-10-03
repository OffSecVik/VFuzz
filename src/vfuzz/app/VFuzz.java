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

public class VFuzz {
    public static void main(String[] args) {

        ConfigManager configManager = ConfigManager.getInstance();

        ArgParse.registerArguments();

        configManager.processArguments(args);

        configManager.verifyRequiredArguments(); // TODO: Check if work!

        if (configManager.isArgumentRegistered("--help")) {
            for (CommandLineArgument arg : configManager.getRegisteredArguments()) {
                System.out.printf("  " + Color.CYAN + "%s" + Color.RESET + ", " + Color.CYAN_BOLD + "%s" + Color.RESET + "\n",
                        arg.getName(),
                        arg.getAlias());
                System.out.printf("    " + Color.GREEN + "%s" + Color.RESET + "\n", arg.getDescription());
                System.out.printf("    " + Color.PURPLE + "Default: " + Color.RESET + "%s\n\n",
                        arg.getDefaultValue() != null ? arg.getDefaultValue() : "none");
            }
            System.exit(1);
        }

        ConfigurationPrinter.printConfiguration();

        int threadCount = ConfigAccessor.getConfigValue("threadCount", Integer.class);
        String wordlistPath = ConfigAccessor.getConfigValue("wordlistPath", String.class);

        ThreadOrchestrator orchestrator = new ThreadOrchestrator(wordlistPath, threadCount);

        WebRequester.initialize(); // NOP method to activate the static initializer of WebRequester. Done to better read the performance profiler output.

        Metrics.startMetrics();

        orchestrator.startFuzzing();
    }
}