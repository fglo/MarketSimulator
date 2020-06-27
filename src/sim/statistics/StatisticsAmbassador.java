package sim.statistics;

import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.NullFederateAmbassador;
import sim.Example13Federate;
import sim.HandlersHelper;
import org.portico.impl.hla13.types.DoubleTime;

import java.util.ArrayList;


public class StatisticsAmbassador extends NullFederateAmbassador {

	protected boolean running = true;

    protected double federateTime        = 0.0;
    protected double federateLookahead   = 1.0;
    protected boolean isRegulating       = false;
    protected boolean isConstrained      = false;
    protected boolean isAdvancing        = false;

    protected boolean isAnnounced        = false;
    protected boolean isReadyToRun       = false;

    protected ArrayList<ExternalEvent> externalEvents = new ArrayList<>();

    protected int shopOpenHandle         = 0;
    protected int shopCloseHandle        = 0;
    protected int checkoutOpenHandle     = 0;
    protected int joinQueueHandle        = 0;
    protected int sendToCheckoutHandle   = 0;
    protected int queueOverloadHandle    = 0;
    protected int startCheckoutServiceHandle = 0;
    protected int endCheckoutServiceHandle = 0;


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


	public void receiveInteraction(int interactionClass,
			ReceivedInteraction theInteraction, byte[] tag) {

		receiveInteraction(interactionClass, theInteraction, tag, null, null);
	}

	public void receiveInteraction(int interactionClass,
			ReceivedInteraction theInteraction, byte[] tag,
			LogicalTime theTime, EventRetractionHandle eventRetractionHandle) {
		StringBuilder builder = new StringBuilder("Interaction Received: ");

        if(interactionClass == queueOverloadHandle) {
            double time = convertTime(theTime);
            ExternalEvent event = new ExternalEvent(EventType.QUEUE_OVERLOAD, time);
            externalEvents.add(event);

            builder.append("Queue overload , time=" + time);
            builder.append("\n");

        } else if (interactionClass == shopOpenHandle) {
            double time = convertTime(theTime);
            ExternalEvent event = new ExternalEvent(EventType.SHOP_OPEN, time);
            externalEvents.add(event);

            builder.append("Shop open , time=" + time);
            builder.append("\n");

        } else if (interactionClass == shopCloseHandle) {
            double time = convertTime(theTime);
            ExternalEvent event = new ExternalEvent(EventType.SHOP_CLOSE, time);
            externalEvents.add(event);

            builder.append("Shop close , time=" + time);
            builder.append("\n");

        } else if (interactionClass == checkoutOpenHandle) {
            double time = convertTime(theTime);
            ExternalEvent event = new ExternalEvent(EventType.CHECKOUT_OPEN, time);
            externalEvents.add(event);

            builder.append("Checkout open , time=" + time);
            builder.append("\n");

        } else if (interactionClass == joinQueueHandle) {
            double time = convertTime(theTime);
            ExternalEvent event = new ExternalEvent(EventType.JOIN_QUEUE, time);
            externalEvents.add(event);

            builder.append("JoinQueue , time=" + time);
            builder.append("\n");

        } else if (interactionClass == sendToCheckoutHandle) {
            double time = convertTime(theTime);
            ExternalEvent event = new ExternalEvent(EventType.SEND_TO_CHECKOUT, time);
            externalEvents.add(event);

            builder.append("Send to checkout , time=" + time);
            builder.append("\n");

        } else if (interactionClass == startCheckoutServiceHandle) {
            double time = convertTime(theTime);
            ExternalEvent event = new ExternalEvent(EventType.START_CHECKOUT_SERVICE, time);
            externalEvents.add(event);

            builder.append("Start checkout service , time=" + time);
            builder.append("\n");

        } else if (interactionClass == endCheckoutServiceHandle) {
            double time = convertTime(theTime);
            ExternalEvent event = new ExternalEvent(EventType.END_CHECKOUT_SERVICE, time);
            externalEvents.add(event);

            builder.append("End checkout service , time=" + time);
            builder.append("\n");

        } else if (interactionClass == HandlersHelper
				.getInteractionHandleByName("InteractionRoot.Finish")) {
			builder.append("End of interaction has been recived.");
			running = false;
		}

		log(builder.toString());
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

	public void reflectAttributeValues(int theObject,
			ReflectedAttributes theAttributes, byte[] tag) {
		reflectAttributeValues(theObject, theAttributes, tag, null, null);
	}

	public void reflectAttributeValues(int theObject,
			ReflectedAttributes theAttributes, byte[] tag, LogicalTime theTime,
			EventRetractionHandle retractionHandle) {
		StringBuilder builder = new StringBuilder("Reflection for object:");

		builder.append(" handle=" + theObject);
//		builder.append(", tag=" + EncodingHelpers.decodeString(tag));

		// print the attribute information
		builder.append(", attributeCount=" + theAttributes.size());
		builder.append("\n");
		for (int i = 0; i < theAttributes.size(); i++) {
			try {
				// print the attibute handle
				builder.append("\tattributeHandle=");
				builder.append(theAttributes.getAttributeHandle(i));
				// print the attribute value
				builder.append(", attributeValue=");
				builder.append(EncodingHelpers.decodeInt(theAttributes
                        .getValue(i)));
                builder.append(", time=");
                builder.append(theTime);
				builder.append("\n");
			} catch (ArrayIndexOutOfBounds aioob) {
				// won't happen
			}
		}

		log(builder.toString());
	}

    @Override
    public void discoverObjectInstance(int theObject, int theObjectClass, String objectName) throws CouldNotDiscover, ObjectClassNotKnown, FederateInternalError {
        System.out.println("Pojawil sie nowy obiekt typu SimObject");
    }
}
