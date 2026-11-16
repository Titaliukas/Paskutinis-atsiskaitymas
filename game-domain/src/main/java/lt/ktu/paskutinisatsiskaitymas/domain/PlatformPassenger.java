package lt.ktu.paskutinisatsiskaitymas.domain;

/** Minimal body geometry and displacement needed by a platform, confined to the owner thread. */
interface PlatformPassenger {
    double platformX();
    double platformFeetY();
    double platformWidth();
    boolean canRidePlatform();
    void moveWithPlatform(double dx, double dy);
}
