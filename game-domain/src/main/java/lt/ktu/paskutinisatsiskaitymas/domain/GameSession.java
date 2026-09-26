package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Random;

/** Single authoritative match aggregate. Callers must confine mutation to the game-loop thread. */
public final class GameSession {
    private final UUID id;
    private final Arena arena;
    private final Map<UUID, GameCharacter> characters = new LinkedHashMap<>();
    private final ItemFactory itemFactory = new ItemFactory();
    private final Map<UUID, Item> activeItems = new LinkedHashMap<>();
    private final Random random = new Random();
    private double sinceLastItemSpawn;

    public GameSession(UUID id, Arena arena) {
        this.id = Objects.requireNonNull(id, "id");
        this.arena = Objects.requireNonNull(arena, "arena");
    }

    public static GameSession createDefault() {
        Platform ground = new Platform(UUID.randomUUID(),
                new Position(GameConstants.GROUND_X, GameConstants.GROUND_Y),
                GameConstants.GROUND_WIDTH, GameConstants.GROUND_HEIGHT);
        Arena arena = new Arena("Prototype arena", GameConstants.ARENA_WIDTH,
                GameConstants.ARENA_HEIGHT, List.of(ground));
        return new GameSession(UUID.randomUUID(), arena);
    }

    public void addPlayer(Player player, int slot) {
        Objects.requireNonNull(player, "player");
        if (characters.size() >= GameConstants.MAX_PLAYERS) {
            throw new IllegalStateException("The match already has two players");
        }
        if (characters.containsKey(player.id())
                || characters.values().stream().anyMatch(character -> character.state().slot() == slot)) {
            throw new IllegalArgumentException("Player ID and slot must be unique");
        }
        double spawnY = GameConstants.GROUND_Y - GameConstants.PLAYER_HEIGHT;
        characters.put(player.id(), new GameCharacter(UUID.randomUUID(), player, slot,
                new Position(GameConstants.spawnX(slot), spawnY), true));
    }

    public void removePlayer(UUID playerId) {
        characters.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    public void advance(Map<UUID, MovementInput> inputByPlayer, double seconds) {
        Objects.requireNonNull(inputByPlayer, "inputByPlayer");
        for (Map.Entry<UUID, GameCharacter> entry : characters.entrySet()) {
            entry.getValue().advance(inputByPlayer.getOrDefault(entry.getKey(), MovementInput.NONE), arena, seconds);
        }
        spawnItemIfDue(seconds, random);
        resolveItemCollisions();
    }

    public void spawnItemIfDue(double seconds, Random random) {
        if (!Double.isFinite(seconds) || seconds <= 0) {
            throw new IllegalArgumentException("Tick duration must be finite and positive");
        }
        Objects.requireNonNull(random, "random");

        sinceLastItemSpawn += seconds;
        if (activeItems.size() >= GameConstants.ITEM_MAX_ACTIVE
                || sinceLastItemSpawn < GameConstants.ITEM_SPAWN_INTERVAL_SECONDS) {
            return;
        }
        sinceLastItemSpawn = 0;

        ItemType[] types = ItemType.values();
        ItemType type = types[random.nextInt(types.length)];
        double spawnX = random.nextDouble() * (arena.width() - GameConstants.PLAYER_WIDTH);
        double spawnY = GameConstants.GROUND_Y - GameConstants.PLAYER_HEIGHT;
        Position position = new Position(spawnX, spawnY);

        Item item = itemFactory.create(type, position);
        activeItems.put(item.id(), item);
    }
    
    private void resolveItemCollisions() {
        for (GameCharacter character : characters.values()) {
            activeItems.values().removeIf(item -> {
                if (item.collected()) {
                    return false;
                }
                boolean overlaps = character.overlaps(item.position(), GameConstants.ITEM_RADIUS);
                if (overlaps) {
                    item.apply(character);
                    item.markCollected();
                    return true;
                }
                return false;
            });
        }
    }

    public List<Item> activeItems() {
        return List.copyOf(activeItems.values());
    }

    public UUID id() { return id; }
    public Arena arena() { return arena; }

    public List<Player> players() {
        return characters.values().stream()
                .map(character -> new Player(character.state().playerId(), character.state().nickname()))
                .toList();
    }

    public List<GameCharacterState> characters() {
        return characters.values().stream().map(GameCharacter::state).toList();
    }
}
