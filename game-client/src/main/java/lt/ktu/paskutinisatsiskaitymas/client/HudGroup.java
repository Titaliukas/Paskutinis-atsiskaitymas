package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Composite: draws its children in order. A child may itself be a group, so the HUD forms a tree. */
final class HudGroup implements HudElement {
    private final String name;
    private final List<HudElement> children = new ArrayList<>();

    HudGroup(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    HudGroup add(HudElement child) {
        Objects.requireNonNull(child, "child");
        if (child == this) {
            throw new IllegalArgumentException("A group cannot contain itself");
        }
        children.add(child);
        return this;
    }

    void remove(HudElement child) {
        children.remove(child);
    }

    List<HudElement> children() {
        return List.copyOf(children);
    }

    String name() {
        return name;
    }

    @Override
    public void draw(Graphics2D g, HudContext context) {
        for (HudElement child : children) {
            child.draw(g, context);
        }
    }
}