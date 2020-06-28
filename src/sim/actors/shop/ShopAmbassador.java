package sim.actors.shop;

import hla.rti.*;
import hla.rti.jlc.NullFederateAmbassador;
import sim.Example13Federate;
import org.portico.impl.hla13.types.DoubleTime;
import sim.HandlersHelper;
import sim.utils.AAmbassador;

import java.util.ArrayList;

public class ShopAmbassador extends AAmbassador {
    protected ArrayList<ExternalEvent> externalEvents = new ArrayList<>();

    protected int queueOverloadHandle = 0;
    protected int checkoutsClosedHandle = 0;
    protected int queuesEmptyHandle = 0;

    @Override
    public void receiveInteraction(int interactionClass,
                                   ReceivedInteraction theInteraction,
                                   byte[] tag,
                                   LogicalTime theTime,
                                   EventRetractionHandle eventRetractionHandle) {

        StringBuilder builder = new StringBuilder("interaction Received: ");
        double time = convertTime(theTime);

        if (interactionClass == queueOverloadHandle) {
            ExternalEvent event = new ExternalEvent(EventType.QUEUE_OVERLOAD, time);
            externalEvents.add(event);

            builder.append("QueueOverload");
        } else if (interactionClass == checkoutsClosedHandle) {
            ExternalEvent event = new ExternalEvent(EventType.CHECKOUTS_CLOSED, time);
            externalEvents.add(event);

            builder.append("CheckoutsClosed");
        } else if (interactionClass == queuesEmptyHandle) {
            ExternalEvent event = new ExternalEvent(EventType.QUEUES_EMPTY, time);
            externalEvents.add(event);

            builder.append("QueuesEmpty");
        }

        log(builder.toString(), time);
    }

    @Override
    public void reflectAttributeValues(int theObject,
                                       ReflectedAttributes theAttributes,
                                       byte[] tag,
                                       LogicalTime theTime,
                                       EventRetractionHandle retractionHandle) {

    }

    @Override
    public void discoverObjectInstance(int theObject,
                                       int theObjectClass,
                                       String objectName) throws CouldNotDiscover, ObjectClassNotKnown, FederateInternalError {

    }

    @Override
    public void removeObjectInstance(int theObject,
                                     byte[] userSuppliedTag,
                                     LogicalTime theTime,
                                     EventRetractionHandle retractionHandle) {

    }
}
