package sim.actors.client;

import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.RtiFactoryFactory;
import org.portico.impl.hla13.types.DoubleTime;
import org.portico.impl.hla13.types.DoubleTimeInterval;
import sim.objects.Client;
import sim.objects.Queue;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class ClientFederate {

    public static final String READY_TO_RUN = "ReadyToRun";

    private RTIambassador rtiamb;
    private ClientAmbassador fedamb;
    private final double timeStep = 10.0;
    private HashMap<Integer, Client> clients = new HashMap<>();

    private boolean shopOpen = false;

    public void runFederate() throws Exception {
        rtiamb = RtiFactoryFactory.getRtiFactory().createRtiAmbassador();

        try {
            File fom = new File("market.fed");
            rtiamb.createFederationExecution("MarketFederation",
                    fom.toURI().toURL());
            log("Created Federation");
        } catch (FederationExecutionAlreadyExists exists) {
            log("Didn't create federation, it already existed");
        } catch (MalformedURLException urle) {
            log("Exception processing fom: " + urle.getMessage());
            urle.printStackTrace();
            return;
        }

        fedamb = new ClientAmbassador();
        rtiamb.joinFederationExecution("ClientFederate", "MarketFederation", fedamb);
        log("Joined Federation as " + "ClientFederate");

        rtiamb.registerFederationSynchronizationPoint(READY_TO_RUN, null);

        while (fedamb.isAnnounced == false) {
            rtiamb.tick();
        }

        waitForUser();

        rtiamb.synchronizationPointAchieved(READY_TO_RUN);
        log("Achieved sync point: " + READY_TO_RUN + ", waiting for federation...");
        while (fedamb.isReadyToRun == false) {
            rtiamb.tick();
        }

        enableTimePolicy();

        publish();
        subscribe();

        while (fedamb.running) {
            double timeToAdvance = fedamb.federateTime + fedamb.federateLookahead; //fedamb.federateTime + timeStep;

            if (fedamb.externalEvents.size() > 0) {
                fedamb.externalEvents.sort(new ExternalEvent.ExternalEventComparator());
                for (ExternalEvent externalEvent : fedamb.externalEvents) {
                    fedamb.federateTime = externalEvent.getTime();
                    switch (externalEvent.getEventType()) {
                        case SHOP_OPEN:
                            this.openShop();
                            break;
                        case SHOP_CLOSE:
                            this.closeShop();
                            break;
                        case END_CHECKOUT:
                            this.endShopping(timeToAdvance,
                                    externalEvent.getParameter("id_client"));
                            break;
                    }
                }
                fedamb.externalEvents.clear();
            }

            if (shopOpen) {
                for (int i = 0; i < ThreadLocalRandom.current().nextInt(0, 3); i++) {
                    spawnNewClient(timeToAdvance);
                }
            }

            for (Map.Entry<Integer, Client> entry : clients.entrySet()) {
                if (--entry.getValue().shoppingTime <= 0 && !fedamb.queues.isEmpty()) {
                    enterShortestQueue(timeToAdvance, entry.getValue());
                }
            }

            advanceTime( timeToAdvance );
            //log( "Time Advanced to " + fedamb.federateTime );

            rtiamb.tick();
        }

//		rtiamb.resignFederationExecution( ResignAction.NO_ACTION );
//		log( "Resigned from Federation" );
//
//		try
//		{
//			rtiamb.destroyFederationExecution( "MarketFederation" );
//			log( "Destroyed Federation" );
//		}
//		catch( FederationExecutionDoesNotExist dne )
//		{
//			log( "No need to destroy federation, it doesn't exist" );
//		}
//		catch( FederatesCurrentlyJoined fcj )
//		{
//			log( "Didn't destroy federation, federates still joined" );
//		}
    }

    private void openShop() {
        log("shop is open");
        shopOpen = true;
    }

    private void closeShop() {
        log("shop is closing");
        shopOpen = false;
    }

    private void endShopping(double time, int idClient) throws RTIexception {
        removeHLAObject(time, idClient);
        log("customer has been served", time);
    }

    private void spawnNewClient(double time) throws RTIexception {
        int idClient = registerCheckoutObject();
        updateHLAObject(time, idClient);
        log("a client enter the shop", time);
    }

    private void enterShortestQueue(double time, Client client) throws RTIexception {
        int shortestQueueId = -1;
        int shortestQueueLength = 0;
        for(Map.Entry < Integer, Queue> entry : fedamb.queues.entrySet()) {
            Queue queue = entry.getValue();
            if(queue.clientsInQueue.isEmpty()) {
                shortestQueueId = queue.idQueue;
                break;
            } else if (shortestQueueLength == 0 || shortestQueueLength > queue.length) {
                shortestQueueId = queue.idQueue;
                shortestQueueLength = queue.length;
            }
        }
        sendJoinQueueInteraction(time, client.idClient, shortestQueueId);
        log("client is entering the shortest queue", time);
    }

    private void waitForUser() {
        log(" >>>>>>>>>> Press Enter to Continue <<<<<<<<<<");
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        try {
            reader.readLine();
        } catch (Exception e) {
            log("Error while waiting for user input: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private int registerCheckoutObject() throws RTIexception {
        int classHandle = rtiamb.getObjectClassHandle("ObjectRoot.Client");
        int idClient = rtiamb.registerObjectInstance(classHandle);
        clients.put(idClient, new Client(idClient));
        return idClient;
    }

    private void updateHLAObject(double time, int idClient) throws RTIexception {
        SuppliedAttributes attributes =
                RtiFactoryFactory.getRtiFactory().createSuppliedAttributes();

        Client client = clients.get(idClient);
        int classHandle = rtiamb.getObjectClass(client.idClient);

        int idClientHandle = rtiamb.getAttributeHandle("idClient", classHandle);
        byte[] byteIdClient = EncodingHelpers.encodeInt(client.idClient);
        attributes.add(idClientHandle, byteIdClient);

        int priorityHandle = rtiamb.getAttributeHandle("priority", classHandle);
        byte[] bytePriorityHandle = EncodingHelpers.encodeInt(client.priority);
        attributes.add(priorityHandle, bytePriorityHandle);

        LogicalTime logicalTime = convertTime(time);
        rtiamb.updateAttributeValues(client.idClient, attributes, "actualize checkout".getBytes(), logicalTime);
        clients.put(idClient, client);
    }

    private void removeHLAObject(double time, int idClient) throws RTIexception {
        LogicalTime logicalTime = convertTime(time);
        rtiamb.deleteObjectInstance(idClient, "remove client".getBytes(), logicalTime);
        clients.remove(idClient);
    }

    private void sendJoinQueueInteraction(double timeStep, int idClient, int idQueue) throws RTIexception {
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

        int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.JoinQueue");

        int idClientHandle = rtiamb.getParameterHandle( "idClient", interactionHandle );
        byte[] byteIdClient = EncodingHelpers.encodeInt(idClient);
        parameters.add(idClientHandle, byteIdClient);

        int idQueueHandle = rtiamb.getParameterHandle( "idQueue", interactionHandle );
        byte[] byteIdQueue = EncodingHelpers.encodeInt(idClient);
        parameters.add(idQueueHandle, byteIdQueue);

        LogicalTime time = convertTime( timeStep );
        rtiamb.sendInteraction( interactionHandle, parameters, "tag".getBytes(), time );
    }

    private void advanceTime(double timeToAdvance) throws RTIexception {
        fedamb.isAdvancing = true;
        LogicalTime newTime = convertTime(timeToAdvance);
        rtiamb.timeAdvanceRequest(newTime);

        while (fedamb.isAdvancing) {
            rtiamb.tick();
        }
    }

    private void publish() throws RTIexception {
        int classHandle = rtiamb.getObjectClassHandle("ObjectRoot.Client");
        int idClientHandle = rtiamb.getAttributeHandle("idClient", classHandle);
        int priorityHandle = rtiamb.getAttributeHandle("priority", classHandle);
        AttributeHandleSet attributes =
                RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
        attributes.add(idClientHandle);
        attributes.add(priorityHandle);
        rtiamb.publishObjectClass(classHandle, attributes);

        int joinQueue = rtiamb.getInteractionClassHandle("InteractionRoot.JoinQueue");
        rtiamb.publishInteractionClass(joinQueue);
    }

    private void subscribe() throws RTIexception {
        int queueHandle = rtiamb.getObjectClassHandle("ObjectRoot.Queue");
        fedamb.queueHandle = queueHandle;
        int idQueueHandle = rtiamb.getAttributeHandle("idQueue", queueHandle);
        int idCheckoutHandle = rtiamb.getAttributeHandle("idCheckout", queueHandle);
        int lenghtHandle = rtiamb.getAttributeHandle("length", queueHandle);
        AttributeHandleSet attributes =
                RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
        attributes.add(idQueueHandle);
        attributes.add(idCheckoutHandle);
        attributes.add(lenghtHandle);
        rtiamb.subscribeObjectClassAttributes(queueHandle, attributes);

        int shopOpenHandle = rtiamb.getInteractionClassHandle("InteractionRoot.ShopOpen");
        fedamb.shopOpenHandle = shopOpenHandle;
        rtiamb.subscribeInteractionClass(shopOpenHandle);

        int shopCloseHandle = rtiamb.getInteractionClassHandle("InteractionRoot.ShopClose");
        fedamb.shopCloseHandle = shopCloseHandle;
        rtiamb.subscribeInteractionClass(shopCloseHandle);

        int endCheckoutHandle = rtiamb.getInteractionClassHandle("InteractionRoot.FinishCheckoutService");
        fedamb.endCheckoutHandle = endCheckoutHandle;
        rtiamb.subscribeInteractionClass(endCheckoutHandle);
    }


    private void enableTimePolicy() throws RTIexception {
        // NOTE: Unfortunately, the LogicalTime/LogicalTimeInterval create code is
        //       Portico specific. You will have to alter this if you move to a
        //       different RTI implementation. As such, we've isolated it into a
        //       method so that any change only needs to happen in a couple of spots
        LogicalTime currentTime = convertTime(fedamb.federateTime);
        LogicalTimeInterval lookahead = convertInterval(fedamb.federateLookahead);

        ////////////////////////////
        // enable time regulation //
        ////////////////////////////
        this.rtiamb.enableTimeRegulation(currentTime, lookahead);

        // tick until we get the callback
        while (fedamb.isRegulating == false) {
            rtiamb.tick();
        }

        /////////////////////////////
        // enable time constrained //
        /////////////////////////////
        this.rtiamb.enableTimeConstrained();

        // tick until we get the callback
        while (fedamb.isConstrained == false) {
            rtiamb.tick();
        }
    }

    private LogicalTime convertTime(double time) {
        // PORTICO SPECIFIC!!
        return new DoubleTime(time);
    }

    /**
     * Same as for {@link #convertTime(double)}
     */
    private LogicalTimeInterval convertInterval(double time) {
        // PORTICO SPECIFIC!!
        return new DoubleTimeInterval(time);
    }

    private void log(String message) {
        System.out.println("ClientFederate  : " + message );
    }

    private void log(String message, double time) {
        System.out.println("ClientFederate  : " + message + ", time: " + time);
    }

    public static void main(String[] args) {
        ClientFederate sf = new ClientFederate();

        try {
            sf.runFederate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
