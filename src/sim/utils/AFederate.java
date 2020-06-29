package sim.utils;

import hla.rti.*;
import hla.rti.jlc.RtiFactoryFactory;
import org.portico.impl.hla13.types.DoubleTime;
import org.portico.impl.hla13.types.DoubleTimeInterval;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.Random;

public abstract class AFederate<TFedAmb extends AAmbassador> {

    public static final String READY_TO_RUN = "ReadyToRun";

    protected RTIambassador rtiamb;
    protected TFedAmb fedamb;
    protected final double timeStep = 10.0;
    protected int timeStepCounter = 0;

    protected LogicalTime lastAdvancedTime;

    protected void waitForUser() {
        log(" >>>>>>>>>> Press Enter to Continue <<<<<<<<<<");
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        try {
            reader.readLine();
        } catch (Exception e) {
            log("Error while waiting for user input: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void runFederate() throws RTIexception {
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

        this.fedamb = getNewFedAmbInstance();
        rtiamb.joinFederationExecution(this.getClass().getSimpleName(), "MarketFederation", fedamb);
        log("Joined Federation as " + this.getClass().getSimpleName());

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
    }

    protected void enableTimePolicy() throws RTIexception {
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

    protected abstract TFedAmb getNewFedAmbInstance();

    protected abstract void publish() throws RTIexception;

    protected abstract void subscribe() throws RTIexception;

    protected void advanceTime(double timestep) throws RTIexception {
        log("requesting time advance for: " + timestep + ", time step: " + ++timeStepCounter, fedamb.federateTime);
        fedamb.isAdvancing = true;
        lastAdvancedTime = convertTime(fedamb.federateTime + timestep);
        rtiamb.timeAdvanceRequest(lastAdvancedTime);
        while (fedamb.isAdvancing) {
            rtiamb.tick();
        }
    }

    protected LogicalTime getLogicalTime() throws RTIexception {
        LogicalTime time = convertTime(fedamb.federateTime + timeStep);
        if(time.isLessThan(lastAdvancedTime)) {
            return lastAdvancedTime;
        }
        return time;
    }

    protected double randomTime() {
        Random r = new Random();
        return 1 + (9 * r.nextDouble());
    }

    protected LogicalTime convertTime(double time) {
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

    protected void log(String message) {
        System.out.println(this.getClass().getSimpleName() + ": " + message);
    }

    protected void log(String message, double time) {
        System.out.println(this.getClass().getSimpleName() + ": " + message + ", time: " + time);
    }
}
