package sim.actors.guard;

import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import sim.objects.Client;
import sim.utils.AAmbassador;

import java.util.ArrayList;
import java.util.HashMap;


public class GuardAmbassador extends AAmbassador {
    protected ArrayList<ExternalEvent> externalEvents = new ArrayList<>();

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
        if (interactionClass == finishHandle) {
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

                    ExternalEvent event = new ExternalEvent(EventType.NEW_CLIENT, time);
                    event.addParameter("id_client", idClient);
                    externalEvents.add(event);
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
            ExternalEvent event = new ExternalEvent(EventType.CLIENT_EXITED, time);
            event.addParameter("id_client", theObject);
            externalEvents.add(event);
            log( "removed client object, handle=" + theObject, time);
        }
    }
}
