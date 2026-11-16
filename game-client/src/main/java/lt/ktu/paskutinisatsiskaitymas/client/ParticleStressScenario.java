package lt.ktu.paskutinisatsiskaitymas.client;

import java.util.ArrayList;
import java.util.List;

/** Spawners laid out on a grid, cycling through every particle kind. Pure logic with no Swing, so it is testable. */
final class ParticleStressScenario {
    private final ParticleSystem system;
    private final List<ParticleSpawner> spawners = new ArrayList<>();

    ParticleStressScenario(ParticleSystem system, int spawnerCount, double ratePerSpawner, double width,
            double height) {
        if (spawnerCount < 1) {
            throw new IllegalArgumentException("spawnerCount must be positive");
        }
        this.system = system;
        int columns = (int) Math.ceil(Math.sqrt(spawnerCount * width / height));
        int rows = (int) Math.ceil(spawnerCount / (double) columns);
        ParticleKind[] kinds = ParticleKind.values();
        for (int index = 0; index < spawnerCount; index++) {
            double x = (index % columns + 0.5) * width / columns;
            double y = (index / columns + 0.5) * height / rows;
            spawners.add(new ParticleSpawner(kinds[index % kinds.length], x, y, ratePerSpawner,
                    index % 2 == 0 ? 1 : -1));
        }
    }

    List<ParticleSpawner> spawners() {
        return List.copyOf(spawners);
    }

    /** Emits from every spawner, then advances the particles. */
    void tick(double seconds) {
        for (ParticleSpawner spawner : spawners) {
            spawner.emit(system, seconds);
        }
        system.update(seconds);
    }
}
