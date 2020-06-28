package sim.utils;

import hla.rti.*;
import hla.rti.jlc.EncodingHelpers;
import hla.rti.jlc.NullFederateAmbassador;
import org.portico.impl.hla13.types.DoubleTime;
import sim.Example13Federate;
import sim.HandlersHelper;

import java.util.ArrayList;
import java.util.HashMap;


public abstract class AAmbassador extends NullFederateAmbassador {

	public boolean running = true;

    public double federateTime        = 0.0;
    public double federateLookahead   = 1.0;

    public boolean isRegulating       = false;
    public boolean isConstrained      = false;
    public boolean isAdvancing        = false;

    public boolean isAnnounced        = false;
    public boolean isReadyToRun       = false;

    public void timeRegulationEnabled( LogicalTime theFederateTime )
    {
        this.federateTime = convertTime( theFederateTime );
        this.isRegulating = true;
    }

    public void timeConstrainedEnabled( LogicalTime theFederateTime )
    {
        this.federateTime = convertTime( theFederateTime );
        this.isConstrained = true;
    }

    public void timeAdvanceGrant( LogicalTime theTime )
    {
        this.federateTime = convertTime( theTime );
        this.isAdvancing = false;
    }

    public void synchronizationPointRegistrationFailed( String label )
    {
        log( "Failed to register sync point: " + label );
    }

    public void synchronizationPointRegistrationSucceeded( String label )
    {
        log( "Successfully registered sync point: " + label );
    }

    public void announceSynchronizationPoint( String label, byte[] tag )
    {
        log( "Synchronization point announced: " + label );
        if( label.equals(Example13Federate.READY_TO_RUN) )
            this.isAnnounced = true;
    }

    public void federationSynchronized( String label )
    {
        log( "Federation Synchronized: " + label );
        if( label.equals(Example13Federate.READY_TO_RUN) )
            this.isReadyToRun = true;
    }

    protected double convertTime( LogicalTime logicalTime )
    {
        // PORTICO SPECIFIC!!
        return ((DoubleTime)logicalTime).getTime();
    }

    protected void log(String message) {
        System.out.println(this.getClass().getSimpleName() + ": " + message);
    }

    protected void log(String message, double time) {
        System.out.println(this.getClass().getSimpleName() + ": " + message + ", time: " + time);
    }

	public void receiveInteraction(int interactionClass, ReceivedInteraction theInteraction, byte[] tag) {
		receiveInteraction(interactionClass, theInteraction, tag, null, null);
	}

	public abstract void receiveInteraction(int interactionClass,
			ReceivedInteraction theInteraction, byte[] tag,
			LogicalTime theTime, EventRetractionHandle eventRetractionHandle);

	public void reflectAttributeValues(int theObject, ReflectedAttributes theAttributes, byte[] tag) {
		reflectAttributeValues(theObject, theAttributes, tag, null, null);
	}

	public abstract void reflectAttributeValues(int theObject,
			ReflectedAttributes theAttributes, byte[] tag, LogicalTime theTime,
			EventRetractionHandle retractionHandle);

    public abstract void discoverObjectInstance(int theObject,
                                                int theObjectClass,
                                                String objectName) throws CouldNotDiscover, ObjectClassNotKnown, FederateInternalError;

    public void removeObjectInstance( int theObject, byte[] userSuppliedTag ) {
        removeObjectInstance(theObject, userSuppliedTag, null, null);
    }

    public abstract void removeObjectInstance( int theObject,
                                      byte[] userSuppliedTag,
                                      LogicalTime theTime,
                                      EventRetractionHandle retractionHandle );
}
