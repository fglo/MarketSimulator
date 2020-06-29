package sim.actors.queue;


import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.RtiFactoryFactory;
import org.portico.impl.hla13.types.DoubleTime;
import org.portico.impl.hla13.types.DoubleTimeInterval;
import sim.objects.Checkout;
import sim.objects.Client;
import sim.objects.Queue;
import sim.utils.AFederate;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class QueueFederate extends AFederate<QueueAmbassador> {

    private HashMap<Integer, Queue> queues = new HashMap<>();

    private boolean shopOpen = false;
    private boolean noClients = false;
    private boolean finish = false;

    private int maxPeopleInQueue = 0;

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
                        case SHOP_CLOSE:
                            log("shop is closing", fedamb.federateTime);
                            shopOpen = false;
                            break;
                        case OPEN_QUEUE:
                            this.openQueue(externalEvent.getParameter("id_checkout"));
                            break;
                        case JOIN_QUEUE:
                            this.joinQueue(
                                    externalEvent.getParameter("id_client"),
                                    externalEvent.getParameter("id_queue"));
                            break;
                        case NO_CLIENTS:
                            noClients = true;
                            break;
                        case FINISH_CHECKOUT:
                            this.finishCheckout(externalEvent.getParameter("id_checkout"));
                            break;
                        case FINISH:
                            finish = true;
                            break;
                    }
                }
                fedamb.externalEvents.clear();
            }

            if (finish) {
                break;
            }

            if (!shopOpen && noClients) {
                for (Map.Entry<Integer, Queue> entry : queues.entrySet()) {
                    Queue queue = entry.getValue();
                    log("max length of queue [" + queue.idQueue + "] was: " + queue.getMaxLength());
                    removeHLAObject(queue.idQueue);
                }
                queues.clear();
                sendQueuesEmptyInteraction();
            }

            for (Map.Entry<Integer, Queue> entry : queues.entrySet()) {
                Queue queue = entry.getValue();
                if (!queue.isCheckoutBusy && queue.length > 0) {
                    int idClientToGo = queue.clientsInQueue.get(0);
                    for (int idClient : queue.clientsInQueue) {
                        Client client = fedamb.clients.get(idClient);
                        if (client != null && client.priority == 1) {
                            idClientToGo = idClient;
                            log("found client [" + idClient + "] with priority=true", fedamb.federateTime);
                            break;
                        }
                    }
                    sendToCheckout(queue, idClientToGo);
                } else if (queue.isCheckoutBusy && queue.length > 0) {
                    log("checkout [" + queue.idCheckout + "] is busy, length of queue [" + queue.idQueue + "]: " + queue.length, fedamb.federateTime);
                }
            }

            rtiamb.tick();
        }

        rtiamb.resignFederationExecution(ResignAction.NO_ACTION);

        log("resigned from Federation");
    }

    @Override
    protected QueueAmbassador getNewFedAmbInstance() {
        return new QueueAmbassador();
    }

    private void openQueue(int idCheckout) throws RTIexception {
        int idQueue = registerQueueObject(idCheckout);
        Queue queue = queues.get(idQueue);
        updateHLAObject(queue);
        log("open queue [" + idQueue + "]", fedamb.federateTime);
    }

    private void joinQueue(int idClient, int idQueue) throws RTIexception {
        Queue queue = queues.get(idQueue);
        if (queue == null) {
            log("queue with [" + idQueue + "] was not found", fedamb.federateTime);
            return;
        }
        queue.addToQueue(idClient);
        updateHLAObject(queue);
        log("join queue [" + idQueue + "], queue length: " + queue.length, fedamb.federateTime);

        if (!queue.openedNewCheckout && queue.length > 5) {
            queue.openedNewCheckout = true;
            sendQueueOverloadInteraction(idQueue);
            log("queue [" + idQueue + "] is overloaded, queue length: " + queue.length, fedamb.federateTime);
        }
    }

    private void finishCheckout(int idCheckout) throws RTIexception {
        for (Map.Entry<Integer, Queue> entry : queues.entrySet()) {
            Queue queue = entry.getValue();
            if (queue.idCheckout == idCheckout) {
                queue.isCheckoutBusy = false;
            }
        }
    }

    private void sendToCheckout(Queue queue, int idClient) throws RTIexception {
        queue.removeFromQueue(idClient);
        sendSendToCheckoutInteraction(idClient, queue.idCheckout);
        updateHLAObject(queue);
        log("queue [" + queue.idQueue + "] sent client [" + idClient + "] to checkout [" + queue.idCheckout + "]", fedamb.federateTime);
        queue.isCheckoutBusy = true;
        queue.openedNewCheckout = false;
    }

    private int registerQueueObject(int idCheckout) throws RTIexception {
        int classHandle = rtiamb.getObjectClassHandle("ObjectRoot.Queue");
        int idQueue = rtiamb.registerObjectInstance(classHandle);
        queues.put(idQueue, new Queue(idQueue, idCheckout));
        return idQueue;
    }

    private void updateHLAObject(Queue queue) throws RTIexception {
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
        byte[] byteLengthHandle = EncodingHelpers.encodeInt(queue.length);
        attributes.add(lengthHandle, byteLengthHandle);

        rtiamb.updateAttributeValues(queue.idQueue, attributes, "actualize checkout".getBytes(), getLogicalTime());
        queues.put(queue.idQueue, queue);
    }

    private void removeHLAObject(int idQueue) throws RTIexception {
        rtiamb.deleteObjectInstance(idQueue, "remove client".getBytes(), getLogicalTime());
    }

    private void sendSendToCheckoutInteraction(int idClient, int idCheckout) throws RTIexception {
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();
        Random random = new Random();

        int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.SendToCheckout");

        byte[] byteIdClient = EncodingHelpers.encodeInt(idClient);
        int idClientHandle = rtiamb.getParameterHandle("idClient", interactionHandle);
        parameters.add(idClientHandle, byteIdClient);

        byte[] byteIdCheckout = EncodingHelpers.encodeInt(idCheckout);
        int idCheckoutHandle = rtiamb.getParameterHandle("idCheckout", interactionHandle);
        parameters.add(idCheckoutHandle, byteIdCheckout);

        rtiamb.sendInteraction(interactionHandle, parameters, "tag".getBytes(), getLogicalTime());
    }

    private void sendQueueOverloadInteraction(int idQueue) throws RTIexception {
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();
        Random random = new Random();

        int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.QueueOverload");

        byte[] byteIdQueue = EncodingHelpers.encodeInt(idQueue);
        int idQueueHandle = rtiamb.getParameterHandle("idQueue", interactionHandle);
        parameters.add(idQueueHandle, byteIdQueue);

        rtiamb.sendInteraction(interactionHandle, parameters, "tag".getBytes(), getLogicalTime());
    }

    private void sendQueuesEmptyInteraction() throws RTIexception {
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

        int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.QueuesEmpty");

        rtiamb.sendInteraction(interactionHandle, parameters, "tag".getBytes(), getLogicalTime());
    }

    @Override
    protected void publish() throws RTIexception {
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

        int queuesEmpty = rtiamb.getInteractionClassHandle("InteractionRoot.QueuesEmpty");
        rtiamb.publishInteractionClass(queuesEmpty);
    }

    @Override
    protected void subscribe() throws RTIexception {
        int clientHandle = rtiamb.getObjectClassHandle("ObjectRoot.Client");
        fedamb.clientHandle = clientHandle;
        int idClientHandleClient = rtiamb.getAttributeHandle("idClient", clientHandle);
        int priorityHandle = rtiamb.getAttributeHandle("priority", clientHandle);
        int hasCashHandle = rtiamb.getAttributeHandle("hasCash", clientHandle);
        AttributeHandleSet attributes =
                RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
        attributes.add(idClientHandleClient);
        attributes.add(priorityHandle);
        attributes.add(hasCashHandle);
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

        int noClientsHandle = rtiamb.getInteractionClassHandle("InteractionRoot.NoClients");
        fedamb.noClientsHandle = noClientsHandle;
        rtiamb.subscribeInteractionClass(noClientsHandle);

        int finishCheckoutServiceHandle = rtiamb.getInteractionClassHandle("InteractionRoot.FinishCheckoutService");
        fedamb.finishCheckoutServiceHandle = finishCheckoutServiceHandle;
        rtiamb.subscribeInteractionClass(finishCheckoutServiceHandle);

        int finishHandle = rtiamb.getInteractionClassHandle("InteractionRoot.Finish");
        fedamb.finishHandle = finishHandle;
        rtiamb.subscribeInteractionClass(finishHandle);
    }

    public static void main(String[] args) {
        try {
            new QueueFederate().runFederate();
        } catch (RTIexception rtIexception) {
            rtIexception.printStackTrace();
        }
    }

}
