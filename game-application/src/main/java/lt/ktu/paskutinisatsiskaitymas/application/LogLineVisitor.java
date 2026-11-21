package lt.ktu.paskutinisatsiskaitymas.application;

import lt.ktu.paskutinisatsiskaitymas.domain.GameEventVisitor;
import lt.ktu.paskutinisatsiskaitymas.domain.ItemCollectedEvent;
import lt.ktu.paskutinisatsiskaitymas.domain.ItemSpawnedEvent;
import lt.ktu.paskutinisatsiskaitymas.domain.PlayerJoinedEvent;
import lt.ktu.paskutinisatsiskaitymas.domain.PlayerLeftEvent;

/** ConcreteVisitor: skaitomas žurnalo įrašas kiekvienam įvykiui. */
final class LogLineVisitor implements GameEventVisitor<String> {
    @Override
    public String visit(ItemSpawnedEvent e) {
        return "Item " + e.itemType() + " spawned (" + e.itemId() + ")";
    }

    @Override
    public String visit(ItemCollectedEvent e) {
        return e.nickname() + " collected " + e.itemType();
    }

    @Override
    public String visit(PlayerJoinedEvent e) {
        return e.nickname() + " joined the match";
    }

    @Override
    public String visit(PlayerLeftEvent e) {
        return e.nickname() + " left the match";
    }
}