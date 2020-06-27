package sim.utils;

import java.util.Comparator;
import java.util.Hashtable;

public abstract class AExternalEvent<TEventType extends IEventType> {

    private Hashtable<String, Integer> parameters;
    private TEventType eventType;
    private Double time;

    public AExternalEvent(TEventType eventType, Double time) {
        this.eventType = eventType;
        this.time = time;
        this.parameters = new Hashtable<>();
    }

    public void addParameter(String key, int value) {
        parameters.put(key, value);
    }

    public TEventType getEventType() {
        return eventType;
    }

    public Integer getParameter(String key) {
        return parameters.get(key);
    }

    public double getTime() {
        return time;
    }

    public static class ExternalEventComparator implements Comparator<AExternalEvent> {

        @Override
        public int compare(AExternalEvent o1, AExternalEvent o2) {
            return o1.time.compareTo(o2.time);
        }
    }

}
