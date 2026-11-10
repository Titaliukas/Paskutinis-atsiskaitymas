package lt.ktu.paskutinisatsiskaitymas.client;

import java.awt.Graphics2D;

interface HudElement {
    void draw(Graphics2D g, HudContext context);
}