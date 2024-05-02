package vfuzz.app;

import vfuzz.config.ConfigAccessor;
import vfuzz.config.ConfigurationPrinter;
import vfuzz.config.ConfigManager;
import vfuzz.core.ArgParse;
import vfuzz.core.ThreadOrchestrator;
import vfuzz.logging.Metrics;
import vfuzz.network.WebRequester;

public class VFuzz {
    public static void main(String[] args) {


        ConfigManager configManager = ConfigManager.getInstance();

        ArgParse.registerArguments();

        configManager.processArguments(args);
        configManager.verifyRequiredArguments(); // TODO: Check if work!

        ConfigurationPrinter.printConfiguration();

        int threadCount = ConfigAccessor.getConfigValue("threadCount", Integer.class);
        String wordlistPath = ConfigAccessor.getConfigValue("wordlistPath", String.class);

        ThreadOrchestrator orchestrator = new ThreadOrchestrator(wordlistPath, threadCount);

        WebRequester.initialize(); // NOP method to activate the static initializer of WebRequester. Done to better read the performance profiler output.

        Metrics.startMetrics();

        orchestrator.startFuzzing();
    }
}