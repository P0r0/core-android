/* **********************************************
 * Create by : Alberto "Quequero" Pelliccione
 * Company   : HT srl
 * Project   : AndroidService
 * Created   : 30-mar-2011
 **********************************************/

package com.android.service.event;

import android.util.Log;

import com.android.service.Status;
import com.android.service.ThreadBase;
import com.android.service.action.Action;
import com.android.service.auto.Cfg;

// TODO: Auto-generated Javadoc
/**
 * The Class EventBase.
 */
public abstract class EventBase extends ThreadBase implements Runnable {
 
	/** The Constant TAG. */
	private static final String TAG = "EventBase";

	// Gli eredi devono implementare i seguenti metodi astratti

	/**
	 * Parses the.
	 * 
	 * @param event
	 *            the event
	 */
	public abstract boolean parse(EventConf event);

	/** The event. */
	protected EventConf event;


	/**
	 * Sets the event.
	 * 
	 * @param event
	 *            the new event
	 */
	public void setEvent(final EventConf event) {
		this.event = event;
	}

	/**
	 * Trigger.
	 */
	protected final void trigger() {
		trigger(event.getAction());
	}
	
	protected final void trigger(int actionId) {
		if (actionId != Action.ACTION_NULL) {
			if(Cfg.DEBUG) Log.d("QZ", TAG + " event: " + this + " triggering: " + actionId);
			Status.self().triggerAction(actionId);
		}
	}
	

}