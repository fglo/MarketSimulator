package sim.actors.queue;


import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.RtiFactoryFactory;
import org.portico.impl.hla13.types.DoubleTime;
import org.portico.impl.hla13.types.DoubleTimeInterval;
import sim.actors.client.ExternalEvent;
import sim.objects.Queue;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Random;

public class QueueFederate {

    public static final String READY_TO_RUN = "ReadyToRun";

    private RTIambassador rtiamb;
    private QueueAmbassador fedamb;
    private final double timeStep           = 10.0;
    private HashMap<Integer, Integer> checkoutHlaHandles;

    private boolean shopOpen = false;

    public void runFederate() throws RTIexception{
        rtiamb = RtiFactoryFactory.getRtiFactory().createRtiAmbassador();

        try
        {
            File fom = new File( "market.fed" );
            rtiamb.createFederationExecution( "MarketFederation",
                    fom.toURI().toURL() );
            log( "Created Federation" );
        }
        catch( FederationExecutionAlreadyExists exists )
        {
            log( "Didn't create federation, it already existed" );
        }
        catch( MalformedURLException urle )
        {
            log( "Exception processing fom: " + urle.getMessage() );
            urle.printStackTrace();
            return;
        }

        fedamb = new QueueAmbassador();
        rtiamb.joinFederationExecution( "QueueFederate", "MarketFederation", fedamb );
        log( "Joined Federation as QueueFederate");

        rtiamb.registerFederationSynchronizationPoint( READY_TO_RUN, null );

        while( fedamb.isAnnounced == false )
        {
            rtiamb.tick();
        }

        waitForUser();

        rtiamb.synchronizationPointAchieved( READY_TO_RUN );
        log( "Achieved sync point: " +READY_TO_RUN+ ", waiting for federation..." );
        while( fedamb.isReadyToRun == false )
        {
            rtiamb.tick();
        }

        enableTimePolicy();

        publish();

        subscribe();

        while (fedamb.running) {
            advanceTime(randomTime());

            if (fedamb.externalEvents.size() > 0) {
                fedamb.externalEvents.sort(new ExternalEvent.ExternalEventComparator());
                for(ExternalEvent externalEvent : fedamb.externalEvents) {
                    fedamb.federateTime = externalEvent.getTime();
                    switch (externalEvent.getEventType()) {
                        case SHOP_CLOSE:
                            this.shopClose();
                            break;
                        case JOIN_QUEUE:
                            this.joinQueue(); // here change
                            break;
                    }
                }
                fedamb.externalEvents.clear();
            }
            rtiamb.tick();
        }
    }

    private void shopClose() {
        log("shop is closing");
        shopOpen = false;
    }

    private void joinQueue(int idQueue) {
        
    }



    private void waitForUser()
    {
        log( " >>>>>>>>>> Press Enter to Continue <<<<<<<<<<" );
        BufferedReader reader = new BufferedReader( new InputStreamReader(System.in) );
        try
        {
            reader.readLine();
        }
        catch( Exception e )
        {
            log( "Error while waiting for user input: " + e.getMessage() );
            e.printStackTrace();
        }
    }

    private void enableTimePolicy() throws RTIexception
    {
        LogicalTime currentTime = convertTime( fedamb.federateTime );
        LogicalTimeInterval lookahead = convertInterval( fedamb.federateLookahead );

        this.rtiamb.enableTimeRegulation( currentTime, lookahead );

        while( fedamb.isRegulating == false )
        {
            rtiamb.tick();
        }

        this.rtiamb.enableTimeConstrained();

        while( fedamb.isConstrained == false )
        {
            rtiamb.tick();
        }
    }

    private void sendInteraction(double timeStep) throws RTIexception {
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();
        Random random = new Random();
        byte[] quantity = EncodingHelpers.encodeInt(random.nextInt(10) + 1);

        int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.AddProduct");
        int quantityHandle = rtiamb.getParameterHandle( "quantity", interactionHandle );

        parameters.add(quantityHandle, quantity);

        LogicalTime time = convertTime( timeStep );
        rtiamb.sendInteraction( interactionHandle, parameters, "tag".getBytes(), time );
    }



    private void publish() throws RTIexception {

        int queueHandle = rtiamb.getObjectClassHandle("ObjectRoot.Queue");
        int idQueue = rtiamb.getAttributeHandle("idQueue", queueHandle);
        int idCheckout = rtiamb.getAttributeHandle("idCheckout", queueHandle);
        int clientsInQueue = rtiamb.getAttributeHandle("clientsInQueue", queueHandle);
        AttributeHandleSet attributeHandleSet =
                RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
        attributeHandleSet.add(idQueue);
        attributeHandleSet.add(idCheckout);
        attributeHandleSet.add(clientsInQueue);
        rtiamb.publishObjectClass(queueHandle, attributeHandleSet);

        int sendToCheckout = rtiamb.getInteractionClassHandle("InteractionRoot.SendToCheckout");
        rtiamb.subscribeInteractionClass(sendToCheckout);

        int queueOverload = rtiamb.getInteractionClassHandle("InteractionRoot.QueueOverload");
        rtiamb.subscribeInteractionClass(queueOverload);
    }

    private void subscribe() throws RTIexception {

        int clientHandle = rtiamb.getObjectClassHandle("ObjectRoot.Client");
        fedamb.clientHandle = clientHandle;
        int idClientHandle = rtiamb.getAttributeHandle("idClientHandle", clientHandle);
        int priopityHandle = rtiamb.getAttributeHandle("priopityHandle", clientHandle);
        AttributeHandleSet attributes =
                RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
        attributes.add(idClientHandle);
        attributes.add(priopityHandle);
        rtiamb.subscribeObjectClassAttributes(clientHandle, attributes);

        int checkoutHandle = rtiamb.getObjectClassHandle("ObjectRoot.Checkout");
        fedamb.checkoutHandle = checkoutHandle;
        int idCheckoutHandle = rtiamb.getAttributeHandle("idCheckoutHandle", checkoutHandle);
        int idCheckoutClientHandle = rtiamb.getAttributeHandle("idCheckoutClientHandle", checkoutHandle);
        AttributeHandleSet checkoutAttributes =
                RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
        checkoutAttributes.add(idCheckoutHandle);
        checkoutAttributes.add(idCheckoutClientHandle);
        rtiamb.subscribeObjectClassAttributes(checkoutHandle, checkoutAttributes);

        int shopClose = rtiamb.getInteractionClassHandle("InteractionRoot.ShopClose");
        rtiamb.subscribeInteractionClass(shopClose);

        int startCheckoutService = rtiamb.getInteractionClassHandle("InteractionRoot.StartCheckoutService");
        rtiamb.subscribeInteractionClass(startCheckoutService);

        int endCheckoutService = rtiamb.getInteractionClassHandle("InteractionRoot.EndCheckoutService");
        rtiamb.subscribeInteractionClass(endCheckoutService);

        int joinQueue = rtiamb.getInteractionClassHandle("InteractionRoot.JoinQueue");
        rtiamb.subscribeInteractionClass(joinQueue);
    }

    private void advanceTime( double timestep ) throws RTIexception
    {
        log("requesting time advance for: " + timestep);
        // request the advance
        fedamb.isAdvancing = true;
        LogicalTime newTime = convertTime( fedamb.federateTime + timestep );
        rtiamb.timeAdvanceRequest( newTime );
        while( fedamb.isAdvancing )
        {
            rtiamb.tick();
        }
    }

    private double randomTime() {
        Random r = new Random();
        return 1 +(9 * r.nextDouble());
    }

    private LogicalTime convertTime( double time )
    {
        // PORTICO SPECIFIC!!
        return new DoubleTime( time );
    }

    /**
     * Same as for {@link #convertTime(double)}
     */
    private LogicalTimeInterval convertInterval( double time )
    {
        // PORTICO SPECIFIC!!
        return new DoubleTimeInterval( time );
    }

    private void log( String message )
    {
        System.out.println( "QueueFederate   : " + message );
    }

    public static void main(String[] args) {
        try {
            new QueueFederate().runFederate();
        } catch (RTIexception rtIexception) {
            rtIexception.printStackTrace();
        }
    }

}
