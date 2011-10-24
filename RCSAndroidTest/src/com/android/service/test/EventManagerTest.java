package com.android.service.test;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.android.service.Exit;
import com.android.service.GeneralException;
import com.android.service.Status;
import com.android.service.Trigger;
import com.android.service.action.Action;
import com.android.service.action.SubAction;

import com.android.service.action.UninstallAction;
import com.android.service.auto.Cfg;
import com.android.service.conf.ConfAction;
import com.android.service.conf.ConfEvent;
import com.android.service.conf.Configuration;
import com.android.service.event.BaseEvent;

import com.android.service.manager.ManagerEvent;
import com.android.service.mock.MockSubAction;
import com.android.service.util.Check;
import com.android.service.util.Utils;

import android.test.AndroidTestCase;
import android.util.Log;

public class EventManagerTest extends AndroidTestCase {
	Status status;

	@Override
	public void setUp() {
		Status.setAppContext(getContext());
		status = Status.self();
		status.clean();
		status.unTriggerAll();
	}

	public void testStart() throws GeneralException, JSONException {
		ManagerEvent em = ManagerEvent.self();

		int max = 10;
		int action = 0;

		String jsonConf = "{\"event\":\"timer\",\"_mig\":true,\"desc\":\"position loop\",\"enabled\":true,\"ts\":\"00:00:00\",\"te\":\"23:59:59\",\"repeat\":8,\"delay\":300}";

		JSONObject conf = (JSONObject) new JSONTokener(jsonConf).nextValue();
		for (int i = 0; i < max; i++) {
			final ConfEvent e = new ConfEvent(i, "timer", conf);
			status.addEvent(e);
		}

		em.startAll();
		Utils.sleep(10);
		em.stopAll();
	}

	private void addTimerLoopEvent(int i) throws JSONException {
		addTimerLoopEvent(i,100,100);
	}

	public void testManyTriggers() throws JSONException {


		int num = 10;
		addTimerLoopEvent(num);

		ManagerEvent em = ManagerEvent.self();

		em.startAll();
		Utils.sleep(1000);
		Trigger[] triggeredFast = status.getTriggeredActions(Action.FAST_QUEUE);
		//int[] triggeredSlow = status.getTriggeredActions(Action.SLOW_QUEUE);
		status.unTriggerAll();
		assertTrue(triggeredFast.length == num);
		//assertTrue(triggeredSlow.length == 0);

		Utils.sleep(1000);
		triggeredFast = status.getTriggeredActions(Action.FAST_QUEUE);
		//triggeredSlow = status.getTriggeredActions(Action.SLOW_QUEUE);
		status.unTriggerAll();
		assertTrue(triggeredFast.length == num);
		//assertTrue(triggeredSlow.length == 0);

		em.stopAll();
	}

	private void addTimerLoopEvent(int num, int delay, int iter) throws JSONException {
		String jsonConf = "{\"event\"=>\"timer\",\"_mig\"=>true,\"desc\"=>\"timer\",\"enabled\"=>true,\"ts\"=>\"00:00:00\",\"te\"=>\"23:59:59\",\"repeat\"=>0,\"delay\"=>100}";
		JSONObject conf = (JSONObject) new JSONTokener(jsonConf).nextValue();
		for (int i = 0; i < num; i++) {
			int id = i;
			int action = i;
			ConfEvent e = new ConfEvent(i, "timer", conf);
			e.delay=delay;
			e.iter=iter;
			Status.self().addEvent(e);
		}
	}

	public void testSingleMockAction() throws JSONException {

		Action action = new Action(0, "action0");
		String jsonConf = "{\"action\"=>\"counter\"}";
		JSONObject conf = (JSONObject) new JSONTokener(jsonConf).nextValue();
				
		MockSubAction sub = new MockSubAction(new ConfAction(0,0,conf ));
		action.addSubAction(sub);
		status.addAction(action);

		int iter = 5;
		addTimerLoopEvent(1,1000,iter);

		ManagerEvent em = ManagerEvent.self();

		em.startAll();

		try {
			for (int i = 0; i < iter; i++) {
				checkActionsFast();
				//Utils.sleep(2000);
			}
		} catch (GeneralException e) {
			if (Cfg.DEBUG) {
				Check.log(e);
			}
		}

		em.stopAll();

		assertTrue(sub.triggered == iter);

	}

	private void checkActionsFast() throws GeneralException {

		Trigger[] actionIds = status.getTriggeredActions(Action.FAST_QUEUE);
		for (int i = 0; i < actionIds.length; i++) {
			Trigger trigger = actionIds[i];
			final Action action = status.getAction(trigger.getActionId());
			executeAction(action);
		}
		
	}

	private void executeAction(Action action) {
		for (SubAction sub : action.getSubActions()) {
			sub.execute(new Trigger(0,null));
		}
	}

}
