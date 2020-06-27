package sim.actors.queue;

import hla.rti.EventRetractionHandle;
import hla.rti.LogicalTime;
import hla.rti.ReceivedInteraction;
import hla.rti.jlc.NullFederateAmbassador;
import sim.Example13Federate;
import org.portico.impl.hla13.types.DoubleTime;
import sim.HandlersHelper;

import java.util.ArrayList;


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

    protected ArrayList<ExternalEvent> externalEvents = new ArrayList<>();



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

}
