package sim.actors.checkout;

import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.NullFederateAmbassador;
import sim.Example13Federate;
import org.portico.impl.hla13.types.DoubleTime;
import sim.HandlersHelper;

import java.util.ArrayList;


public class CheckoutAmbassador extends NullFederateAmbassador {

    //----------------------------------------------------------
    //                    STATIC VARIABLES
    //----------------------------------------------------------

    //----------------------------------------------------------
    //                   INSTANCE VARIABLES
    //----------------------------------------------------------
    // these variables are accessible in the package
    protected double federateTime = 0.0;
    protected double federateLookahead = 1.0;

    protected boolean isRegulating = false;
    protected boolean isConstrained = false;
    protected boolean isAdvancing = false;

    protected boolean isAnnounced = false;
    protected boolean isReadyToRun = false;

    protected boolean running = true;

    protected int checkoutOpenHandle = 0;
    protected int checkoutCloseHandle = 0;
    protected int shopCloseHandle = 0;
    protected int sendToCheckoutHandle = 0;

    protected ArrayList<ExternalEvent> externalEvents = new ArrayList<>();

    public void timeRegulationEnabled( LogicalTime theFederateTime ) {
        this.federateTime = convertTime( theFederateTime );
        this.isRegulating = true;
    }

    public void timeConstrainedEnabled( LogicalTime theFederateTime ) {
        this.federateTime = convertTime( theFederateTime );
        this.isConstrained = true;
    }

    public void timeAdvanceGrant( LogicalTime theTime ) {
        this.federateTime = convertTime( theTime );
        this.isAdvancing = false;
    }

    public void synchronizationPointRegistrationFailed( String label ) {
        log( "Failed to register sync point: " + label );
    }

    public void synchronizationPointRegistrationSucceeded( String label ) {
        log( "Successfully registered sync point: " + label );
    }

    public void announceSynchronizationPoint( String label, byte[] tag ) {
        log( "Synchronization point announced: " + label );
        if( label.equals(Example13Federate.READY_TO_RUN) )
            this.isAnnounced = true;
    }

    public void federationSynchronized( String label ) {
        log( "Federation Synchronized: " + label );
        if( label.equals(Example13Federate.READY_TO_RUN) )
            this.isReadyToRun = true;
    }

    private double convertTime( LogicalTime logicalTime ) {
        // PORTICO SPECIFIC!!
        return ((DoubleTime)logicalTime).getTime();
    }

    private void log(String message) {
        System.out.println("FederateAmbassador: " + message);
    }


    ///LOGIC

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

        if (interactionClass == checkoutOpenHandle) {
            double time = convertTime(theTime);
            ExternalEvent event = new ExternalEvent(EventType.CHECKOUT_OPEN, time);
            externalEvents.add(event);

            builder.append("CheckoutOpen , time=" + time);
            builder.append("\n");
        } else if (interactionClass == checkoutCloseHandle) {
            try {
                int idCheckout = EncodingHelpers.decodeInt(theInteraction.getValue(0));
                double time = convertTime(theTime);
                ExternalEvent event = new ExternalEvent(EventType.CHECKOUT_CLOSE, time);
                event.addParameter("id_checkout", idCheckout);
                externalEvents.add(event);

                builder.append("CheckoutClose , time=" + time);
                builder.append(" id_checkout=").append(idCheckout);
                builder.append("\n");

            } catch (ArrayIndexOutOfBounds ignored) {

            }
        } else if (interactionClass == shopCloseHandle) {
            double time = convertTime(theTime);
            ExternalEvent event = new ExternalEvent(EventType.SHOP_CLOSE, time);
            externalEvents.add(event);

            builder.append("ShopClose , time=" + time);
            builder.append("\n");
        } else if (interactionClass == sendToCheckoutHandle) {
            try {
                int idClient = EncodingHelpers.decodeInt(theInteraction.getValue(0));
                int idCheckout = EncodingHelpers.decodeInt(theInteraction.getValue(1));
                double time = convertTime(theTime);
                ExternalEvent event = new ExternalEvent(EventType.SEND_TO_CHECKOUT, time);
                event.addParameter("id_client", idClient);
                event.addParameter("id_checkout", idCheckout);
                externalEvents.add(event);

                builder.append("SendToCheckout , time=" + time);
                builder.append(" id_client=").append(idClient);
                builder.append(" id_checkout=").append(idCheckout);
                builder.append("\n");

            } catch (ArrayIndexOutOfBounds ignored) {

            }
        }

        log(builder.toString());
    }

}
