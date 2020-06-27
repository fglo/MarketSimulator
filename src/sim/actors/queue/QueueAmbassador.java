package sim.actors.queue;

import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.NullFederateAmbassador;
import sim.Example13Federate;
import org.portico.impl.hla13.types.DoubleTime;
import sim.HandlersHelper;
import sim.objects.Checkout;
import sim.objects.Client;
import sim.objects.Queue;

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
    protected int startCheckoutServiceHandle   = 0;
    protected int endCheckoutServiceHandle     = 0;
    protected int joinQueueHandelHandle        = 0;

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
        System.out.println( "FederateAmbassador: " + message );
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
        StringBuilder builder = new StringBuilder("Interaction Received:");
        if (interactionClass == shopCloseHandle) {
            double time = convertTime(theTime);
            ExternalEvent event = new ExternalEvent(EventType.SHOP_CLOSE, time);
            externalEvents.add(event);

            builder.append("Shop close , time=" + time);
            builder.append("\n");

        } else if (interactionClass == startCheckoutServiceHandle) {
            double time = convertTime(theTime);
            ExternalEvent event = new ExternalEvent(EventType.START_CHECKOUT, time);
            externalEvents.add(event);

            builder.append("Start checkout service, time=" + time);
            builder.append("\n");

        } else if (interactionClass == endCheckoutServiceHandle) {
            double time = convertTime(theTime);
            ExternalEvent event = new ExternalEvent(EventType.END_CHECKOUT, time);
            externalEvents.add(event);

            builder.append("End checkout service, time=" + time);
            builder.append("\n");

        } else if (interactionClass == joinQueueHandelHandle) {
            double time = convertTime(theTime);
            ExternalEvent event = new ExternalEvent(EventType.JOIN_QUEUE, time);
            externalEvents.add(event);

            builder.append("Join queue, time=" + time);
            builder.append("\n");

        }else if(interactionClass == HandlersHelper
                .getInteractionHandleByName("InteractionRoot.Finish")) {
            builder.append("End of interaction has been received.");
            running = false;
        }

        log(builder.toString());
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
        StringBuilder builder = new StringBuilder("Reflection for object:");

        if(clientInstancesHandles.contains(theObject)) {
            builder.append(" handle=" + theObject);
            builder.append(", attributeCount=" + theAttributes.size());
            builder.append("\n");

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
            log(builder.toString());
        }

        if(checkoutInstancesHandles.contains(theObject)) {
            builder.append(" handle=" + theObject);
            builder.append(", attributeCount=" + theAttributes.size());
            builder.append("\n");

            try {
                int idCheckout = EncodingHelpers.decodeInt(theAttributes.getValue(0));
                Checkout checkout;
                if(checkouts.containsKey(idCheckout)) {
                    checkout = checkouts.get(idCheckout);
                    checkout.idCheckout = idCheckout;
                    checkout.idClient = EncodingHelpers.decodeInt(theAttributes.getValue(1));
                } else {
                    checkout = new Checkout(idCheckout);
                    checkout.idClient = idCheckout;
                    checkout.idClient = EncodingHelpers.decodeInt(theAttributes.getValue(1));
                }
                checkouts.put(idCheckout, checkout);

            } catch (ArrayIndexOutOfBounds arrayIndexOutOfBounds) {
                //arrayIndexOutOfBounds.printStackTrace();
            }
            log(builder.toString());
        }
    }

    @Override
    public void discoverObjectInstance(int theObject, int theObjectClass, String objectName) throws CouldNotDiscover, ObjectClassNotKnown, FederateInternalError {
        if(theObjectClass == clientHandle) {
            System.out.println("New client object");
            clientInstancesHandles.add(theObject);
        } else if (theObjectClass == checkoutHandle) {
            System.out.println("New checkout object");
            checkoutInstancesHandles.add(theObject);
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
        log( "Object Removed: handle=" + theObject );
        if(clientInstancesHandles.contains(theObject)) {
            clients.remove(theObject);
            clientInstancesHandles.remove(theObject);
        } else if (checkoutInstancesHandles.contains(theObject)) {
            checkouts.remove(theObject);
            checkoutInstancesHandles.remove(theObject);
        }
    }

}
