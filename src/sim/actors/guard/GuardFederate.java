package sim.actors.guard;


import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.RtiFactoryFactory;
import sim.objects.Checkout;
import sim.objects.Client;
import sim.utils.AFederate;

import java.util.HashMap;
import java.util.Map;

public class GuardFederate extends AFederate<GuardAmbassador> {

    private HashMap<Integer, Checkout> checkouts = new HashMap<>();

    private boolean doorOpen = true;
    private boolean finish = false;

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
                        case FINISH:
                            finish = true;
                            break;
                        case NEW_CLIENT:
                            checkFaceMask(externalEvent.getParameter("id_client"));
                            if (doorOpen && fedamb.clients.size() >= 50) {
                                closeDoor();
                            }
                            break;
                        case CLIENT_EXITED:
                            if (!doorOpen && fedamb.clients.size() < 50) {
                                openDoor();
                            }
                            break;
                    }
                }
                fedamb.externalEvents.clear();
            }

            if (finish) {
                break;
            }

            rtiamb.tick();
        }

        rtiamb.resignFederationExecution(ResignAction.NO_ACTION);
        log("resigned from Federation");
    }

    @Override
    protected GuardAmbassador getNewFedAmbInstance() {
        return new GuardAmbassador();
    }

    private void checkFaceMask(int idClient) throws RTIexception {
        if (fedamb.clients.get(idClient).hasFaceMask == 1) {
            log("client has mask, letting in", fedamb.federateTime);
            sendLetClientInInteraction(idClient);
        } else {
            log("client doesn't have mask, rejecting", fedamb.federateTime);
            sendRejectClientInteraction(idClient);
        }
    }

    private void closeDoor() throws RTIexception {
        log("closing door", fedamb.federateTime);
        doorOpen = false;
        sendCloseDoorInteraction();
    }

    private void openDoor() throws RTIexception {
        log("opening door", fedamb.federateTime);
        doorOpen = true;
        sendOpenDoorInteraction();
    }

    private void sendLetClientInInteraction(int idClient) throws RTIexception {
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

        int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.LetClientIn");

        byte[] byteIdClient = EncodingHelpers.encodeInt(idClient);
        int idClientHandle = rtiamb.getParameterHandle("idClient", interactionHandle);
        parameters.add(idClientHandle, byteIdClient);

        rtiamb.sendInteraction(interactionHandle, parameters, "tag".getBytes(), getLogicalTime());
    }

    private void sendRejectClientInteraction(int idClient) throws RTIexception {
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();

        int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.RejectClient");

        byte[] byteIdClient = EncodingHelpers.encodeInt(idClient);
        int idClientHandle = rtiamb.getParameterHandle("idClient", interactionHandle);
        parameters.add(idClientHandle, byteIdClient);

        rtiamb.sendInteraction(interactionHandle, parameters, "tag".getBytes(), getLogicalTime());
    }

    private void sendOpenDoorInteraction() throws RTIexception {
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();
        int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.OpenDoor");
        rtiamb.sendInteraction(interactionHandle, parameters, "tag".getBytes(), getLogicalTime());
    }

    private void sendCloseDoorInteraction() throws RTIexception {
        SuppliedParameters parameters =
                RtiFactoryFactory.getRtiFactory().createSuppliedParameters();
        int interactionHandle = rtiamb.getInteractionClassHandle("InteractionRoot.CloseDoor");
        rtiamb.sendInteraction(interactionHandle, parameters, "tag".getBytes(), getLogicalTime());
    }

    @Override
    protected void publish() throws RTIexception {
        int letClientInHandle = rtiamb.getInteractionClassHandle("InteractionRoot.LetClientIn");
        rtiamb.publishInteractionClass(letClientInHandle);

        int rejectClientHandle = rtiamb.getInteractionClassHandle("InteractionRoot.RejectClient");
        rtiamb.publishInteractionClass(rejectClientHandle);

        int closeDoorHandle = rtiamb.getInteractionClassHandle("InteractionRoot.CloseDoor");
        rtiamb.publishInteractionClass(closeDoorHandle);

        int openDoorHandle = rtiamb.getInteractionClassHandle("InteractionRoot.OpenDoor");
        rtiamb.publishInteractionClass(openDoorHandle);
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

        int finishHandle = rtiamb.getInteractionClassHandle("InteractionRoot.Finish");
        fedamb.finishHandle = finishHandle;
        rtiamb.subscribeInteractionClass(finishHandle);
    }

    public static void main(String[] args) {
        try {
            new GuardFederate().runFederate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
