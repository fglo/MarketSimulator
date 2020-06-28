package sim.actors.client;

import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.NullFederateAmbassador;
import org.portico.impl.hla13.types.DoubleTime;
import sim.Example13Federate;
import sim.HandlersHelper;
import sim.objects.Queue;
import sim.utils.AAmbassador;

import java.util.ArrayList;
import java.util.HashMap;

public class ClientAmbassador extends AAmbassador {
    protected ArrayList<Integer> queueInstancesHandles = new ArrayList();

    protected int shopOpenHandle = 0;
    protected int shopCloseHandle = 0;
    protected int endCheckoutHandle = 0;

    protected int queueHandle = 0;
    protected ArrayList<ExternalEvent> externalEvents = new ArrayList<>();
    protected HashMap<Integer, Queue> queues = new HashMap<>();

    @Override
    public void receiveInteraction(int interactionClass,
                                   ReceivedInteraction theInteraction, byte[] tag,
                                   LogicalTime theTime, EventRetractionHandle eventRetractionHandle) {
        StringBuilder builder = new StringBuilder("interaction Received: ");

        double time = convertTime(theTime);

//        if (interactionClass == HandlersHelper
//                .getInteractionHandleByName("InteractionRoot.Finish")) {
//            builder.append("Odebrano interakcję kończącą.");
//            running = false;
//        } else

        if (interactionClass == shopOpenHandle) {
            ExternalEvent event = new ExternalEvent(EventType.SHOP_OPEN, time);
            externalEvents.add(event);

            builder.append("ShopOpen");
        } else if (interactionClass == shopCloseHandle) {
            ExternalEvent event = new ExternalEvent(EventType.SHOP_CLOSE, time);
            externalEvents.add(event);

            builder.append("ShopClose");
        } else if (interactionClass == endCheckoutHandle) {
            try {
                int idClient = EncodingHelpers.decodeInt(theInteraction.getValue(0));
                ExternalEvent event = new ExternalEvent(EventType.END_CHECKOUT, time);
                event.addParameter("id_client", idClient);
                externalEvents.add(event);

                builder.append("EndCheckoutService");
                builder.append(", id_client=").append(idClient);
            } catch (ArrayIndexOutOfBounds ignored) {

            }
        }

        log(builder.toString(), time);
    }

    @Override
    public void reflectAttributeValues(int theObject,
                                       ReflectedAttributes theAttributes, byte[] tag, LogicalTime theTime,
                                       EventRetractionHandle retractionHandle) {
        StringBuilder builder = new StringBuilder("reflection for object: ");
        double time = convertTime(theTime);

        if (queueInstancesHandles.contains(theObject)) {
            builder.append("handle=" + theObject);
            builder.append(", attributeCount=" + theAttributes.size());

            try {
                int idQueue = EncodingHelpers.decodeInt(theAttributes.getValue(0));
                int idCheckout = EncodingHelpers.decodeInt(theAttributes.getValue(1));
                int length = EncodingHelpers.decodeInt(theAttributes.getValue(2));
                Queue queue;
                if (queues.containsKey(idQueue)) {
                    queue = queues.get(idQueue);
                    queue.idQueue = idQueue;
                    queue.idCheckout = idCheckout;
                    queue.length = length;
                } else {
                    queue = new Queue(idQueue, idCheckout, length);
                }
                queues.put(idQueue, queue);

            } catch (ArrayIndexOutOfBounds arrayIndexOutOfBounds) {
                //arrayIndexOutOfBounds.printStackTrace();
            }
            log(builder.toString(), time);
        }
    }

    @Override
    public void discoverObjectInstance(int theObject, int theObjectClass, String objectName) throws CouldNotDiscover, ObjectClassNotKnown, FederateInternalError {
        if (theObjectClass == queueHandle) {
            log("new queue object");
            queueInstancesHandles.add(theObject);
        }
    }

    @Override
    public void removeObjectInstance(int theObject,
                                     byte[] userSuppliedTag,
                                     LogicalTime theTime,
                                     EventRetractionHandle retractionHandle) {
        double time = convertTime(theTime);
        if (queues.containsKey(theObject)) {
            queues.remove(theObject);
            queueInstancesHandles.remove((Integer) theObject);
            log("removed queue object, handle=" + theObject, time);
        }
    }
}
