package lt.ktu.paskutinisatsiskaitymas.application;

import lt.ktu.paskutinisatsiskaitymas.domain.GameEvent;
import lt.ktu.paskutinisatsiskaitymas.domain.GameEventListener;

public final class EventLogObserver implements GameEventListener {
    private static final System.Logger LOG = System.getLogger(EventLogObserver.class.getName());

    @Override
    public void onEvent(GameEvent event) {
        LOG.log(System.Logger.Level.INFO, "Game event: {0}", event);
    }
}