package lt.ktu.paskutinisatsiskaitymas.client;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Collects performance and memory figures for the particle system over one run (a play session or a stress test).
 * Single-threaded: every call happens on the EDT. Nothing is recorded until {@link #start()} and after {@link #stop()}.
 */
final class ParticleStats {
    /** Which particle implementation produced the numbers; flipped when the Flyweight replaces the naive version. */
    static final String IMPLEMENTATION = "naive";
    static final String SPAWNERS = "spawners";
    static final String RATE_PER_SPAWNER = "rate_per_spawner";
    static final String PARTICLE_CAP = "particle_cap";
    static final String RETAINED_BYTES_PER_PARTICLE = "retained_bytes_per_particle";

    private static final int MAX_SAMPLES = 4_000_000;
    private static final int HEAP_SAMPLE_EVERY_UPDATES = 30;
    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();

    private final String scenario;
    private final Samples updateTimes = new Samples();
    private final Samples drawTimes = new Samples();
    private final Samples frameTimes = new Samples();
    private final EnumMap<ParticleKind, Long> spawnedByKind = new EnumMap<>(ParticleKind.class);
    private boolean running;
    private long startNanos;
    private long endNanos;
    private long startHeap;
    private long peakHeap;
    private long endHeap;
    private long startGcCount;
    private long startGcMillis;
    private long endGcCount;
    private long endGcMillis;
    private long startSprites;
    private long startSpriteBytes;
    private long endSprites;
    private long endSpriteBytes;
    private long spawned;
    private long expired;
    private long evicted;
    private long emitNanos;
    private long emitBytes;
    private long updateBytes;
    private long drawBytes;
    private long liveSum;
    private long updateCalls;
    private int peakLive;

    ParticleStats(String scenario) {
        this.scenario = scenario;
    }

    boolean running() {
        return running;
    }

    /** Thread-allocated bytes of the calling thread, or 0 where the JVM cannot report it. */
    static long allocatedBytes() {
        if (THREADS instanceof com.sun.management.ThreadMXBean sun && sun.isThreadAllocatedMemorySupported()) {
            return sun.getCurrentThreadAllocatedBytes();
        }
        return 0;
    }

    static long usedHeap() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    /** Heap in use after asking the collector to run three times; for retained-memory measurements. */
    static long usedHeapAfterGc() {
        for (int i = 0; i < 3; i++) {
            System.gc();
        }
        return usedHeap();
    }

    void start() {
        updateTimes.clear();
        drawTimes.clear();
        frameTimes.clear();
        spawnedByKind.clear();
        spawned = 0;
        expired = 0;
        evicted = 0;
        emitNanos = 0;
        emitBytes = 0;
        updateBytes = 0;
        drawBytes = 0;
        liveSum = 0;
        updateCalls = 0;
        peakLive = 0;
        startHeap = usedHeap();
        peakHeap = startHeap;
        startGcCount = gcCount();
        startGcMillis = gcMillis();
        startSprites = ParticleAllocations.spritesCreated();
        startSpriteBytes = ParticleAllocations.spriteBytes();
        startNanos = System.nanoTime();
        running = true;
    }

    /** Freezes duration, heap and collector figures; a later {@link #report(Map)} describes this frozen run. */
    void stop() {
        if (!running) {
            return;
        }
        endNanos = System.nanoTime();
        endHeap = usedHeap();
        peakHeap = Math.max(peakHeap, endHeap);
        endGcCount = gcCount();
        endGcMillis = gcMillis();
        endSprites = ParticleAllocations.spritesCreated();
        endSpriteBytes = ParticleAllocations.spriteBytes();
        running = false;
    }

    ParticleStatsReport finish(Map<String, String> extras) {
        stop();
        return report(extras);
    }

    void recordEmit(ParticleKind kind, int created, long nanos, long bytes, int evictedCount, int liveAfter) {
        if (!running) {
            return;
        }
        spawned += created;
        spawnedByKind.merge(kind, (long) created, Long::sum);
        evicted += evictedCount;
        emitNanos += nanos;
        emitBytes += bytes;
        peakLive = Math.max(peakLive, liveAfter);
    }

    void recordUpdate(long nanos, long bytes, int expiredCount, int liveAfter) {
        if (!running) {
            return;
        }
        updateTimes.add(nanos);
        updateBytes += bytes;
        expired += expiredCount;
        liveSum += liveAfter;
        updateCalls++;
        if (updateCalls % HEAP_SAMPLE_EVERY_UPDATES == 0) {
            peakHeap = Math.max(peakHeap, usedHeap());
        }
    }

    void recordDraw(long nanos, long bytes) {
        if (!running) {
            return;
        }
        drawTimes.add(nanos);
        drawBytes += bytes;
    }

    /** One whole rendered frame (and, in the stress test, the simulation step before it). */
    void recordFrame(long nanos) {
        if (running) {
            frameTimes.add(nanos);
        }
    }

    /** Builds the report. {@code extras} may carry the SPAWNERS, RATE_PER_SPAWNER, PARTICLE_CAP and retained keys. */
    ParticleStatsReport report(Map<String, String> extras) {
        double seconds = Math.max(0, endNanos - startNanos) / 1e9;
        long allocated = emitBytes + updateBytes + drawBytes;
        List<Map.Entry<String, String>> columns = new ArrayList<>();
        add(columns, "timestamp", Instant.now().toString());
        add(columns, "implementation", IMPLEMENTATION);
        add(columns, "scenario", scenario);
        add(columns, "duration_s", seconds);
        add(columns, "jvm", System.getProperty("java.vm.name") + " " + System.getProperty("java.version"));
        add(columns, "cpu_cores", Runtime.getRuntime().availableProcessors());
        add(columns, "max_heap_mb", Runtime.getRuntime().maxMemory() / 1_048_576.0);
        for (String key : List.of(SPAWNERS, RATE_PER_SPAWNER, PARTICLE_CAP)) {
            add(columns, key, extras.getOrDefault(key, ""));
        }
        add(columns, "spawned_total", spawned);
        add(columns, "spawned_per_s", seconds == 0 ? 0 : spawned / seconds);
        for (ParticleKind kind : ParticleKind.values()) {
            add(columns, "spawned_" + kind.name(), spawnedByKind.getOrDefault(kind, 0L));
        }
        add(columns, "expired", expired);
        add(columns, "evicted_by_cap", evicted);
        add(columns, "peak_live", peakLive);
        add(columns, "avg_live", updateCalls == 0 ? 0 : (double) liveSum / updateCalls);
        addTimings(columns, "update", updateTimes);
        addTimings(columns, "draw", drawTimes);
        addTimings(columns, "frame", frameTimes);
        add(columns, "frames", frameTimes.size());
        add(columns, "fps_effective", seconds == 0 ? 0 : frameTimes.size() / seconds);
        add(columns, "emit_ns_per_particle", spawned == 0 ? 0 : (double) emitNanos / spawned);
        add(columns, "alloc_bytes_total", allocated);
        add(columns, "alloc_bytes_per_spawned", spawned == 0 ? 0 : (double) allocated / spawned);
        add(columns, "alloc_emit_bytes", emitBytes);
        add(columns, "alloc_update_bytes", updateBytes);
        add(columns, "alloc_draw_bytes", drawBytes);
        add(columns, "sprites_created", endSprites - startSprites);
        add(columns, "sprite_bytes_total", endSpriteBytes - startSpriteBytes);
        add(columns, "heap_start_mb", startHeap / 1_048_576.0);
        add(columns, "heap_peak_mb", peakHeap / 1_048_576.0);
        add(columns, "heap_end_mb", endHeap / 1_048_576.0);
        add(columns, "gc_count", endGcCount - startGcCount);
        add(columns, "gc_pause_ms", endGcMillis - startGcMillis);
        add(columns, RETAINED_BYTES_PER_PARTICLE, extras.getOrDefault(RETAINED_BYTES_PER_PARTICLE, ""));
        return new ParticleStatsReport(columns);
    }

    private static void addTimings(List<Map.Entry<String, String>> columns, String name, Samples samples) {
        add(columns, name + "_ms_avg", samples.averageMillis());
        add(columns, name + "_ms_p50", samples.percentileMillis(0.50));
        add(columns, name + "_ms_p95", samples.percentileMillis(0.95));
        add(columns, name + "_ms_p99", samples.percentileMillis(0.99));
        add(columns, name + "_ms_max", samples.maxMillis());
    }

    private static void add(List<Map.Entry<String, String>> columns, String name, Object value) {
        String text = value instanceof Double number ? String.format(Locale.ROOT, "%.3f", number) : String.valueOf(value);
        columns.add(Map.entry(name, text));
    }

    private static long gcCount() {
        long total = 0;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            total += Math.max(0, bean.getCollectionCount());
        }
        return total;
    }

    private static long gcMillis() {
        long total = 0;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            total += Math.max(0, bean.getCollectionTime());
        }
        return total;
    }

    /** Growable list of nanosecond durations with nearest-rank percentiles; stops growing at a fixed cap. */
    private static final class Samples {
        private long[] values = new long[1024];
        private int size;

        void add(long nanos) {
            if (size == MAX_SAMPLES) {
                return;
            }
            if (size == values.length) {
                values = Arrays.copyOf(values, Math.min(MAX_SAMPLES, values.length * 2));
            }
            values[size++] = nanos;
        }

        void clear() {
            size = 0;
        }

        int size() {
            return size;
        }

        double averageMillis() {
            if (size == 0) {
                return 0;
            }
            long total = 0;
            for (int i = 0; i < size; i++) {
                total += values[i];
            }
            return total / (double) size / 1e6;
        }

        double percentileMillis(double fraction) {
            if (size == 0) {
                return 0;
            }
            long[] sorted = Arrays.copyOf(values, size);
            Arrays.sort(sorted);
            int index = Math.max(0, (int) Math.ceil(fraction * size) - 1);
            return sorted[index] / 1e6;
        }

        double maxMillis() {
            long max = 0;
            for (int i = 0; i < size; i++) {
                max = Math.max(max, values[i]);
            }
            return max / 1e6;
        }
    }
}
