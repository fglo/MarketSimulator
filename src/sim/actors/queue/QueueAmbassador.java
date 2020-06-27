package sim.actors.queue;

import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.NullFederateAmbassador;
import sim.Example13Federate;
import org.portico.impl.hla13.types.DoubleTime;
import sim.HandlersHelper;
import sim.objects.Checkout;
import sim.objects.Client;

import java.util.ArrayList;
import java.util.HashMap;


public class QueueAmbassador extends NullFederateAmbassador {

    protected double federateTime        = 0.0;
    protected double federateLookahead   = 1.0;

    protected boolean isRegulating       = false;
    protected boolean isConstrained      = false;
    protected boolean isAdvancing        = false;

    protected boolean isAnnounced        = false;
    protected boolean isReadyToRun       = false;

    protected boolean running 			 = true;

    protected int shopCloseHandle              = 0;
    protected int openQueueHandle = 0;
    protected int joinQueueHandle        = 0;

    protected int clientHandle = 0;
    protected int checkoutHandle = 0;

    protected ArrayList<Integer> clientInstancesHandles = new ArrayList();
    protected ArrayList<Integer> checkoutInstancesHandles = new ArrayList();

    protected ArrayList<ExternalEvent> externalEvents = new ArrayList<>();
    protected HashMap<Integer, Client> clients = new HashMap<>();
    protected HashMap<Integer, Checkout> checkouts = new HashMap<>();

    private double convertTime( LogicalTime logicalTime )
    {
        // PORTICO SPECIFIC!!
        return ((DoubleTime)logicalTime).getTime();
    }

    private void log( String message )
    {
        System.out.println( "QueueAmbassador: " + message );
    }

    private void log(String message, double time) {
        System.out.println("QueueAmbassador: " + message + ", time: " + time);
    }

    public void synchronizationPointRegistrationFailed( String label )
    {
        log( "Failed to register sync point: " + label );
    }

    public void synchronizationPointRegistrationSucceeded( String label )
    {
        log( "Successfully registered sync point: " + label );
    }

    public void announceSynchronizationPoint( String label, byte[] tag )
    {
        log( "Synchronization point announced: " + label );
        if( label.equals(Example13Federate.READY_TO_RUN) )
            this.isAnnounced = true;
    }

    public void federationSynchronized( String label )
    {
        log( "Federation Synchronized: " + label );
        if( label.equals(Example13Federate.READY_TO_RUN) )
            this.isReadyToRun = true;
    }

    /**
     * The RTI has informed us that time regulation is now enabled.
     */
    public void timeRegulationEnabled( LogicalTime theFederateTime )
    {
        this.federateTime = convertTime( theFederateTime );
        this.isRegulating = true;
    }

    public void timeConstrainedEnabled( LogicalTime theFederateTime )
    {
        this.federateTime = convertTime( theFederateTime );
        this.isConstrained = true;
    }

    public void timeAdvanceGrant( LogicalTime theTime )
    {
        this.federateTime = convertTime( theTime );
        this.isAdvancing = false;
    }

    public void receiveInteraction(int interactionClass,
                                   ReceivedInteraction theInteraction,
                                   byte[] tag) {
        // just pass it on to the other method for printing purposes
        // passing null as the time will let the other method know it
        // it from us, not from the RTI
        receiveInteraction(interactionClass, theInteraction, tag, null, null);
    }

    public void receiveInteraction(int interactionClass,
                                   ReceivedInteraction theInteraction,
                                   byte[] tag,
                                   LogicalTime theTime,
                                   EventRetractionHandle eventRetractionHandle) {
        StringBuilder builder = new StringBuilder("interaction Received: ");
        double time = convertTime(theTime);

        if (interactionClass == shopCloseHandle) {
            ExternalEvent event = new ExternalEvent(EventType.SHOP_CLOSE, time);
            externalEvents.add(event);

            builder.append("Close shop");

        } else if (interactionClass == openQueueHandle) {
            try {
                int idCheckout = EncodingHelpers.decodeInt(theInteraction.getValue(0));
                ExternalEvent event = new ExternalEvent(EventType.OPEN_QUEUE, time);
                event.addParameter("id_checkout", idCheckout);
                externalEvents.add(event);

                builder.append("Open queue");
                builder.append(", id_checkout=").append(idCheckout);
            } catch (ArrayIndexOutOfBounds ignored) {

            }
        } else if (interactionClass == joinQueueHandle) {
            try {
                int idClient = EncodingHelpers.decodeInt(theInteraction.getValue(0));
                int idQueue = EncodingHelpers.decodeInt(theInteraction.getValue(1));
                ExternalEvent event = new ExternalEvent(EventType.JOIN_QUEUE, time);
                event.addParameter("id_client", idClient);
                event.addParameter("id_queue", idQueue);
                externalEvents.add(event);

                builder.append("Join queue");
                builder.append(", id_client=").append(idClient);
                builder.append(", id_queue=").append(idQueue);
            } catch (ArrayIndexOutOfBounds ignored) {

            }
        }

        log(builder.toString(), time);
    }

    @Override
    public void reflectAttributeValues(int theObject,
                                       ReflectedAttributes theAttributes, byte[] tag) {
        reflectAttributeValues(theObject, theAttributes, tag, null, null);
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
                int priority = EncodingHelpers.decodeInt(theAttributes.getValue(1));
                Client client;
                if(clients.containsKey(idClient)) {
                    client = clients.get(idClient);
                    client.idClient = idClient;
                    client.priority = priority;
                } else {
                    client = new Client(idClient);
                    client.idClient = idClient;
                    client.priority = priority;
                }
                clients.put(idClient, client);

            } catch (ArrayIndexOutOfBounds arrayIndexOutOfBounds) {
                //arrayIndexOutOfBounds.printStackTrace();
            }
        } else if(checkoutInstancesHandles.contains(theObject)) {
            builder.append("handle=" + theObject);
            builder.append(", attributeCount=" + theAttributes.size());

            try {
                int idCheckout = EncodingHelpers.decodeInt(theAttributes.getValue(0));
                Checkout checkout = checkouts.get(idCheckout);
                if(checkout == null) {
                    checkout = new Checkout(idCheckout);
                }
                checkout.idClient = EncodingHelpers.decodeInt(theAttributes.getValue(1));
                checkouts.put(idCheckout, checkout);
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
        } else if (theObjectClass == checkoutHandle) {
            checkoutInstancesHandles.add(theObject);
            log("new checkout object");
        }
    }

    @Override
    public void removeObjectInstance( int theObject, byte[] userSuppliedTag )
    {
        removeObjectInstance(theObject, userSuppliedTag, null, null);
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
        } else if (checkoutInstancesHandles.contains(theObject)) {
            checkouts.remove(theObject);
            checkoutInstancesHandles.remove((Integer)theObject);
            log( "removed checkout object, handle=" + theObject, time);
        }
    }
}
