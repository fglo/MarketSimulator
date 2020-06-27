package sim.actors.client;

import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.NullFederateAmbassador;
import org.portico.impl.hla13.types.DoubleTime;
import sim.Example13Federate;
import sim.HandlersHelper;
import sim.objects.Queue;

import java.util.ArrayList;
import java.util.HashMap;

public class ClientAmbassador extends NullFederateAmbassador {

	protected boolean running = true;

    protected double federateTime        = 0.0;
    protected double federateLookahead   = 1.0;
    protected boolean isRegulating       = false;
    protected boolean isConstrained      = false;
    protected boolean isAdvancing        = false;

    protected boolean isAnnounced        = false;
    protected boolean isReadyToRun       = false;

    protected int shopOpenHandle = 0;
    protected int shopCloseHandle = 0;
    protected int endCheckoutHandle = 0;

    protected int queueHandle = 0;

    protected ArrayList<ExternalEvent> externalEvents = new ArrayList<>();
    protected HashMap<Integer, Queue> queues = new HashMap<>();

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

    public void timeAdvanceGrant( LogicalTime theTime )
    {
        this.federateTime = convertTime( theTime );
        this.isAdvancing = false;
    }

    private double convertTime( LogicalTime logicalTime )
    {
        // PORTICO SPECIFIC!!
        return ((DoubleTime)logicalTime).getTime();
    }

	private void log(String message) {
		System.out.println("StatisticsAmbassador: " + message);
	}

    public void receiveInteraction(int interactionClass,
                                   ReceivedInteraction theInteraction, byte[] tag) {

        receiveInteraction(interactionClass, theInteraction, tag, null, null);
    }

    public void receiveInteraction(int interactionClass,
                                   ReceivedInteraction theInteraction, byte[] tag,
                                   LogicalTime theTime, EventRetractionHandle eventRetractionHandle) {
        StringBuilder builder = new StringBuilder("Interaction Received: ");

        if (interactionClass == HandlersHelper
                .getInteractionHandleByName("InteractionRoot.Finish")) {
            builder.append("Odebrano interakcję kończącą.");
            running = false;
        } else if (interactionClass == shopOpenHandle) {
            double time = convertTime(theTime);
            ExternalEvent event = new ExternalEvent(EventType.SHOP_OPEN, time);
            externalEvents.add(event);

            builder.append("MarketOpen , time=" + time);
            builder.append("\n");
        } else if (interactionClass == shopCloseHandle) {
            double time = convertTime(theTime);
            ExternalEvent event = new ExternalEvent(EventType.SHOP_CLOSE, time);
            externalEvents.add(event);

            builder.append("MarketClose , time=" + time);
            builder.append("\n");
        } else if (interactionClass == endCheckoutHandle) {
            try {
                int idClient = EncodingHelpers.decodeInt(theInteraction.getValue(0));
                double time = convertTime(theTime);
                ExternalEvent event = new ExternalEvent(EventType.END_CHECKOUT, time);
                event.addParameter("id_client", idClient);
                externalEvents.add(event);

                builder.append("EndCheckoutService , time=" + time);
                builder.append(" id_client=").append(idClient);
                builder.append("\n");

            } catch (ArrayIndexOutOfBounds ignored) {

            }
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

		if(theObject == queueHandle) {
            builder.append(" handle=" + theObject);
            builder.append(", attributeCount=" + theAttributes.size());
            builder.append("\n");

            try {
                int idQueue = EncodingHelpers.decodeInt(theAttributes.getValue(0));
                Queue queue;
                if(queues.containsKey(idQueue)) {
                    queue = queues.get(idQueue);
                    queue.idQueue = idQueue;
                    queue.idCheckout = EncodingHelpers.decodeInt(theAttributes.getValue(1));
                    queue.clientsInQueue = EncodingHelpers.decodeInt(theAttributes.getValue(2));
                } else {
                    queue = new Queue(idQueue,
                            theAttributes.getAttributeHandle(1),
                            theAttributes.getAttributeHandle(2));
                }
                queues.put(idQueue, queue);

            } catch (ArrayIndexOutOfBounds arrayIndexOutOfBounds) {
                //arrayIndexOutOfBounds.printStackTrace();
            }
            log(builder.toString());
        }
	}

    @Override
    public void discoverObjectInstance(int theObject, int theObjectClass, String objectName) throws CouldNotDiscover, ObjectClassNotKnown, FederateInternalError {
        System.out.println("New object");
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
        if(queues.containsKey(theObject)) {
            queues.remove(theObject);
        }
    }
}
