package sim.actors.shop;

import hla.rti.*;
import hla.rti.jlc.RtiFactoryFactory;
import org.portico.impl.hla13.types.DoubleTime;
import org.portico.impl.hla13.types.DoubleTimeInterval;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

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
            double timeToAdvance = fedamb.federateTime + fedamb.federateLookahead; //fedamb.federateTime + timeStep;
            timeStepCounter ++;

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
                openShop(timeToAdvance);
                for (int i = 0; i < 3; i++) {
                    openCheckout(timeToAdvance);
                }
            }
//            if(timeStepCounter == 100)
//            {
//                shopClose(timeToAdvance);
//            }

            advanceTime( timeToAdvance );
            //log( "Time Advanced to " + fedamb.federateTime );

            rtiamb.tick();
        }

    }

    private void openShop(double time) throws RTIexception {
        shopStatus = true;
        sendOpenShopInteraction(time);
    }

    private void closeShop(double time) throws RTIexception {
        shopStatus = false;
        closeCheckout(time);
        sendCloseShopInteraction(time);

    }

    private void openCheckout(double time) throws RTIexception {
        checkoutCounter += 1;
        sendOpenCheckoutInteraction(time);
    }

    private void closeCheckout(double time) throws RTIexception {
        sendCloseShopInteraction(time);
    }

    private void queueOverload(double time) throws RTIexception {
        openCheckout(time);
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

    private void sendOpenShopInteraction(double timeStep) throws RTIexception {
        LogicalTime time = convertTime( timeStep );
        log("shop opens", timeStep);

        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

        int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.ShopOpen");
        rtiamb.sendInteraction( interactionHandle, parameters, "tag".getBytes(), time );
    }

    private void sendCloseShopInteraction(double timeStep) throws RTIexception {
        LogicalTime time = convertTime( timeStep );
        log("shop closes", timeStep);

        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

        int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.ShopClose");
        rtiamb.sendInteraction( interactionHandle, parameters, "tag".getBytes(), time );
    }

    private void sendOpenCheckoutInteraction(double timeStep) throws RTIexception {
        LogicalTime time = convertTime( timeStep );
        log("checkout opens", timeStep);

        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

        int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.CheckoutOpen");
        rtiamb.sendInteraction( interactionHandle, parameters, "tag".getBytes(), time );
    }

    private void sendCloseCheckoutInteraction(double timeStep) throws RTIexception {
        LogicalTime time = convertTime( timeStep );
        log("checkout closes", timeStep);

        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

        int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.CheckoutClose");
        rtiamb.sendInteraction( interactionHandle, parameters, "tag".getBytes(), time );
    }

    private void publishAndSubscribe() throws RTIexception {
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
        fedamb.queueOverloadHandle = queueOverloadHanle;
        rtiamb.subscribeInteractionClass(queueOverloadHanle);
    }

    private void advanceTime( double timestep ) throws RTIexception {
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

    private void log(String message) {
        System.out.println("ShopFederate  : " + message );
    }

    private void log(String message, double time) {
        System.out.println("ShopFederate  : " + message + ", time: " + time);
    }

    public static void main(String[] args) {
        try {
            new ShopFederate().runFederate();
        } catch (RTIexception rtIexception) {
            rtIexception.printStackTrace();
        }
    }


}
