package lt.ktu.paskutinisatsiskaitymas.domain;

public sealed interface GameEvent
        permits ItemSpawnedEvent, ItemCollectedEvent, PlayerJoinedEvent, PlayerLeftEvent { }