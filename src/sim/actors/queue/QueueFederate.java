package sim.actors.queue;


import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.RtiFactoryFactory;
import org.portico.impl.hla13.types.DoubleTime;
import org.portico.impl.hla13.types.DoubleTimeInterval;
import sim.objects.Checkout;
import sim.objects.Client;
import sim.objects.Queue;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class QueueFederate {

    public static final String READY_TO_RUN = "ReadyToRun";

    private RTIambassador rtiamb;
    private QueueAmbassador fedamb;
    private final double timeStep           = 10.0;
    private HashMap<Integer, Queue> queues = new HashMap<>();

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
            double timeToAdvance = fedamb.federateTime + fedamb.federateLookahead; //fedamb.federateTime + timeStep;

            if (fedamb.externalEvents.size() > 0) {
                fedamb.externalEvents.sort(new ExternalEvent.ExternalEventComparator());
                for(ExternalEvent externalEvent : fedamb.externalEvents) {
                    fedamb.federateTime = externalEvent.getTime();
                    switch (externalEvent.getEventType()) {
                        case SHOP_CLOSE:
                            this.shopClose();
                            break;
                        case OPEN_QUEUE:
                            this.openQueue(timeToAdvance,
                                    externalEvent.getParameter("id_checkout"));
                            break;
                        case JOIN_QUEUE:
                            this.joinQueue(timeToAdvance,
                                    externalEvent.getParameter("id_client"),
                                    externalEvent.getParameter("id_queue"));
                            break;
                    }
                }
                fedamb.externalEvents.clear();
            }

            for (Map.Entry<Integer, Queue> entry : queues.entrySet()) {
                Queue queue = entry.getValue();
                Checkout checkout = fedamb.checkouts.get(queue.idCheckout);
                if(checkout != null && checkout.idClient == -1 && !queue.clientsInQueue.isEmpty()) {
                    int idClientToGo = queue.clientsInQueue.get(0);
                    for (int idClient : queue.clientsInQueue) {
                        Client client = fedamb.clients.get(idClient);
                        if(client != null && client.priority == 1) {
                            idClientToGo = idClient;
                            log("found client with priority=true", timeToAdvance);
                            break;
                        }
                    }
                    sendToCheckout(timeToAdvance, queue.idQueue, idClientToGo, queue.idCheckout);
                }
            }

            advanceTime( timeToAdvance );
            //log( "Time Advanced to " + fedamb.federateTime );

            rtiamb.tick();
        }
    }

    private void shopClose() {
        log("shop is closing");
        shopOpen = false;
    }

    private void openQueue(double time, int idCheckout) throws RTIexception {
        int idQueue = registerQueueObject(idCheckout);
        Queue queue = queues.get(idQueue);
        updateHLAObject(time, queue);
        log("open queue", time);
    }

    private void joinQueue(double time, int idClient, int idQueue) throws RTIexception {
        Queue queue = queues.get(idQueue);
        if(queue == null) {
            log("queue with id: " + idQueue + " was not found.");
            return;
        }
        queue.addToQueue(idClient);
        updateHLAObject(time, queue);
        log("join queue", time);

        if(queue.length > 5) {
            sendQueueOverloadInteraction(time, idQueue);
            log( "queue is overloaded", time);
        }
    }

    private void sendToCheckout(double time, int idQueue, int idClient, int idCheckout) throws RTIexception {
        Queue queue = queues.get(idQueue);
        if(queue == null) {
            log("queue with id: " + idQueue + " was not found.", time);
            return;
        }
        queue.removeFromQueue(idClient);
        sendSendToCheckoutInteraction(time, idClient, idCheckout);
        updateHLAObject(time, queue);
        log("send to checkout", time);
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

    private int registerQueueObject(int idCheckout) throws RTIexception {
        int classHandle = rtiamb.getObjectClassHandle("ObjectRoot.Queue");
        int idQueue = rtiamb.registerObjectInstance(classHandle);
        queues.put(idQueue, new Queue(idQueue, idCheckout));
        return idQueue;
    }

    private void updateHLAObject(double time, Queue queue) throws RTIexception {
        SuppliedAttributes attributes =
                RtiFactoryFactory.getRtiFactory().createSuppliedAttributes();
        int classHandle = rtiamb.getObjectClass(queue.idQueue);

        int idClientHandle = rtiamb.getAttributeHandle("idQueue", classHandle);
        byte[] byteIdClient = EncodingHelpers.encodeInt(queue.idQueue);
        attributes.add(idClientHandle, byteIdClient);

        int idCheckoutHandle = rtiamb.getAttributeHandle("idCheckout", classHandle);
        byte[] byteIdCheckoutHandle = EncodingHelpers.encodeInt(queue.idCheckout);
        attributes.add(idCheckoutHandle, byteIdCheckoutHandle);

        int lengthHandle = rtiamb.getAttributeHandle("length", classHandle);
        byte[] byteLengthHandle = EncodingHelpers.encodeInt(queue.idCheckout);
        attributes.add(lengthHandle, byteLengthHandle);

        LogicalTime logicalTime = convertTime(time);
        rtiamb.updateAttributeValues(queue.idQueue, attributes, "actualize checkout".getBytes(), logicalTime);
        queues.put(queue.idQueue, queue);
    }

    private void removeHLAObject(double time, int idQueue) throws RTIexception {
        LogicalTime logicalTime = convertTime(time);
        rtiamb.deleteObjectInstance(idQueue, "remove client".getBytes(), logicalTime);
        queues.remove(idQueue);
    }

    private void sendSendToCheckoutInteraction(double timeStep, int idClient, int idCheckout) throws RTIexception {
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();
        Random random = new Random();

        int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.SendToCheckout");

        byte[] byteIdClient = EncodingHelpers.encodeInt(idClient);
        int idClientHandle = rtiamb.getParameterHandle( "idClient", interactionHandle );
        parameters.add(idClientHandle, byteIdClient);

        byte[] byteIdCheckout = EncodingHelpers.encodeInt(idCheckout);
        int idCheckoutHandle = rtiamb.getParameterHandle( "idCheckout", interactionHandle );
        parameters.add(idCheckoutHandle, byteIdCheckout);

        LogicalTime time = convertTime( timeStep );
        rtiamb.sendInteraction( interactionHandle, parameters, "tag".getBytes(), time );
    }

    private void sendQueueOverloadInteraction(double timeStep, int idQueue) throws RTIexception {
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();
        Random random = new Random();

        int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.QueueOverload");

        byte[] byteIdQueue = EncodingHelpers.encodeInt(idQueue);
        int idQueueHandle = rtiamb.getParameterHandle( "idQueue", interactionHandle );
        parameters.add(idQueueHandle, byteIdQueue);

        LogicalTime time = convertTime( timeStep );
        rtiamb.sendInteraction( interactionHandle, parameters, "tag".getBytes(), time );
    }

    private void publish() throws RTIexception {

        int queueHandle = rtiamb.getObjectClassHandle("ObjectRoot.Queue");
        int idQueue = rtiamb.getAttributeHandle("idQueue", queueHandle);
        int idCheckout = rtiamb.getAttributeHandle("idCheckout", queueHandle);
        int lenght = rtiamb.getAttributeHandle("length", queueHandle);
        AttributeHandleSet attributeHandleSet =
                RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
        attributeHandleSet.add(idQueue);
        attributeHandleSet.add(idCheckout);
        attributeHandleSet.add(lenght);
        rtiamb.publishObjectClass(queueHandle, attributeHandleSet);

        int sendToCheckout = rtiamb.getInteractionClassHandle("InteractionRoot.SendToCheckout");
        rtiamb.publishInteractionClass(sendToCheckout);

        int queueOverload = rtiamb.getInteractionClassHandle("InteractionRoot.QueueOverload");
        rtiamb.publishInteractionClass(queueOverload);
    }

    private void subscribe() throws RTIexception {

        int clientHandle = rtiamb.getObjectClassHandle("ObjectRoot.Client");
        fedamb.clientHandle = clientHandle;
        int idClientHandleClient = rtiamb.getAttributeHandle("idClient", clientHandle);
        int priopityHandle = rtiamb.getAttributeHandle("priority", clientHandle);
        AttributeHandleSet attributes =
                RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
        attributes.add(idClientHandleClient);
        attributes.add(priopityHandle);
        rtiamb.subscribeObjectClassAttributes(clientHandle, attributes);

        int checkoutHandle = rtiamb.getObjectClassHandle("ObjectRoot.Checkout");
        fedamb.checkoutHandle = checkoutHandle;
        int idCheckoutHandle = rtiamb.getAttributeHandle("idCheckout", checkoutHandle);
        int idClientHandleCheckout = rtiamb.getAttributeHandle("idClient", checkoutHandle);
        AttributeHandleSet checkoutAttributes =
                RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
        checkoutAttributes.add(idCheckoutHandle);
        checkoutAttributes.add(idClientHandleCheckout);
        rtiamb.subscribeObjectClassAttributes(checkoutHandle, checkoutAttributes);

        int shopCloseHandle = rtiamb.getInteractionClassHandle("InteractionRoot.ShopClose");
        fedamb.shopCloseHandle = shopCloseHandle;
        rtiamb.subscribeInteractionClass(shopCloseHandle);

        int openQueueHandle = rtiamb.getInteractionClassHandle("InteractionRoot.QueueOpen");
        fedamb.openQueueHandle = openQueueHandle;
        rtiamb.subscribeInteractionClass(openQueueHandle);

        int joinQueueHandle = rtiamb.getInteractionClassHandle("InteractionRoot.JoinQueue");
        fedamb.joinQueueHandle = joinQueueHandle;
        rtiamb.subscribeInteractionClass(joinQueueHandle);
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

    private void log(String message, double time) {
        System.out.println("QueueFederate  : " + message + ", time: " + time);
    }

    public static void main(String[] args) {
        try {
            new QueueFederate().runFederate();
        } catch (RTIexception rtIexception) {
            rtIexception.printStackTrace();
        }
    }

}
