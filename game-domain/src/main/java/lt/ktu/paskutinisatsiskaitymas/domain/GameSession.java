package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/** Single authoritative match aggregate. Callers must confine mutation to the game-loop thread. */
public final class GameSession {
    private final UUID id;
    private final Arena arena;
    private final Map<UUID, GameCharacter> characters = new LinkedHashMap<>();
    private final Map<Integer, NPC> NPCs = new LinkedHashMap<>();
    private final Map<Integer, NPCBehaviorController> NPCControllers = new LinkedHashMap<>();
    private final Map<Integer, UUID> targetCharacters = new LinkedHashMap<>();
    private long nextTargetId = 1;
    private final ItemFactory itemFactory = new ItemFactory();
    private final Map<UUID, Item> activeItems = new LinkedHashMap<>();
    private final Random random = new Random();
    private double sinceLastItemSpawn;
    private final List<GameEventListener> listeners = new CopyOnWriteArrayList<>();

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
        GameSession session = new GameSession(UUID.randomUUID(), arena);
        session.spawnNPC(1, new Position(360, GameConstants.GROUND_Y - GameConstants.PLAYER_HEIGHT), 300, 420);
        session.spawnNPC(2, new Position(580, GameConstants.GROUND_Y - GameConstants.PLAYER_HEIGHT), 530, 650);
        return session;
    }

    public void addPlayer(Player player, int slot) {
        Objects.requireNonNull(player, "player");
        if (characters.size() >= GameConstants.MAX_PLAYERS) {
            throw new IllegalStateException("The match already has four players");
        }
        if (characters.containsKey(player.id())
                || characters.values().stream().anyMatch(character -> character.state().slot() == slot)) {
            throw new IllegalArgumentException("Player ID and slot must be unique");
        }
        if (nextTargetId > Integer.MAX_VALUE) {
            throw new IllegalStateException("Session target identities exhausted");
        }
        double spawnY = GameConstants.GROUND_Y - GameConstants.PLAYER_HEIGHT;
        characters.put(player.id(), new GameCharacter(UUID.randomUUID(), player, slot,
                new Position(GameConstants.spawnX(slot), spawnY), true));
        targetCharacters.put((int) nextTargetId++, characters.get(player.id()).id());
        publish(new PlayerJoinedEvent(player.id(), player.nickname()));
    }

    public void removePlayer(UUID playerId) {
        GameCharacter removed = characters.remove(Objects.requireNonNull(playerId, "playerId"));
        if (removed != null) {
            Integer targetId = targetCharacters.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(removed.id())).map(Map.Entry::getKey)
                    .findFirst().orElseThrow();
            targetCharacters.remove(targetId);
            NPCControllers.values().forEach(controller -> controller.clearTarget(targetId));
            publish(new PlayerLeftEvent(playerId, removed.state().nickname()));
        }
    }
    public void subscribe(GameEventListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void unsubscribe(GameEventListener listener) {
        listeners.remove(listener);
    }

    private void publish(GameEvent event) {
        for (GameEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (RuntimeException exception) {
                System.getLogger(GameSession.class.getName())
                        .log(System.Logger.Level.ERROR, "Game event listener failed", exception);
            }
        }
    }

    public void advance(Map<UUID, MovementInput> inputByPlayer, double seconds) {
        Objects.requireNonNull(inputByPlayer, "inputByPlayer");
        NPCGeometry.positive(seconds, "seconds");
        for (Map.Entry<UUID, GameCharacter> entry : characters.entrySet()) {
            entry.getValue().advance(inputByPlayer.getOrDefault(entry.getKey(), MovementInput.NONE), arena, seconds);
        }
        spawnItemIfDue(seconds, random);
        resolveItemCollisions();
        // STRATEGY-REPORT R5 BEGIN: The existing owner tick selects, delegates, integrates, and resolves.
        List<NPCTarget> targets = npcTargets();
        for (Map.Entry<Integer, NPC> entry : NPCs.entrySet()) {
            NPC npc = entry.getValue();
            NPCBehaviorController controller = NPCControllers.get(entry.getKey());
            controller.update(targets, seconds);
            NPCTarget target = targets.stream().filter(value -> value.id().equals(npc.state().targetId()))
                    .findFirst().orElse(null);
            NPCAction action = npc.advance(arena, target, seconds);
            if (action.hasAttackRequest()) {
                controller.onAttackResolved(resolveNPCAction(entry.getKey(), action));
            }
        }
        // STRATEGY-REPORT R5 END
    }

    /** Adds a matched entity/controller pair on the owner thread; boundaries are left-edge coordinates. */
    public void spawnNPC(Integer npcId, Position spawn, double leftBoundary, double rightBoundary) {
        NPCGeometry.id(Objects.requireNonNull(npcId, "npcId"));
        Objects.requireNonNull(spawn, "spawn");
        if (NPCs.containsKey(npcId) || rightBoundary > arena.width() - GameConstants.PLAYER_WIDTH
                || spawn.x() > arena.width() - GameConstants.PLAYER_WIDTH
                || spawn.y() > arena.height() - GameConstants.PLAYER_HEIGHT) {
            throw new IllegalArgumentException("Duplicate ID or geometry outside arena");
        }
        PatrolStrategy patrol = new PatrolStrategy(leftBoundary, rightBoundary);
        NPC npc = new NPC(npcId, spawn, GameConstants.PLAYER_WIDTH, GameConstants.PLAYER_HEIGHT,
                GameConstants.NPC_SPEED, GameConstants.NPC_ATTACK_RANGE, GameConstants.NPC_ATTACK_DAMAGE,
                GameConstants.NPC_ATTACK_COOLDOWN_SECONDS, patrol);
        NPCBehaviorController controller = new NPCBehaviorController(npc, patrol,
                GameConstants.NPC_DETECTION_RADIUS, GameConstants.NPC_DETECTION_SECONDS,
                GameConstants.NPC_SAFE_DISTANCE, GameConstants.NPC_MAX_CHASE_SECONDS,
                GameConstants.NPC_MAX_FLEE_SECONDS);
        NPCs.put(npcId, npc);
        NPCControllers.put(npcId, controller);
    }

    public void removeNPC(Integer npcId) {
        NPC removed = NPCs.remove(Objects.requireNonNull(npcId, "npcId"));
        if (removed != null) {
            removed.retire();
        }
        NPCControllers.remove(npcId);
    }

    public List<NPCView> npcs() {
        return NPCs.entrySet().stream()
                .map(entry -> new NPCView(entry.getValue().state(), NPCControllers.get(entry.getKey()).activity()))
                .toList();
    }

    public List<Integer> npcControllerIds() { return List.copyOf(NPCControllers.keySet()); }

    public List<NPCTarget> npcTargets() {
        return targetCharacters.entrySet().stream().map(entry -> {
            GameCharacterState state = characterForTarget(entry.getKey()).state();
            return new NPCTarget(entry.getKey(), state.x(), state.y(),
                    GameConstants.PLAYER_WIDTH, GameConstants.PLAYER_HEIGHT);
        }).toList();
    }

    private GameCharacter characterForTarget(Integer targetId) {
        UUID characterId = targetCharacters.get(targetId);
        return characters.values().stream().filter(character -> character.id().equals(characterId))
                .findFirst().orElse(null);
    }

    /** Returns true only if damage changed player state. False also covers rejection or shield block. */
    public boolean resolveNPCAction(Integer npcId, NPCAction action) {
        Objects.requireNonNull(action, "action");
        // STRATEGY-REPORT R5 BEGIN: Revalidate current geometry and cooldown before any state mutation.
        NPC npc = NPCs.get(npcId);
        if (npc == null || !action.hasAttackRequest() || !npc.state().alive() || !npc.state().attackReady()
                || NPCControllers.get(npcId).activity() != NPCActivity.ATTACK
                || !action.attackTargetId().equals(npc.state().targetId())) {
            return false;
        }
        GameCharacter target = characterForTarget(action.attackTargetId());
        if (target == null) {
            return false;
        }
        GameCharacterState state = target.state();
        NPCTarget current = new NPCTarget(action.attackTargetId(), state.x(), state.y(),
                GameConstants.PLAYER_WIDTH, GameConstants.PLAYER_HEIGHT);
        if (NPCGeometry.distance(npc.state(), current) > npc.state().attackRange()) {
            return false;
        }
        npc.recordAttackAttempt();
        return target.takeDamage(npc.attackDamage());
        // STRATEGY-REPORT R5 END
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
        publish(new ItemSpawnedEvent(item.id(), item.type()));
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
                    GameCharacterState state = character.state();
                    publish(new ItemCollectedEvent(state.playerId(), state.nickname(), item.type()));
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
