package lt.ktu.paskutinisatsiskaitymas.server;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import lt.ktu.paskutinisatsiskaitymas.application.AuthoritativeGameLoop;
import lt.ktu.paskutinisatsiskaitymas.application.BoundedCommandQueue;
import lt.ktu.paskutinisatsiskaitymas.application.GameCommand;
import lt.ktu.paskutinisatsiskaitymas.application.MatchController;
import lt.ktu.paskutinisatsiskaitymas.domain.GameConstants;
import lt.ktu.paskutinisatsiskaitymas.domain.GameSession;
import lt.ktu.paskutinisatsiskaitymas.protocol.WorldSnapshot;

/** Owns the one simulation thread, command queue, match, and snapshot cadence. */
final class GameRuntime implements AutoCloseable {
    static final int COMMAND_QUEUE_CAPACITY = 512;
    static final int SNAPSHOT_RATE = 20;
    private static final System.Logger LOG = System.getLogger(GameRuntime.class.getName());
    private final BoundedCommandQueue<GameCommand> commands =
            new BoundedCommandQueue<>(COMMAND_QUEUE_CAPACITY);
    private final MatchController match = new MatchController(GameSession.createDefault());
    private final AuthoritativeGameLoop loop = new AuthoritativeGameLoop(commands, match);
    private final Consumer<WorldSnapshot> broadcaster;
    private final AtomicBoolean started = new AtomicBoolean();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "authoritative-game-loop");
        thread.setDaemon(false);
        return thread;
    });

    GameRuntime(Consumer<WorldSnapshot> broadcaster) {
        this.broadcaster = Objects.requireNonNull(broadcaster, "broadcaster");
    }

    BoundedCommandQueue<GameCommand> commands() {
        return commands;
    }

    void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        long periodNanos = TimeUnit.SECONDS.toNanos(1) / GameConstants.TICK_RATE;
        scheduler.scheduleAtFixedRate(this::safeTick, 0, periodNanos, TimeUnit.NANOSECONDS);
    }

    private void safeTick() {
        try {
            loop.tick();
            int ticksPerSnapshot = GameConstants.TICK_RATE / SNAPSHOT_RATE;
            if (loop.tickNumber() % ticksPerSnapshot == 0) {
                broadcaster.accept(SnapshotMapper.map(loop.tickNumber(), match.session()));
            }
        } catch (RuntimeException exception) {
            LOG.log(System.Logger.Level.ERROR, "Authoritative game tick failed", exception);
        }
    }

    boolean isTerminated() {
        return scheduler.isTerminated();
    }

    @Override
    public void close() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
                scheduler.awaitTermination(3, TimeUnit.SECONDS);
            }
        } catch (InterruptedException exception) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
