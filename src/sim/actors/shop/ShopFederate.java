package sim.actors.shop;

import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.RtiFactoryFactory;
import org.portico.impl.hla13.types.DoubleTime;
import org.portico.impl.hla13.types.DoubleTimeInterval;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Random;

/**
 * Created by Michal on 2016-04-27.
 */
public class ShopFederate {

    public static final String READY_TO_RUN = "ReadyToRun";

    private RTIambassador rtiamb;
    private ShopAmbassador fedamb;
    private final double timeStep           = 10.0;
    private HashMap<Integer, Integer> shopHlaHandles;
    private int checkoutCounter = 0;
    private boolean shopStatus = false;
    int timeStepCounter = 0;


    public void runFederate() throws RTIexception {
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

        fedamb = new ShopAmbassador();
        rtiamb.joinFederationExecution( "ShopFederate", "MarketFederation", fedamb );
        log("Joined Federation as ShopFederate");

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

        publishAndSubscribe();

        while (fedamb.running) {
            double timeToAdvance = fedamb.federateTime + timeStep;
            timeStepCounter ++;
            advanceTime(randomTime());

            if(fedamb.externalEvents.size() > 0) {
                fedamb.externalEvents.sort(new ExternalEvent.ExternalEventComparator());
                for(ExternalEvent externalEvent : fedamb.externalEvents) {
                    fedamb.federateTime = externalEvent.getTime();
                    switch (externalEvent.getEventType()) {
                        case QUEUE_OVERLOAD:
                            this.queueOverload(timeToAdvance);
                            break;
                    }
                }
                fedamb.externalEvents.clear();
            }

            if(shopStatus == false)
            {
                shopOpen(timeToAdvance);
            }

            if(timeStepCounter > 15)
            {
                shopClose(timeToAdvance);
            }

            rtiamb.tick();
        }

    }

    private void shopOpen(double time) throws RTIexception {
        shopStatus = true;
        sendInteraction_shopOpen(fedamb.federateTime + fedamb.federateLookahead);

    }

    private void shopClose(double time) throws RTIexception {

        shopStatus = false;
        checkoutClose(time);
        sendInteraction_shopClose(fedamb.federateTime + fedamb.federateLookahead);

    }

    private void checkoutOpen(double time) throws RTIexception {
        checkoutCounter += 1;
        sendInteraction_checkoutOpen(fedamb.federateTime + fedamb.federateLookahead);
    }

    private void checkoutClose(double time) throws RTIexception {
        sendInteraction_shopClose(fedamb.federateTime + fedamb.federateLookahead);
    }

    private void queueOverload(double time) throws RTIexception {
        if(checkoutCounter < 4){
            checkoutOpen(time);
        } else {
            log("All checkout's are already open");
        }
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

    private void sendInteraction_shopOpen(double timeStep) throws RTIexception {
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

        int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.ShopOpen");

        LogicalTime time = convertTime( timeStep );
        log("ShopOpen_error: " + time);
        // TSO
        rtiamb.sendInteraction( interactionHandle, parameters, "tag".getBytes(), time );
//        // RO
//        rtiamb.sendInteraction( interactionHandle, parameters, "tag".getBytes() );
    }

    private void sendInteraction_shopClose(double timeStep) throws RTIexception {
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

        int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.ShopClose");

        LogicalTime time = convertTime( timeStep );
        log("shopClose_error: " + time);
        // TSO
        rtiamb.sendInteraction( interactionHandle, parameters, "tag".getBytes(), time );
//        // RO
//        rtiamb.sendInteraction( interactionHandle, parameters, "tag".getBytes() );
    }

    private void sendInteraction_checkoutOpen(double timeStep) throws RTIexception {
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

        int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.CheckoutOpen");

        LogicalTime time = convertTime( timeStep );
        log("CheckoutOpen_error: " + time);
        // TSO
        rtiamb.sendInteraction( interactionHandle, parameters, "tag".getBytes(), time );
//        // RO
//        rtiamb.sendInteraction( interactionHandle, parameters, "tag".getBytes() );
    }

    private void sendInteraction_checkoutClose(double timeStep) throws RTIexception {
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

        int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.CheckoutClose");

        LogicalTime time = convertTime( timeStep );
        log("CheckoutClose_error: " + time);
        // TSO
        rtiamb.sendInteraction( interactionHandle, parameters, "tag".getBytes(), time );
//        // RO
//        rtiamb.sendInteraction( interactionHandle, parameters, "tag".getBytes() );
    }

    private void publishAndSubscribe() throws RTIexception {
        int addProductHandle = rtiamb.getInteractionClassHandle( "InteractionRoot.GetProduct" );
        rtiamb.publishInteractionClass(addProductHandle);

        ////    PUBLISH
        int ShopOpen = rtiamb.getInteractionClassHandle("InteractionRoot.ShopOpen");
        rtiamb.publishInteractionClass(ShopOpen);

        int ShopClose = rtiamb.getInteractionClassHandle("InteractionRoot.ShopClose");
        rtiamb.publishInteractionClass(ShopClose);

        int CheckoutOpen = rtiamb.getInteractionClassHandle("InteractionRoot.CheckoutOpen");
        rtiamb.publishInteractionClass(CheckoutOpen);

        int CheckoutClose = rtiamb.getInteractionClassHandle("InteractionRoot.CheckoutClose");
        rtiamb.publishInteractionClass(CheckoutClose);

        ////    SUBSCRIBE

        int queueOverloadHanle = rtiamb.getInteractionClassHandle("InteractionRoot.QueueOverload");
        fedamb.QueueOverloadHandle = queueOverloadHanle;
        rtiamb.subscribeInteractionClass(queueOverloadHanle);

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
        return 1 +(4 * r.nextDouble());
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
        System.out.println( "ShopFederate   : " + message );
    }

    public static void main(String[] args) {
        try {
            new ShopFederate().runFederate();
        } catch (RTIexception rtIexception) {
            rtIexception.printStackTrace();
        }
    }


}
