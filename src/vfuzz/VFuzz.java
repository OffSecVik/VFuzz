package vfuzz;

public class VFuzz {

	public static void main(String[] args) {
		
		/*
		int threadCount = 10; // default value is 10
		String wordlistPath = "C:\\Users\\Vik\\eclipse-workspace\\VFuzz\\src\\vfuzz\\directory-list-2.3-big.txt";
		String targetUrl = "http://127.0.0.1:8000";
		*/
		
		int parseResult = ArgParse.parse(args);
		if (parseResult != 0) {
			System.exit(parseResult);
		}
		if (!ArgParse.checkRequired()) {
			return;
		}
		
		int threadCount = ArgParse.getThreadCount();
		String wordlistPath = ArgParse.getWordlistPath();
		
		ThreadOrchestrator orchestrator = new ThreadOrchestrator(wordlistPath, threadCount);
		orchestrator.startFuzzing();
		
	}
}
