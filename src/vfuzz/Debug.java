package vfuzz;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Debug {
	
	private static ArrayList<String> debugLog = new ArrayList<>();
	private static Map<String, String> requestLog = new HashMap<>();
	
	public static void log(String message) {
		if (isEnabled()) {
			debugLog.add(message);
		}
	}
	
	public static void logRequest(String requestIdentifier, String logMessage) {
		if (isEnabled()) {
			requestLog.put(requestIdentifier, logMessage);
		}
	}
	
	public static boolean isEnabled( ) {
		return ArgParse.getDebugEnabled();
	}
	
	public static ArrayList<String> getLog() {
		return new ArrayList<>(requestLog.values());
	}
}
