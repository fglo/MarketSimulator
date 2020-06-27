package sim.actors.queue;

import com.sun.istack.internal.Nullable;
import sim.utils.AExternalEvent;
import sim.utils.IEventType;

import java.util.Comparator;
import java.util.Hashtable;

class ExternalEvent extends AExternalEvent<EventType> {
    public ExternalEvent(EventType eventType, Double time) {
        super(eventType, time);
    }
}
