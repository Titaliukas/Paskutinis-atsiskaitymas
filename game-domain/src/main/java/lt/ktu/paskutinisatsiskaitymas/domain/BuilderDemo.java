package lt.ktu.paskutinisatsiskaitymas.domain;

/** Small executable demonstration of the real construction path and reusable builders. */
public final class BuilderDemo {
    private BuilderDemo() { }

    public static void main(String[] args) {
        ArenaDirector director = new ArenaDirector();
        IArenaBuilder combatBuilder = new CombatArenaBuilder();
        Arena combat = director.construct(combatBuilder);
        Arena training = director.construct(new TrainingArenaBuilder());
        Arena rebuilt = director.construct(combatBuilder);
        for (Arena arena : new Arena[]{combat, training, rebuilt}) {
            System.out.printf("%s: %.0fx%.0f, surfaces=%d, players=%d, NPCs=%d, passage=%s%n",
                    arena.type().id(), arena.width(), arena.height(), arena.platforms().size(),
                    arena.playerSpawns().size(), arena.npcSpawns().size(), arena.sidePassage().isPresent());
        }
        System.out.printf("Rebuilt without accumulation; original still has %d surfaces and %d NPC recipes.%n",
                combat.platforms().size(), combat.npcSpawns().size());
    }
}
