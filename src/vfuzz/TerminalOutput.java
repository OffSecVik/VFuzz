package vfuzz;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

public class TerminalOutput implements Runnable {

	private Screen screen;
	private volatile boolean running = true;
	
	public TerminalOutput() {
		try {
			//Terminal terminal = new DefaultTerminalFactory().createTerminal();
			screen = new DefaultTerminalFactory().createScreen();
			screen.startScreen();
			screen.clear();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void run() {
		while (running) {
			updateMetrics();
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				running = false;
				Thread.currentThread().interrupt();
			}
		}
	}
	
	public synchronized void updateMetrics() {
		try {
			screen.clear();
			TextGraphics textGraphics = screen.newTextGraphics();
			//textGraphics.setBackgroundColor(TextColor.ANSI.WHITE);
			textGraphics.setForegroundColor(TextColor.ANSI.GREEN);
			textGraphics.putString(new TerminalPosition(0, 0), "Words read from wordlist per Second: " + Metrics.getProducedPerSecond());
			textGraphics.putString(new TerminalPosition(0, 1), "Requests per Second: " + Metrics.getRequestsPerSecond());	
			screen.refresh();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void stop() {
		running = false;
		try {
			screen.stopScreen();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
