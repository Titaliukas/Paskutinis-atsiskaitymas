package lt.ktu.paskutinisatsiskaitymas.application;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.Predicate;

/** Array-backed, non-blocking handoff that prevents network producers from growing memory without limit. */
public final class BoundedCommandQueue<C> implements CommandQueue<C> {
    private final ArrayBlockingQueue<C> queue;

    public BoundedCommandQueue(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Queue capacity must be positive");
        }
        queue = new ArrayBlockingQueue<>(capacity);
    }

    @Override
    public boolean offer(C command) {
        return queue.offer(Objects.requireNonNull(command, "command"));
    }

    /**
     * Offers a lifecycle command, discarding one replaceable command only when the queue is full.
     * This keeps the queue bounded while allowing joins and leaves to take precedence over stale input.
     */
    public boolean offerAfterEvicting(C command, Predicate<C> replaceable) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(replaceable, "replaceable");
        if (queue.offer(command)) {
            return true;
        }
        for (C candidate : queue) {
            if (replaceable.test(candidate) && queue.remove(candidate)) {
                return queue.offer(command);
            }
        }
        return false;
    }

    @Override
    public Optional<C> poll() {
        return Optional.ofNullable(queue.poll());
    }

    public int size() {
        return queue.size();
    }
}
