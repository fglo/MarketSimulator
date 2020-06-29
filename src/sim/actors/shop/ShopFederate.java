package sim.actors.shop;

import hla.rti.*;
import hla.rti.jlc.RtiFactoryFactory;
import org.portico.impl.hla13.types.DoubleTime;
import org.portico.impl.hla13.types.DoubleTimeInterval;
import sim.utils.AFederate;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Random;

/**
 * Created by Michal on 2016-04-27.
 */
public class ShopFederate extends AFederate<ShopAmbassador> {

    private int checkoutCounter = 0;

    private boolean shopOpen = false;
    private boolean queuesEmpty = false;
    private boolean checkoutsClosed = false;

    @Override
    public void runFederate() throws RTIexception {
        super.runFederate();

        while (fedamb.running) {
            advanceTime( timeStep );

            if(fedamb.externalEvents.size() > 0) {
                fedamb.externalEvents.sort(new ExternalEvent.ExternalEventComparator());
                for(ExternalEvent externalEvent : fedamb.externalEvents) {
                    fedamb.federateTime = externalEvent.getTime();
                    switch (externalEvent.getEventType()) {
                        case QUEUE_OVERLOAD:
                            this.queueOverload();
                            break;
                        case CHECKOUTS_CLOSED:
                            checkoutsClosed = true;
                            break;
                        case QUEUES_EMPTY:
                            queuesEmpty = true;
                            break;
                    }
                }
                fedamb.externalEvents.clear();
            }

            if(!shopOpen && fedamb.federateTime < 2048)
            {
                openShop();
                for (int i = 0; i < 9; i++) {
                    openCheckout();
                }
            }

            if(shopOpen && fedamb.federateTime >= 2048)
            {
                closeShop();
            }

            if(checkoutsClosed && queuesEmpty) {
                sendFinishInteraction();
                break;
            }

            rtiamb.tick();
        }

        rtiamb.resignFederationExecution( ResignAction.NO_ACTION );
        log( "Resigned from Federation" );

//        try
//        {
//            rtiamb.destroyFederationExecution( "MarketFederation" );
//            log( "Destroyed Federation" );
//        }
//        catch( FederationExecutionDoesNotExist dne )
//        {
//            log( "No need to destroy federation, it doesn't exist" );
//        }
//        catch( FederatesCurrentlyJoined fcj )
//        {
//            log( "Didn't destroy federation, federates still joined" );
//        }
    }

    @Override
    protected ShopAmbassador getNewFedAmbInstance() {
        return new ShopAmbassador();
    }

    private void openShop() throws RTIexception {
        shopOpen = true;
        sendOpenShopInteraction();
        log("opening the shop", fedamb.federateTime);
    }

    private void closeShop() throws RTIexception {
        shopOpen = false;
        sendCloseShopInteraction();
        log("closing the shop", fedamb.federateTime);
    }

    private void openCheckout() throws RTIexception {
        checkoutCounter++;
        sendOpenCheckoutInteraction();
        log("checkout opens, number of checkouts: " + checkoutCounter, fedamb.federateTime);
    }

    private void queueOverload() throws RTIexception {
        openCheckout();
    }

    private void sendOpenShopInteraction() throws RTIexception {
        log("shop opens", fedamb.federateTime);

        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

        int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.ShopOpen");
        rtiamb.sendInteraction( interactionHandle, parameters, "tag".getBytes(), getLogicalTime());
    }

    private void sendCloseShopInteraction() throws RTIexception {
        log("shop closes", fedamb.federateTime);

        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

        int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.ShopClose");
        rtiamb.sendInteraction( interactionHandle, parameters, "tag".getBytes(), getLogicalTime());
    }

    private void sendOpenCheckoutInteraction() throws RTIexception {
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

        int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.CheckoutOpen");
        rtiamb.sendInteraction( interactionHandle, parameters, "tag".getBytes(), getLogicalTime());
    }

    private void sendFinishInteraction() throws RTIexception {
        log("finish", fedamb.federateTime);

        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

        int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.Finish");
        rtiamb.sendInteraction( interactionHandle, parameters, "tag".getBytes(), getLogicalTime());
    }

    @Override
    protected void publish() throws RTIexception {
        int shopOpenHandle = rtiamb.getInteractionClassHandle("InteractionRoot.ShopOpen");
        rtiamb.publishInteractionClass(shopOpenHandle);

        int shopCloseHandle = rtiamb.getInteractionClassHandle("InteractionRoot.ShopClose");
        rtiamb.publishInteractionClass(shopCloseHandle);

        int checkoutOpenHandle = rtiamb.getInteractionClassHandle("InteractionRoot.CheckoutOpen");
        rtiamb.publishInteractionClass(checkoutOpenHandle);

        int finishHandle = rtiamb.getInteractionClassHandle("InteractionRoot.Finish");
        rtiamb.publishInteractionClass(finishHandle);
    }

    @Override
    protected void subscribe() throws RTIexception {
        int queueOverloadHandle = rtiamb.getInteractionClassHandle("InteractionRoot.QueueOverload");
        fedamb.queueOverloadHandle = queueOverloadHandle;
        rtiamb.subscribeInteractionClass(queueOverloadHandle);

        int checkoutsClosedHandle = rtiamb.getInteractionClassHandle("InteractionRoot.CheckoutsClosed");
        fedamb.checkoutsClosedHandle = checkoutsClosedHandle;
        rtiamb.subscribeInteractionClass(checkoutsClosedHandle);

        int queuesEmptyHandle = rtiamb.getInteractionClassHandle("InteractionRoot.QueuesEmpty");
        fedamb.queuesEmptyHandle = queuesEmptyHandle;
        rtiamb.subscribeInteractionClass(queuesEmptyHandle);
    }

    public static void main(String[] args) {
        try {
            new ShopFederate().runFederate();
        } catch (RTIexception rtIexception) {
            rtIexception.printStackTrace();
        }
    }
}
