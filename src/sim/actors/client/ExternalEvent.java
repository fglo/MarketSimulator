package sim.actors.client;

import sim.utils.AExternalEvent;

public class ExternalEvent extends AExternalEvent<EventType> {
    public ExternalEvent(EventType eventType, Double time) {
        super(eventType, time);
    }
}
