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
    int timeStepCounter = 0;

    private boolean shopOpen = false;
    private boolean shopClosed = false;

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
            log("time step: " + ++timeStepCounter, timeToAdvance);

            if (fedamb.externalEvents.size() > 0) {
                fedamb.externalEvents.sort(new ExternalEvent.ExternalEventComparator());
                for (ExternalEvent externalEvent : fedamb.externalEvents) {
                    fedamb.federateTime = externalEvent.getTime();
                    switch (externalEvent.getEventType()) {
                        case SHOP_OPEN:
                            this.openShop();
                            break;
                        case SHOP_CLOSE:
                            this.closeShop(timeToAdvance);
                            break;
                        case END_CHECKOUT:
                            this.endShopping(timeToAdvance,
                                    externalEvent.getParameter("id_client"));
                            break;
                    }
                }
                fedamb.externalEvents.clear();
            }

            if (shopOpen && ThreadLocalRandom.current().nextInt(0, 100) < 10 && !fedamb.queues.isEmpty()) {
                spawnNewClient(timeToAdvance);
            }

            if(shopClosed && clients.isEmpty()) {
                log("no more clients", timeToAdvance);
                sendNoClientsInteraction(timeToAdvance);
                advanceTime(timeToAdvance);
                break;
            }

            for (Map.Entry<Integer, Client> entry : clients.entrySet()) {
                Client client = entry.getValue();
                if (!client.inQueue && --client.shoppingTime <= 0 && !fedamb.queues.isEmpty()) {
                    enterShortestQueue(timeToAdvance, client);
                }
            }

            advanceTime(timeToAdvance);
            rtiamb.tick();
        }

		rtiamb.resignFederationExecution( ResignAction.NO_ACTION );
		log( "resigned from Federation" );
    }

    private void openShop() {
        log("shop is open");
        shopOpen = true;
        shopClosed = false;
    }

    private void closeShop(double time) {
        log("shop is closing", time);
        shopOpen = false;
        shopClosed = true;
    }

    private void endShopping(double time, int idClient) throws RTIexception {
        removeHLAObject(time, idClient);
        log("client [" + idClient + "] exited shop", time);
    }

    private void spawnNewClient(double time) throws RTIexception {
        int idClient = registerCheckoutObject();
        Client client = clients.get(idClient);
        updateHLAObject(time, client);
        log("a client [" + idClient + "] enter the shop", time);
    }

    private void enterShortestQueue(double time, Client client) throws RTIexception {
        int shortestQueueId = -1;
        int shortestQueueLength = 0;
        client.inQueue = true;
        Queue queue = null;
        for (Map.Entry<Integer, Queue> entry : fedamb.queues.entrySet()) {
            queue = entry.getValue();
            if (queue.length == 0) {
                shortestQueueId = queue.idQueue;
                break;
            } else if (shortestQueueLength > queue.length) {
                shortestQueueId = queue.idQueue;
                shortestQueueLength = queue.length;
            }
        }
        if (shortestQueueId != -1 && queue != null) {
            queue.length++;
            fedamb.queues.put(queue.idQueue, queue);

            updateHLAObject(time, client);
            sendJoinQueueInteraction(time, client.idClient, shortestQueueId);
            log("client [" + client.idClient + "] is entering the shortest queue [" + shortestQueueId + "], number of people: " + shortestQueueLength, time);
        }
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

    private void updateHLAObject(double time, Client client) throws RTIexception {
        SuppliedAttributes attributes =
                RtiFactoryFactory.getRtiFactory().createSuppliedAttributes();

        int classHandle = rtiamb.getObjectClass(client.idClient);

        int idClientHandle = rtiamb.getAttributeHandle("idClient", classHandle);
        byte[] byteIdClient = EncodingHelpers.encodeInt(client.idClient);
        attributes.add(idClientHandle, byteIdClient);

        int priorityHandle = rtiamb.getAttributeHandle("priority", classHandle);
        byte[] bytePriorityHandle = EncodingHelpers.encodeInt(client.priority);
        attributes.add(priorityHandle, bytePriorityHandle);

        LogicalTime logicalTime = convertTime(time);
        rtiamb.updateAttributeValues(client.idClient, attributes, "actualize checkout".getBytes(), logicalTime);
        clients.put(client.idClient, client);
    }

    private void removeHLAObject(double time, int idClient) throws RTIexception {
        LogicalTime logicalTime = convertTime(time);
        clients.remove(idClient);
        rtiamb.deleteObjectInstance(idClient, "remove client".getBytes(), logicalTime);
    }

    private void sendJoinQueueInteraction(double timeStep, int idClient, int idQueue) throws RTIexception {
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

        int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.JoinQueue");

        int idClientHandle = rtiamb.getParameterHandle("idClient", interactionHandle);
        byte[] byteIdClient = EncodingHelpers.encodeInt(idClient);
        parameters.add(idClientHandle, byteIdClient);

        int idQueueHandle = rtiamb.getParameterHandle("idQueue", interactionHandle);
        byte[] byteIdQueue = EncodingHelpers.encodeInt(idQueue);
        parameters.add(idQueueHandle, byteIdQueue);

        LogicalTime time = convertTime(timeStep);
        rtiamb.sendInteraction(interactionHandle, parameters, "tag".getBytes(), time);
    }

    private void sendNoClientsInteraction(double timeStep) throws RTIexception {
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

        int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.NolCients");

        LogicalTime time = convertTime(timeStep);
        rtiamb.sendInteraction(interactionHandle, parameters, "tag".getBytes(), time);
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

        int noClientsQueue = rtiamb.getInteractionClassHandle("InteractionRoot.NoClients");
        rtiamb.publishInteractionClass(noClientsQueue);
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
        System.out.println("ClientFederate  : " + message);
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
