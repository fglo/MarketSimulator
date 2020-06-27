package sim.statistics;

import hla.rti.*;
import hla.rti.jlc.RtiFactoryFactory;
import sim.HandlersHelper;
import org.portico.impl.hla13.types.DoubleTime;
import org.portico.impl.hla13.types.DoubleTimeInterval;
import sim.objects.Checkout;
import sim.objects.Client;
import sim.objects.Queue;
import sim.statistics.ExternalEvent;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

public class StatisticsFederate {

    public static final String READY_TO_RUN = "ReadyToRun";

	private RTIambassador rtiamb;
	private StatisticsAmbassador fedamb;
	private int shopOpenCounter = 0;
	private int shopCloseCounter = 0;
	private int checkoutOpenCounter = 0;
	private int joinQueueCounter = 0;
	private int sendToCheckoutCounter = 0;
	private int startCheckoutServiceCounter = 0;
	private int endCheckoutServiceCounter = 0;
	private int queueOverloadCounter = 0;

	int sumOfShoppingTime = 0;

	protected HashMap<Integer, Queue> queues = new HashMap<>();
	protected HashMap<Integer, Client> clients = new HashMap<>();
	protected HashMap<Integer, Checkout> checkouts = new HashMap<>();


	public void runFederate() throws Exception{
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

		fedamb = new StatisticsAmbassador();
		rtiamb.joinFederationExecution( "StatisticsFederate", "MarketFederation", fedamb );
		log( "Joined Federation as " + "StatisticsFederate" );

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
		log( "Published and Subscribed" );


		while(fedamb.running) {
            advanceTime(1.0);

			if(fedamb.externalEvents.size() > 0) {
				fedamb.externalEvents.sort(new ExternalEvent.ExternalEventComparator());
				for(ExternalEvent externalEvent : fedamb.externalEvents) {
					fedamb.federateTime = externalEvent.getTime();
					switch (externalEvent.getEventType()) {
						case QUEUE_OVERLOAD:
							this.queueOverload();
							break;
						case SHOP_OPEN:
							this.shopOpen();
							break;
						case SHOP_CLOSE:
							this.shopClose();
							break;
						case CHECKOUT_OPEN:
							this.checkoutOpen();
							break;
						case JOIN_QUEUE:
							this.joinQueue();
							break;
						case SEND_TO_CHECKOUT:
							this.sendToCheckout();
							break;
						case START_CHECKOUT_SERVICE:
							this.startCheckoutService();
							break;
						case END_CHECKOUT_SERVICE:
							this.endCheckoutService();
							break;
					}
				}
				fedamb.externalEvents.clear();
			}

			rtiamb.tick();
		}


		rtiamb.resignFederationExecution( ResignAction.NO_ACTION );
		log( "Resigned from Federation" );
		
		try
		{
			rtiamb.destroyFederationExecution( "MarketFederation" );
			log( "Destroyed Federation" );
		}
		catch( FederationExecutionDoesNotExist dne )
		{
			log( "No need to destroy federation, it doesn't exist" );
		}
		catch( FederatesCurrentlyJoined fcj )
		{
			log( "Didn't destroy federation, federates still joined" );
		}
	}

	private void shopOpen() {
		shopOpenCounter++;
		log("shop open: " + shopOpenCounter);
	}

	private void shopClose() {
		shopCloseCounter++;
		log("shop close: " + shopCloseCounter);
	}

	private void checkoutOpen () {
		checkoutOpenCounter++;
		log("checkout open: " + checkoutOpenCounter);
	}

	private void joinQueue() {
		joinQueueCounter++;
		log("join queue: " + joinQueueCounter);
	}

	private void sendToCheckout() {
		sendToCheckoutCounter++;
		log("send to checkout: " + sendToCheckoutCounter);
	}

	private void startCheckoutService() {
		startCheckoutServiceCounter++;
		log("start checkout service: " + startCheckoutServiceCounter);
	}

	private void endCheckoutService() {
		endCheckoutServiceCounter++;
		log("end checkout service: " + endCheckoutServiceCounter);
	}

	private void queueOverload() {
		queueOverloadCounter++;
		log("queue overload: " + queueOverloadCounter);
	}

	public void meanTimeOfShopping()
	{
		for(Map.Entry<Integer, Client> entry : clients.entrySet())
		{
			Client client = entry.getValue();
			sumOfShoppingTime = sumOfShoppingTime + client.shoppingTime;
		}
		double meanTimeOfShopping = 0;
		meanTimeOfShopping = sumOfShoppingTime/startCheckoutServiceCounter;
		log("Mean time of shopping is: " + meanTimeOfShopping);
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

    private void advanceTime( double timestep ) throws RTIexception
    {
        // request the advance
        fedamb.isAdvancing = true;
        LogicalTime newTime = convertTime( fedamb.federateTime + timestep );
        rtiamb.timeAdvanceRequest( newTime );
        while( fedamb.isAdvancing )
        {
            rtiamb.tick();
        }
    }
	
	private void publishAndSubscribe() throws RTIexception {

		// Publkowanie obiektu SimObject z atrybutem state

		int simObjectClassHandle = rtiamb
				.getObjectClassHandle("ObjectRoot.Storage");
		int stateHandle = rtiamb.getAttributeHandle("stock", simObjectClassHandle);

		AttributeHandleSet attributes = RtiFactoryFactory.getRtiFactory()
				.createAttributeHandleSet();
		attributes.add(stateHandle);

		rtiamb.subscribeObjectClassAttributes(simObjectClassHandle, attributes);

		// Zapisanie do inteakcji ko�cz�cej
		int interactionHandle = rtiamb
				.getInteractionClassHandle("InteractionRoot.Finish");
		// Dodanie mapowania interakcji na uchwyt
		HandlersHelper.addInteractionClassHandler("InteractionRoot.Finish",
                interactionHandle);

		rtiamb.subscribeInteractionClass(interactionHandle);

		int shopOpen = rtiamb.getInteractionClassHandle("InteractionRoot.ShopOpen");
		fedamb.shopOpenHandle = shopOpen;
		rtiamb.subscribeInteractionClass(shopOpen);

		int shopClose = rtiamb.getInteractionClassHandle("InteractionRoot.ShopClose");
		fedamb.shopCloseHandle = shopClose;
		rtiamb.subscribeInteractionClass(shopClose);

		int checkoutOpen = rtiamb.getInteractionClassHandle("InteractionRoot.CheckoutOpen");
		fedamb.checkoutOpenHandle = checkoutOpen;
		rtiamb.subscribeInteractionClass(checkoutOpen);

		int joinQueueHandle = rtiamb.getInteractionClassHandle("InteractionRoot.JoinQueue");
		fedamb.joinQueueHandle = joinQueueHandle;
		rtiamb.subscribeInteractionClass(joinQueueHandle);

		int sendToCheckoutHandle = rtiamb.getInteractionClassHandle("InteractionRoot.SendToCheckout");
		fedamb.sendToCheckoutHandle = sendToCheckoutHandle;
		rtiamb.subscribeInteractionClass(sendToCheckoutHandle);

		int queueOverloadHandle = rtiamb.getInteractionClassHandle("InteractionRoot.QueueOverloadHandle");
		fedamb.queueOverloadHandle = queueOverloadHandle;
		rtiamb.subscribeInteractionClass(queueOverloadHandle);

		int startCheckoutServiceHandle = rtiamb.getInteractionClassHandle("InteractionRoot.StartCheckoutServiceHandle");
		fedamb.startCheckoutServiceHandle = startCheckoutServiceHandle;
		rtiamb.subscribeInteractionClass(startCheckoutServiceHandle);

		int endCheckoutServiceHandle = rtiamb.getInteractionClassHandle("InteractionRoot.EndCheckoutServiceHandle");
		fedamb.endCheckoutServiceHandle = endCheckoutServiceHandle;
		rtiamb.subscribeInteractionClass(endCheckoutServiceHandle);

		int queueHandle = rtiamb.getObjectClassHandle("ObjectRoot.Queue");
		fedamb.queueHandle = queueHandle;
		int idQueueHandle = rtiamb.getAttributeHandle("idQueue", queueHandle);
		int idqueueCheckoutHandle = rtiamb.getAttributeHandle("idCheckout", queueHandle);
		int lenghtHandle = rtiamb.getAttributeHandle("length", queueHandle);
		AttributeHandleSet queueAttributes =
				RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
		queueAttributes.add(idQueueHandle);
		queueAttributes.add(idqueueCheckoutHandle);
		queueAttributes.add(lenghtHandle);
		rtiamb.subscribeObjectClassAttributes(queueHandle, queueAttributes);

		int clientHandle = rtiamb.getObjectClassHandle("ObjectRoot.Client");
		fedamb.clientHandle = clientHandle;
		int idClientHandleClient = rtiamb.getAttributeHandle("idClient", clientHandle);
		int priopityHandle = rtiamb.getAttributeHandle("priority", clientHandle);
		int shooppingTimeHandle = rtiamb.getAttributeHandle("shoppingTime",clientHandle)
		AttributeHandleSet clientAttributes =
				RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
		clientAttributes.add(idClientHandleClient);
		clientAttributes.add(priopityHandle);
		clientAttributes.add(shooppingTimeHandle);
		rtiamb.subscribeObjectClassAttributes(clientHandle, clientAttributes);

		int checkoutHandle = rtiamb.getObjectClassHandle("ObjectRoot.Checkout");
		fedamb.checkoutHandle = checkoutHandle;
		int idCheckoutHandle = rtiamb.getAttributeHandle("idCheckout", checkoutHandle);
		int idClientHandleCheckout = rtiamb.getAttributeHandle("idClient", checkoutHandle);
		AttributeHandleSet checkoutAttributes =
				RtiFactoryFactory.getRtiFactory().createAttributeHandleSet();
		checkoutAttributes.add(idCheckoutHandle);
		checkoutAttributes.add(idClientHandleCheckout);
		rtiamb.subscribeObjectClassAttributes(checkoutHandle, checkoutAttributes);

	}

    private void enableTimePolicy() throws RTIexception
    {
        // NOTE: Unfortunately, the LogicalTime/LogicalTimeInterval create code is
        //       Portico specific. You will have to alter this if you move to a
        //       different RTI implementation. As such, we've isolated it into a
        //       method so that any change only needs to happen in a couple of spots
        LogicalTime currentTime = convertTime( fedamb.federateTime );
        LogicalTimeInterval lookahead = convertInterval( fedamb.federateLookahead );

        ////////////////////////////
        // enable time regulation //
        ////////////////////////////
        this.rtiamb.enableTimeRegulation( currentTime, lookahead );

        // tick until we get the callback
        while( fedamb.isRegulating == false )
        {
            rtiamb.tick();
        }

        /////////////////////////////
        // enable time constrained //
        /////////////////////////////
        this.rtiamb.enableTimeConstrained();

        // tick until we get the callback
        while( fedamb.isConstrained == false )
        {
            rtiamb.tick();
        }
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
		System.out.println( "StatisticsFederate  : " + message );
	}

	public static void main(String[] args) {
		StatisticsFederate sf = new StatisticsFederate();
		
		try {
			sf.runFederate();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
