package lt.ktu.paskutinisatsiskaitymas.client;

import java.util.concurrent.atomic.LongAdder;

/** Process-wide counters of particle sprites created, so memory benchmarks can compare implementations. */
final class ParticleAllocations {
    private static final LongAdder SPRITES = new LongAdder();
    private static final LongAdder SPRITE_BYTES = new LongAdder();

    private ParticleAllocations() { }

    static void recordSprite(long bytes) {
        SPRITES.increment();
        SPRITE_BYTES.add(bytes);
    }

    static long spritesCreated() {
        return SPRITES.sum();
    }

    static long spriteBytes() {
        return SPRITE_BYTES.sum();
    }
}
