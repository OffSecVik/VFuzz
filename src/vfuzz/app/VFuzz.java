package vfuzz.app;

import vfuzz.config.ConfigAccessor;
import vfuzz.config.ConfigurationPrinter;
import vfuzz.config.ConfigManager;
import vfuzz.core.ArgParse;
import vfuzz.core.ThreadOrchestrator;
import vfuzz.logging.Metrics;

public class VFuzz {
    public static void main(String[] args) {


        ConfigManager configManager = ConfigManager.getInstance();

        ArgParse.registerArguments();

        configManager.processArguments(args);

        // configManager.verifyRequiredArguments(); // TODO: Fix verifyRequiredArguments

        ConfigurationPrinter.printConfiguration();

        int threadCount = ConfigAccessor.getConfigValue("threadCount", Integer.class);
        String wordlistPath = ConfigAccessor.getConfigValue("wordlistPath", String.class);

        ThreadOrchestrator orchestrator = new ThreadOrchestrator(wordlistPath, threadCount + 19);
        Metrics.startMetrics();
        orchestrator.startFuzzing();
        orchestrator.awaitCompletion();
    }
}