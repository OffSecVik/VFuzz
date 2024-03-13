package vfuzz;

import java.util.Iterator;
import java.util.concurrent.BlockingQueue;

public class WaitingIterator<T> implements Iterator<T> {

	private final BlockingQueue<T> queue;
	
	public WaitingIterator(BlockingQueue<T> queue) {
		this.queue = queue;
	}
	
	@Override
	public boolean hasNext() {
		return true; // always return true to indicate that there may be more elements. the iterator stops with an EOF marker
	}

	@Override
	public T next() {
		while (true) {
			try {
				T element = queue.poll();
				if (element != null) {
					return element;
				}
				Thread.sleep(50);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new RuntimeException("Iterator interrupted", e);
			}
		}
	}

}
