Considerations
Thread Reallocation Complexity: Dynamically reallocating threads between the main target and recursive targets introduces complexity in managing and tracking the number of threads dedicated to each task. You'd need a robust mechanism to adjust these allocations on-the-fly without causing interruptions or inconsistencies in the fuzzing process.

Thread Synchronization: Ensuring that thread reallocation happens in a thread-safe manner is crucial. You'll need to synchronize access to shared resources, such as the thread count and task assignments, to prevent race conditions.

Recursive Depth Tracking: Implementing a limit on recursion depth as a command-line argument adds another layer of control. This requires each recursive task to be aware of its current depth and to communicate this information back to the orchestrator for decision-making.

Potential Approach
To manage the complexity, consider implementing a "Task Manager" that oversees all fuzzing tasks, both primary and recursive. This manager could:

Hold a reference to the ExecutorService and the total number of available threads.
Maintain a dynamic mapping of active fuzzing tasks to their thread allocations.
Be responsible for initiating new fuzzing tasks upon recursive hits, adjusting thread allocations, and enforcing recursion depth limits.
When a new recursive target is found:

The QueueConsumer informs the Task Manager of the new target and requests a recursion.
The Task Manager calculates new thread allocations for all active targets, taking into account the maximum recursion depth and total available threads.
Threads are then reallocated accordingly. This might involve pausing certain tasks or reducing the number of threads for some targets to free up threads for the new target.