package sim.actors.checkout;

import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.NullFederateAmbassador;
import sim.Example13Federate;
import org.portico.impl.hla13.types.DoubleTime;
import sim.HandlersHelper;
import sim.objects.Checkout;
import sim.objects.Client;
import sim.utils.AAmbassador;

import java.util.ArrayList;
import java.util.HashMap;


public class CheckoutAmbassador extends AAmbassador {
    protected ArrayList<ExternalEvent> externalEvents = new ArrayList<>();

    protected int checkoutOpenHandle = 0;
    protected int shopCloseHandle = 0;
    protected int sendToCheckoutHandle = 0;
    protected int noClientsHandle = 0;
    protected int finishHandle = 0;

    protected int clientHandle = 0;
    protected ArrayList<Integer> clientInstancesHandles = new ArrayList();
    protected HashMap<Integer, Client> clients = new HashMap<>();

    @Override
    public void receiveInteraction(int interactionClass,
                                   ReceivedInteraction theInteraction,
                                   byte[] tag,
                                   LogicalTime theTime,
                                   EventRetractionHandle eventRetractionHandle) {
        StringBuilder builder = new StringBuilder("interaction Received: ");

        double time = convertTime(theTime);
        if (interactionClass == checkoutOpenHandle) {
            ExternalEvent event = new ExternalEvent(EventType.CHECKOUT_OPEN, time);
            externalEvents.add(event);
            builder.append("CheckoutOpen");
        } else if (interactionClass == shopCloseHandle) {
            ExternalEvent event = new ExternalEvent(EventType.SHOP_CLOSE, time);
            externalEvents.add(event);

            builder.append("ShopClose");
        } else if (interactionClass == sendToCheckoutHandle) {
            try {
                int idClient = EncodingHelpers.decodeInt(theInteraction.getValue(0));
                int idCheckout = EncodingHelpers.decodeInt(theInteraction.getValue(1));
                ExternalEvent event = new ExternalEvent(EventType.SEND_TO_CHECKOUT, time);
                event.addParameter("id_client", idClient);
                event.addParameter("id_checkout", idCheckout);
                externalEvents.add(event);

                builder.append("SendToCheckout");
                builder.append(", id_client=").append(idClient);
                builder.append(", id_checkout=").append(idCheckout);

            } catch (ArrayIndexOutOfBounds ignored) {

            }
        } else if (interactionClass == noClientsHandle) {
            ExternalEvent event = new ExternalEvent(EventType.NO_CLIENTS, time);
            externalEvents.add(event);

            builder.append("NoClients");
        } else if (interactionClass == finishHandle) {
            ExternalEvent event = new ExternalEvent(EventType.FINISH, time);
            externalEvents.add(event);

            builder.append("Finish");
        }
        log(builder.toString(), time);
    }

    @Override
    public void reflectAttributeValues(int theObject,
                                       ReflectedAttributes theAttributes, byte[] tag, LogicalTime theTime,
                                       EventRetractionHandle retractionHandle) {
        StringBuilder builder = new StringBuilder("reflection for object: ");
        double time = convertTime(theTime);

        if(clientInstancesHandles.contains(theObject)) {
            builder.append("handle=" + theObject);
            builder.append(", attributeCount=" + theAttributes.size());

            try {
                int idClient = EncodingHelpers.decodeInt(theAttributes.getValue(0));
                Client client;
                if(clients.containsKey(idClient)) {
                    client = clients.get(idClient);
                    client.idClient = idClient;
                    client.priority = EncodingHelpers.decodeInt(theAttributes.getValue(1));
                } else {
                    client = new Client(idClient);
                    client.idClient = idClient;
                    client.priority = EncodingHelpers.decodeInt(theAttributes.getValue(1));
                }
                clients.put(idClient, client);

            } catch (ArrayIndexOutOfBounds arrayIndexOutOfBounds) {
                //arrayIndexOutOfBounds.printStackTrace();
            }
        }
        log(builder.toString(), time);
    }

    @Override
    public void discoverObjectInstance(int theObject, int theObjectClass, String objectName) throws CouldNotDiscover, ObjectClassNotKnown, FederateInternalError {
        if(theObjectClass == clientHandle) {
            clientInstancesHandles.add(theObject);
            log("new client object");
        }
    }

    @Override
    public void removeObjectInstance( int theObject,
                                      byte[] userSuppliedTag,
                                      LogicalTime theTime,
                                      EventRetractionHandle retractionHandle )
    {
        double time = convertTime(theTime);

        if(clientInstancesHandles.contains(theObject)) {
            clients.remove(theObject);
            clientInstancesHandles.remove((Integer)theObject);
            log( "removed client object, handle=" + theObject, time);
        }
    }
}
