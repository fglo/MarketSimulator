package sim.actors.client;

import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.RtiFactoryFactory;
import org.portico.impl.hla13.types.DoubleTime;
import org.portico.impl.hla13.types.DoubleTimeInterval;
import sim.objects.Client;
import sim.objects.Queue;
import sim.utils.AFederate;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class ClientFederate extends AFederate<ClientAmbassador> {

    private HashMap<Integer, Client> clients = new HashMap<>();

    private boolean shopOpen = false;
    private boolean doorOpen = false;
    private boolean shopClosed = false;
    private boolean finish = false;

    private int maxNumberOfClientsAtOneTime = 0;

    @Override
    public void runFederate() throws RTIexception {
        super.runFederate();

        while (fedamb.running) {
            advanceTime(timeStep);

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
                            this.endShopping(externalEvent.getParameter("id_client"));
                            break;
                        case FINISH:
                            finish = true;
                            break;
                        case LET_CLIENT_IN:
                            this.letClientIn(externalEvent.getParameter("id_client"));
                            break;
                        case REJECT_CLIENT:
                            this.rejectClient(externalEvent.getParameter("id_client"));
                            break;
                        case OPEN_DOOR:
                            doorOpen = true;
                            break;
                        case CLOSE_DOOR:
                            doorOpen = false;
                            break;
                    }
                }
                fedamb.externalEvents.clear();
            }

            if(finish) {
                log("max number of clients int the shop at one time: " + maxNumberOfClientsAtOneTime);
                break;
            }

            if (doorOpen && shopOpen && ThreadLocalRandom.current().nextInt(0, 100) < 101 && !fedamb.queues.isEmpty()) {
                for(int i = 0; i < ThreadLocalRandom.current().nextInt(2, 6); i++) {
                    spawnNewClient();
                }
            }

            if(shopClosed && clients.isEmpty()) {
                log("no more clients", fedamb.federateTime);
                sendNoClientsInteraction();
            }

            for (Map.Entry<Integer, Client> entry : clients.entrySet()) {
                Client client = entry.getValue();
                if(client.shoppingTime > 0) {
                    client.shoppingTime--;
                    if (!client.inQueue && client.shoppingTime <= 0 && !fedamb.queues.isEmpty()) {
                        enterShortestQueue(client);
                    }
                    clients.put(client.idClient, client);
                }
            }

            rtiamb.tick();
        }

		rtiamb.resignFederationExecution( ResignAction.NO_ACTION );
		log( "resigned from Federation" );
    }

    @Override
    protected ClientAmbassador getNewFedAmbInstance() {
        return new ClientAmbassador();
    }

    private void openShop() {
        log("shop is open");
        shopOpen = true;
        shopClosed = false;
        doorOpen = true;
    }

    private void closeShop() {
        log("shop is closing, clients left: " + clients.size(), fedamb.federateTime);
        shopOpen = false;
        shopClosed = true;
    }

    private void endShopping(int idClient) throws RTIexception {
        removeHLAObject(idClient);
        clients.remove(idClient);
        log("client [" + idClient + "] exited shop, clients left: " + clients.size(), fedamb.federateTime);
    }

    private void spawnNewClient() throws RTIexception {
        int idClient = registerCheckoutObject();
        Client client = clients.get(idClient);
        updateHLAObject(client);
        log("a client [" + idClient + "] enter the shop", fedamb.federateTime);
    }

    private void letClientIn(int idClient) throws RTIexception {
        Client client = clients.get(idClient);
        client.startShopping();
        updateHLAObject(client);
        log("client [" + idClient + "] entered the shop, clients in the shop: " + clients.size(), fedamb.federateTime);
        if(maxNumberOfClientsAtOneTime < clients.size()) {
            maxNumberOfClientsAtOneTime = clients.size();
        }
    }

    private void rejectClient(int idClient) throws RTIexception {
        removeHLAObject(idClient);
        clients.remove(idClient);
        log("client [" + idClient + "] was rejected", fedamb.federateTime);
    }

    private void enterShortestQueue(Client client) throws RTIexception {
        int shortestQueueId = -1;
        int shortestQueueLength = -1;
        for (Map.Entry<Integer, Queue> entry : fedamb.queues.entrySet()) {
            Queue queue = entry.getValue();
            if (queue.length == 0) {
                shortestQueueId = queue.idQueue;
                break;
            } else if (shortestQueueLength == -1 || shortestQueueLength > queue.length) {
                shortestQueueId = queue.idQueue;
                shortestQueueLength = queue.length;
            }
        }
        if (shortestQueueId != -1) {
            Queue queue = fedamb.queues.get(shortestQueueId);
            updateHLAObject(client);
            sendJoinQueueInteraction(client.idClient, shortestQueueId);
            log("client [" + client.idClient + "] is entering the shortest queue [" + shortestQueueId + "], number of people: " + shortestQueueLength, fedamb.federateTime);
            queue.length++;
            fedamb.queues.put(queue.idQueue, queue);
            client.inQueue = true;
        }
    }

    private int registerCheckoutObject() throws RTIexception {
        int classHandle = rtiamb.getObjectClassHandle("ObjectRoot.Client");
        int idClient = rtiamb.registerObjectInstance(classHandle);
        clients.put(idClient, new Client(idClient));
        return idClient;
    }

    private void updateHLAObject(Client client) throws RTIexception {
        SuppliedAttributes attributes =
                RtiFactoryFactory.getRtiFactory().createSuppliedAttributes();

        int classHandle = rtiamb.getObjectClass(client.idClient);

        int idClientHandle = rtiamb.getAttributeHandle("idClient", classHandle);
        byte[] byteIdClient = EncodingHelpers.encodeInt(client.idClient);
        attributes.add(idClientHandle, byteIdClient);

        int priorityHandle = rtiamb.getAttributeHandle("priority", classHandle);
        byte[] bytePriorityHandle = EncodingHelpers.encodeInt(client.priority);
        attributes.add(priorityHandle, bytePriorityHandle);

        rtiamb.updateAttributeValues(client.idClient, attributes, "actualize checkout".getBytes(), getLogicalTime());
        clients.put(client.idClient, client);
    }

    private void removeHLAObject(int idClient) throws RTIexception {
        rtiamb.deleteObjectInstance(idClient, "remove client".getBytes(), getLogicalTime());
    }

    private void sendJoinQueueInteraction(int idClient, int idQueue) throws RTIexception {
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

        int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.JoinQueue");

        int idClientHandle = rtiamb.getParameterHandle("idClient", interactionHandle);
        byte[] byteIdClient = EncodingHelpers.encodeInt(idClient);
        parameters.add(idClientHandle, byteIdClient);

        int idQueueHandle = rtiamb.getParameterHandle("idQueue", interactionHandle);
        byte[] byteIdQueue = EncodingHelpers.encodeInt(idQueue);
        parameters.add(idQueueHandle, byteIdQueue);

        rtiamb.sendInteraction(interactionHandle, parameters, "tag".getBytes(), getLogicalTime());
    }

    private void sendNoClientsInteraction() throws RTIexception {
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

        int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.NoClients");

        rtiamb.sendInteraction(interactionHandle, parameters, "tag".getBytes(), getLogicalTime());
    }

    @Override
    protected void publish() throws RTIexception {
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

    @Override
    protected void subscribe() throws RTIexception {
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

        int finishHandle = rtiamb.getInteractionClassHandle("InteractionRoot.Finish");
        fedamb.finishHandle = finishHandle;
        rtiamb.subscribeInteractionClass(finishHandle);

        int letClientInHandle = rtiamb.getInteractionClassHandle("InteractionRoot.LetClientIn");
        fedamb.letClientInHandle = letClientInHandle;
        rtiamb.subscribeInteractionClass(letClientInHandle);

        int rejectClientHandle = rtiamb.getInteractionClassHandle("InteractionRoot.RejectClient");
        fedamb.rejectClientHandle = rejectClientHandle;
        rtiamb.subscribeInteractionClass(rejectClientHandle);

        int openDoorHandle = rtiamb.getInteractionClassHandle("InteractionRoot.OpenDoor");
        fedamb.openDoorHandle = openDoorHandle;
        rtiamb.subscribeInteractionClass(openDoorHandle);

        int closeDoorHandle = rtiamb.getInteractionClassHandle("InteractionRoot.CloseDoor");
        fedamb.closeDoorHandle = closeDoorHandle;
        rtiamb.subscribeInteractionClass(closeDoorHandle);
    }

    public static void main(String[] args) {
        try {
            new ClientFederate().runFederate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
