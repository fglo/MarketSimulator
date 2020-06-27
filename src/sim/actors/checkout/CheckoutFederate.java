package sim.actors.checkout;


import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.RtiFactoryFactory;
import org.portico.impl.hla13.types.DoubleTime;
import org.portico.impl.hla13.types.DoubleTimeInterval;
import sim.objects.Checkout;
import sim.objects.Client;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class CheckoutFederate {

    public static final String READY_TO_RUN = "ReadyToRun";

    private RTIambassador rtiamb;
    private CheckoutAmbassador fedamb;
    private final double timeStep = 10.0;
    private HashMap<Integer, Checkout> checkouts = new HashMap<>();

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
            double timeToAdvance = fedamb.federateTime + fedamb.federateLookahead; //fedamb.federateTime + timeStep;

            if (fedamb.externalEvents.size() > 0) {
                fedamb.externalEvents.sort(new ExternalEvent.ExternalEventComparator());
                for (ExternalEvent externalEvent : fedamb.externalEvents) {
                    fedamb.federateTime = externalEvent.getTime();
                    switch (externalEvent.getEventType()) {
                        case CHECKOUT_OPEN:
                            this.openCheckout(timeToAdvance);
                            break;
                        case CHECKOUT_CLOSE:
                            this.closeCheckout(timeToAdvance, externalEvent.getParameter("id_checkout"));
                            break;
                        case SHOP_CLOSE:
                            this.closeShop(timeToAdvance);
                            break;
                        case SEND_TO_CHECKOUT:
                            this.startCheckoutService(timeToAdvance,
                                    externalEvent.getParameter("id_checkout"),
                                    externalEvent.getParameter("id_client"));
                            break;
                    }
                }
                fedamb.externalEvents.clear();
            }

            for (Map.Entry<Integer, Checkout> entry : checkouts.entrySet()) {
                Checkout checkout = entry.getValue();
                if(checkout.idClient != -1 && --checkout.serviceTime <= 0) {
                    endCheckoutService(timeToAdvance, checkout);
                }
            }

            advanceTime( timeToAdvance );
            //log( "Time Advanced to " + fedamb.federateTime );

            rtiamb.tick();
        }
    }

    private void openCheckout(double time) throws RTIexception {
        int idCheckout = registerCheckoutObject();
        updateHLAObject(time, checkouts.get(idCheckout));
        sendOpenQueueInteraction(time, idCheckout);
        log("opened checkout", time);
    }

    private void closeCheckout(double time, int idCheckout) throws RTIexception {
        removeHLAObject(time, idCheckout);
        log("closed checkout", time);
    }

    private void closeShop(double time) {
    }

    private void startCheckoutService(double time, int idCheckout, int idClient) throws RTIexception {
        Client client = fedamb.clients.get(idClient);
        if(client == null) {
            log("checkout [" + idClient + "] was not found", time);
            return;
        }
        if(client.hasCach == 0) {
            log("client [" + idClient + "] does not have cash. and was rejested", time);
            return;
        }

        Checkout checkout = checkouts.get(idCheckout);
        if(checkout == null) {
            log("checkout with id: [" + idCheckout + "] was not found", time);
            return;
        }
        checkout.idClient = idClient;
        checkout.serviceTime = ThreadLocalRandom.current().nextInt(1, 3);
        updateHLAObject(time, checkout);
        log("started checkout service", time);
    }

    private void endCheckoutService(double time, Checkout checkout) throws RTIexception {
        sendEndCheckoutServiceInteraction(time, checkout.idClient);
        checkout.idClient = -1;
        updateHLAObject(time, checkout);
        log("ended checkout service", time);
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
        int classHandle = rtiamb.getObjectClassHandle("ObjectRoot.Checkout");
        int idCheckout = rtiamb.registerObjectInstance(classHandle);
        checkouts.put(idCheckout, new Checkout(idCheckout));
        return idCheckout;
    }

    private void updateHLAObject(double time, Checkout checkout) throws RTIexception {
        SuppliedAttributes attributes =
                RtiFactoryFactory.getRtiFactory().createSuppliedAttributes();

        int objectHandle = rtiamb.getObjectClass(checkout.idCheckout);

        int idCheckoutHandle = rtiamb.getAttributeHandle("idCheckout", objectHandle);
        byte[] byteIdCheckout = EncodingHelpers.encodeInt(checkout.idCheckout);
        attributes.add(idCheckoutHandle, byteIdCheckout);

        int idClientHandle = rtiamb.getAttributeHandle("idClient", objectHandle);
        byte[] byteIdClient = EncodingHelpers.encodeInt(checkout.idClient);
        attributes.add(idClientHandle, byteIdClient);

        LogicalTime logicalTime = convertTime(time);
        rtiamb.updateAttributeValues(checkout.idCheckout, attributes, "actualize checkout".getBytes(), logicalTime);

        checkouts.put(checkout.idCheckout, checkout);
    }

    private void removeHLAObject(double time, int idCheckout) throws RTIexception {
        LogicalTime logicalTime = convertTime(time);
        rtiamb.deleteObjectInstance(idCheckout, "remove checkout".getBytes(), logicalTime);
        checkouts.remove((Integer)idCheckout);
    }

    private void sendEndCheckoutServiceInteraction(double timeStep, int idClient) throws RTIexception {
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

        byte[] byteIdClient = EncodingHelpers.encodeInt(idClient);
        int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.FinishCheckoutService");
        int idClientHandle = rtiamb.getParameterHandle( "idClient", interactionHandle );

        parameters.add(idClientHandle, byteIdClient);

        LogicalTime time = convertTime( timeStep );
        rtiamb.sendInteraction( interactionHandle, parameters, "tag".getBytes(), time );
    }

    private void sendOpenQueueInteraction(double timeStep, int idCheckout) throws RTIexception {
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

        byte[] byteIdCheckout = EncodingHelpers.encodeInt(idCheckout);
        int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.QueueOpen");
        int idCheckoutHandle = rtiamb.getParameterHandle( "idCheckout", interactionHandle );

        parameters.add(idCheckoutHandle, byteIdCheckout);

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
    }

    private void subscribe() throws RTIexception {
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

        int checkoutCloseHandle = rtiamb.getInteractionClassHandle("InteractionRoot.CheckoutClose");
        fedamb.checkoutCloseHandle = checkoutCloseHandle;
        rtiamb.subscribeInteractionClass(checkoutCloseHandle);

        int shopCloseHandle = rtiamb.getInteractionClassHandle("InteractionRoot.ShopClose");
        fedamb.shopCloseHandle = shopCloseHandle;
        rtiamb.subscribeInteractionClass(shopCloseHandle);

        int sendToCheckoutHandle = rtiamb.getInteractionClassHandle("InteractionRoot.SendToCheckout");
        fedamb.sendToCheckoutHandle = sendToCheckoutHandle;
        rtiamb.subscribeInteractionClass(sendToCheckoutHandle);
    }

    private void enableTimePolicy() throws RTIexception {
        LogicalTime currentTime = convertTime(fedamb.federateTime);
        LogicalTimeInterval lookahead = convertInterval(fedamb.federateLookahead);

        this.rtiamb.enableTimeRegulation(currentTime, lookahead);

        while (fedamb.isRegulating == false) {
            rtiamb.tick();
        }

        this.rtiamb.enableTimeConstrained();

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
        System.out.println("CheckoutFederate  : " + message );
    }

    private void log(String message, double time) {
        System.out.println("CheckoutFederate  : " + message + ", time: " + time);
    }

    public static void main(String[] args) {
        try {
            new CheckoutFederate().runFederate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
