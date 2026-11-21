package lt.ktu.paskutinisatsiskaitymas.domain;

/** Visitor: viena operacija visiems įvykių tipams. R yra operacijos rezultatas. */
public interface GameEventVisitor<R> {
    R visit(ItemSpawnedEvent event);
    R visit(ItemCollectedEvent event);
    R visit(PlayerJoinedEvent event);
    R visit(PlayerLeftEvent event);
}