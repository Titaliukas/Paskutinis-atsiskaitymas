package lt.ktu.paskutinisatsiskaitymas.domain;

/** Paired edge openings: the complete body must fit between openingTop and supportY. */
public record SidePassage(double openingTop, double supportY) {
    public SidePassage {
        if (!Double.isFinite(openingTop) || !Double.isFinite(supportY)
                || openingTop < 0 || supportY - openingTop < GameConstants.PLAYER_HEIGHT) {
            throw new IllegalArgumentException("Side passage must fit a whole player body");
        }
    }

    public boolean containsBody(double top, double bottom) {
        return top >= openingTop && bottom <= supportY + 0.000_001;
    }
}
