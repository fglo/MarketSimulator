package sim.statistics;

import sim.statistics.EventType;
import sim.utils.AExternalEvent;

class ExternalEvent extends AExternalEvent<EventType> {
    ExternalEvent(EventType eventType, Double time) {
        super(eventType, time);
    }
}
