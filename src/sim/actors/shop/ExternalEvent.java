package sim.actors.shop;

import sim.utils.AExternalEvent;

class ExternalEvent extends AExternalEvent<EventType> {
    ExternalEvent(EventType eventType, Double time) {
        super(eventType, time);
    }
}
