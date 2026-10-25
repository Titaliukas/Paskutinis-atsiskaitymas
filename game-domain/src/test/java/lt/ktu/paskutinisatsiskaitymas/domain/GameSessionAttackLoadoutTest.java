package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameSessionAttackLoadoutTest {
    @Test
    void rangedAttacksUseTheSourceCharactersLoadout() {
        int[] expectedDamage = {12, 30, 16, 20};
        CharacterType[] types = CharacterType.values();

        for (int index = 0; index < types.length; index++) {
            GameSession session = sessionWithTwoPlayers(types[index]);
            UUID sourceId = session.players().getFirst().id();
            UUID targetId = session.players().get(1).id();

            session.startAttack(sourceId, AttackType.RANGED);
            advanceUntilDamage(session, targetId, 60);

            assertEquals(expectedDamage[index], state(session, targetId).stressLevel(), types[index].name());
        }
    }

    @Test
    void meleeAttacksUseTheSourceCharactersLoadout() {
        int[] expectedDamage = {32, 12, 16, 22};
        CharacterType[] types = CharacterType.values();

        for (int index = 0; index < types.length; index++) {
            GameSession session = sessionWithTwoPlayers(types[index]);
            UUID sourceId = session.players().getFirst().id();
            UUID targetId = session.players().get(1).id();
            double attackPosition = state(session, targetId).x()
                    - GameConstants.PLAYER_WIDTH - GameConstants.MELEE_RANGE;

            for (int tick = 0; tick < GameConstants.TICK_RATE * 3
                    && state(session, sourceId).x() < attackPosition; tick++) {
                session.advance(Map.of(sourceId, new MovementInput(false, true, false)),
                        GameConstants.TICK_SECONDS);
            }

            assertTrue(state(session, sourceId).x() >= attackPosition, types[index].name());
            session.startAttack(sourceId, AttackType.MELEE);
            session.advance(Map.of(), GameConstants.TICK_SECONDS);

            assertEquals(expectedDamage[index], state(session, targetId).stressLevel(), types[index].name());
        }
    }

    @Test
    void controlCharacterRangedAttackSlowsItsTarget() {
        GameSession session = sessionWithTwoPlayers(CharacterType.JURGELINIS);
        UUID sourceId = session.players().getFirst().id();
        UUID targetId = session.players().get(1).id();

        session.startAttack(sourceId, AttackType.RANGED);
        advanceUntilDamage(session, targetId, 60);
        double xBeforeMoving = state(session, targetId).x();
        session.advance(Map.of(targetId, new MovementInput(false, true, false)), GameConstants.TICK_SECONDS);

        assertEquals(GameConstants.MOVE_SPEED * 0.3 * GameConstants.TICK_SECONDS,
                state(session, targetId).x() - xBeforeMoving, 0.000_001);
    }

    @Test
    void meleeSpecialistAttackKnocksItsTargetBack() {
        GameSession session = sessionWithTwoPlayers(CharacterType.PATASIJUS);
        UUID sourceId = session.players().getFirst().id();
        UUID targetId = session.players().get(1).id();
        double attackPosition = state(session, targetId).x()
                - GameConstants.PLAYER_WIDTH - GameConstants.MELEE_RANGE;

        for (int tick = 0; tick < GameConstants.TICK_RATE * 3
                && state(session, sourceId).x() < attackPosition; tick++) {
            session.advance(Map.of(sourceId, new MovementInput(false, true, false)), GameConstants.TICK_SECONDS);
        }
        session.startAttack(sourceId, AttackType.MELEE);
        session.advance(Map.of(), GameConstants.TICK_SECONDS);

        assertEquals(GameConstants.MELEE_KNOCKBACK_Y, state(session, targetId).velocityY(), 0.000_001);
    }

    private static GameSession sessionWithTwoPlayers(CharacterType sourceType) {
        Platform ground = new Platform(UUID.randomUUID(), new Position(0, GameConstants.GROUND_Y),
                GameConstants.GROUND_WIDTH, GameConstants.GROUND_HEIGHT);
        GameSession session = new GameSession(UUID.randomUUID(), TestArenas.geometry("Attack test",
                GameConstants.ARENA_WIDTH, GameConstants.ARENA_HEIGHT, List.of(ground)));
        session.addPlayer(new Player(UUID.randomUUID(), "Source"), 0, sourceType);
        session.addPlayer(new Player(UUID.randomUUID(), "Target"), 1, CharacterType.PATASIJUS);
        return session;
    }

    private static void advanceUntilDamage(GameSession session, UUID targetId, int maxTicks) {
        for (int tick = 0; tick < maxTicks && state(session, targetId).stressLevel() == 0; tick++) {
            session.advance(Map.of(), GameConstants.TICK_SECONDS);
        }
    }

    private static GameCharacterState state(GameSession session, UUID playerId) {
        return session.characters().stream()
                .filter(character -> character.playerId().equals(playerId))
                .findFirst().orElseThrow();
    }
}
