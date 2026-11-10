package lt.ktu.paskutinisatsiskaitymas.client;

/** Assembles the HUD tree once. The panel only knows the root element. */
final class Hud {
    private Hud() { }
    
    static HudGroup create() {
        HudGroup top = new HudGroup("TopCards")
                .add(new PlayerCardElement(0))
                .add(new PlayerCardElement(1));
        HudGroup bottom = new HudGroup("BottomCards")
                .add(new PlayerCardElement(2))
                .add(new PlayerCardElement(3));
        return new HudGroup("Hud")
                .add(top)
                .add(bottom)
                .add(new NotificationElement());
    }
}