package sim.actors.checkout;


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
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class CheckoutFederate extends AFederate<CheckoutAmbassador> {

    private HashMap<Integer, Checkout> checkouts = new HashMap<>();

    private boolean shopOpen = false;
    private boolean noClients = false;
    private boolean finish = false;

    @Override
    public void runFederate() throws RTIexception {
        super.runFederate();

        fedamb = new CheckoutAmbassador();
        rtiamb.joinFederationExecution("CheckoutFederate", "MarketFederation", fedamb);
        log("Joined Federation as CheckoutFederate");

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
            advanceTime(timeStep);

            if (fedamb.externalEvents.size() > 0) {
                fedamb.externalEvents.sort(new ExternalEvent.ExternalEventComparator());
                for (ExternalEvent externalEvent : fedamb.externalEvents) {
                    fedamb.federateTime = externalEvent.getTime();
                    switch (externalEvent.getEventType()) {
                        case CHECKOUT_OPEN:
                            this.openCheckout();
                            break;
                        case SHOP_CLOSE:
                            log("shop is closing", fedamb.federateTime);
                            shopOpen = false;
                            break;
                        case SEND_TO_CHECKOUT:
                            this.startCheckoutService(
                                    externalEvent.getParameter("id_checkout"),
                                    externalEvent.getParameter("id_client"));
                            break;
                        case NO_CLIENTS:
                            noClients = true;
                            break;
                        case FINISH:
                            finish = true;
                            break;
                    }
                }
                fedamb.externalEvents.clear();
            }

            if(finish) {
                break;
            }

            if (!shopOpen && noClients) {
                for (Map.Entry<Integer, Checkout> entry : checkouts.entrySet()) {
                    removeHLAObject(entry.getValue().idCheckout);
                }
                log("number of checkouts at the end: " + checkouts.size(), fedamb.federateTime);
                checkouts.clear();
                sendCheckoutsClosedInteraction();
            }

            for (Map.Entry<Integer, Checkout> entry : checkouts.entrySet()) {
                Checkout checkout = entry.getValue();
                checkout.serviceTime--;
                if (checkout.endedWork()) {
                    endCheckoutService(checkout);
                }
                checkouts.put(checkout.idCheckout, checkout);
            }

            rtiamb.tick();
        }

        rtiamb.resignFederationExecution(ResignAction.NO_ACTION);
        log("resigned from Federation");
    }

    private void openCheckout() throws RTIexception {
        int idCheckout = registerCheckoutObject();
        updateHLAObject(checkouts.get(idCheckout));
        sendOpenQueueInteraction(idCheckout);
        log("opened checkout", fedamb.federateTime);
    }

    private void startCheckoutService(int idCheckout, int idClient) throws RTIexception {

        Checkout checkout = checkouts.get(idCheckout);
        if (checkout == null) {
            log("checkout with id: [" + idCheckout + "] was not found", fedamb.federateTime);
            return;
        }

        Client client = fedamb.clients.get(idClient);
        if (client == null) {
            log("client [" + idClient + "] was not found", fedamb.federateTime);
            return;
        }

        if (client.hasCach == 0) {
            log("client [" + idClient + "] does not have cash. and was rejested", fedamb.federateTime);
            sendEndCheckoutServiceInteraction(client.idClient, checkout.idCheckout);
            checkout.stopWork();
            updateHLAObject(checkout);
            return;
        }

        checkout.startWork(idClient);
        updateHLAObject(checkout);
        log("started checkout [" + idCheckout + "] service", fedamb.federateTime);
    }

    private void endCheckoutService(Checkout checkout) throws RTIexception {
        sendEndCheckoutServiceInteraction(checkout.idClient, checkout.idCheckout);
        checkout.stopWork();
        updateHLAObject(checkout);
        log("ended checkout [" + checkout.idCheckout + "] service", fedamb.federateTime);
    }

    private int registerCheckoutObject() throws RTIexception {
        int classHandle = rtiamb.getObjectClassHandle("ObjectRoot.Checkout");
        int idCheckout = rtiamb.registerObjectInstance(classHandle);
        checkouts.put(idCheckout, new Checkout(idCheckout));
        return idCheckout;
    }

    private void updateHLAObject(Checkout checkout) throws RTIexception {
        SuppliedAttributes attributes =
                RtiFactoryFactory.getRtiFactory().createSuppliedAttributes();

        int objectHandle = rtiamb.getObjectClass(checkout.idCheckout);

        int idCheckoutHandle = rtiamb.getAttributeHandle("idCheckout", objectHandle);
        byte[] byteIdCheckout = EncodingHelpers.encodeInt(checkout.idCheckout);
        attributes.add(idCheckoutHandle, byteIdCheckout);

        int idClientHandle = rtiamb.getAttributeHandle("idClient", objectHandle);
        byte[] byteIdClient = EncodingHelpers.encodeInt(checkout.idClient);
        attributes.add(idClientHandle, byteIdClient);

        rtiamb.updateAttributeValues(checkout.idCheckout, attributes, "actualize checkout".getBytes(), getLogicalTime());
        checkouts.put(checkout.idCheckout, checkout);
    }

    private void removeHLAObject(int idCheckout) throws RTIexception {
        rtiamb.deleteObjectInstance(idCheckout, "remove checkout".getBytes(), getLogicalTime());
    }

    private void sendEndCheckoutServiceInteraction(int idClient, int idCheckout) throws RTIexception {
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

        int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.FinishCheckoutService");

        byte[] byteIdClient = EncodingHelpers.encodeInt(idClient);
        int idClientHandle = rtiamb.getParameterHandle("idClient", interactionHandle);
        parameters.add(idClientHandle, byteIdClient);

        byte[] byteIdCheckout = EncodingHelpers.encodeInt(idCheckout);
        int idCheckoutHandle = rtiamb.getParameterHandle("idCheckout", interactionHandle);
        parameters.add(idCheckoutHandle, byteIdCheckout);

        rtiamb.sendInteraction(interactionHandle, parameters, "tag".getBytes(), getLogicalTime());
    }

    private void sendOpenQueueInteraction(int idCheckout) throws RTIexception {
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

        byte[] byteIdCheckout = EncodingHelpers.encodeInt(idCheckout);
        int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.QueueOpen");
        int idCheckoutHandle = rtiamb.getParameterHandle("idCheckout", interactionHandle);

        parameters.add(idCheckoutHandle, byteIdCheckout);

        rtiamb.sendInteraction(interactionHandle, parameters, "tag".getBytes(), getLogicalTime());
    }

    private void sendCheckoutsClosedInteraction() throws RTIexception {
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

        int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.CheckoutsClosed");

        rtiamb.sendInteraction(interactionHandle, parameters, "tag".getBytes(), getLogicalTime());
    }

    @Override
    protected void publish() throws RTIexception {
        int classHandle = rtiamb.getObjectClassHandle("ObjectRoot.Checkout");
        int idCheckoutHandle = rtiamb.getAttributeHandle("idCheckout", classHandle);
        int idClientHandle = rtiamb.getAttributeHandle("idClient", classHandle);
        AttributeHandleSet attributes =
                RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
        attributes.add(idCheckoutHandle);
        attributes.add(idClientHandle);
        rtiamb.publishObjectClass(classHandle, attributes);

        int finishCheckoutService = rtiamb.getInteractionClassHandle("InteractionRoot.FinishCheckoutService");
        rtiamb.publishInteractionClass(finishCheckoutService);

        int queueOpen = rtiamb.getInteractionClassHandle("InteractionRoot.QueueOpen");
        rtiamb.publishInteractionClass(queueOpen);

        int checkoutsClosed = rtiamb.getInteractionClassHandle("InteractionRoot.CheckoutsClosed");
        rtiamb.publishInteractionClass(checkoutsClosed);
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

        int checkoutOpenHandle = rtiamb.getInteractionClassHandle("InteractionRoot.CheckoutOpen");
        fedamb.checkoutOpenHandle = checkoutOpenHandle;
        rtiamb.subscribeInteractionClass(checkoutOpenHandle);

        int shopCloseHandle = rtiamb.getInteractionClassHandle("InteractionRoot.ShopClose");
        fedamb.shopCloseHandle = shopCloseHandle;
        rtiamb.subscribeInteractionClass(shopCloseHandle);

        int sendToCheckoutHandle = rtiamb.getInteractionClassHandle("InteractionRoot.SendToCheckout");
        fedamb.sendToCheckoutHandle = sendToCheckoutHandle;
        rtiamb.subscribeInteractionClass(sendToCheckoutHandle);

        int noClientsHandle = rtiamb.getInteractionClassHandle("InteractionRoot.NoClients");
        fedamb.noClientsHandle = noClientsHandle;
        rtiamb.subscribeInteractionClass(noClientsHandle);

        int finishHandle = rtiamb.getInteractionClassHandle("InteractionRoot.Finish");
        fedamb.finishHandle = finishHandle;
        rtiamb.subscribeInteractionClass(finishHandle);
    }

    public static void main(String[] args) {
        try {
            new CheckoutFederate().runFederate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
