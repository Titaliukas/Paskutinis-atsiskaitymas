package lt.ktu.paskutinisatsiskaitymas.server;

import lt.ktu.paskutinisatsiskaitymas.domain.*;
import lt.ktu.paskutinisatsiskaitymas.protocol.GameEventMessage;

/** ConcreteVisitor: įvykį paverčia kliento pranešimu. Tekstai nepakitę, nes klientas parsina " picked up ". */
final class NotificationVisitor implements GameEventVisitor<GameEventMessage> {
    @Override
    public GameEventMessage visit(ItemSpawnedEvent e) {
        return new GameEventMessage("ITEM_SPAWNED", e.itemType() + " appeared");
    }

    @Override
    public GameEventMessage visit(ItemCollectedEvent e) {
        return new GameEventMessage("ITEM_COLLECTED", e.nickname() + " picked up " + e.itemType());
    }

    @Override
    public GameEventMessage visit(PlayerJoinedEvent e) {
        return new GameEventMessage("PLAYER_JOINED", e.nickname() + " joined");
    }

    @Override
    public GameEventMessage visit(PlayerLeftEvent e) {
        return new GameEventMessage("PLAYER_LEFT", e.nickname() + " left");
    }
}