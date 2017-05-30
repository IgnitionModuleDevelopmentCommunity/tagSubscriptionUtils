package com.byes.ignition.gateway.utils;

import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.common.licensing.LicenseState;

import com.inductiveautomation.ignition.gateway.model.AbstractGatewayModuleHook;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.inductiveautomation.ignition.common.script.ScriptManager;

public class GatewayHook extends AbstractGatewayModuleHook {

	Logger logger;
	GatewayContext context;

    public boolean isFreeModule(){
        return true;
    }

	public GatewayHook() {
		logger = LogManager.getLogger(this.getClass());
	}

	@Override
	public void setup(GatewayContext context) {
		try {
            logger.info("setup()");
			this.context = context;
		} catch (Exception e) {
			logger.fatal("Error setting module.", e);
		}
	}

    @Override
    public void initializeScriptManager(ScriptManager manager) {
        logger.info("initializeScriptManager()");
		// no gateway scoped function
        super.initializeScriptManager(manager);
    }

	@Override
	public void startup(LicenseState licenseState) {
        logger.info("startup()");
	}

	@Override
	public void shutdown() {
        logger.info("Utils module stopped.");
        // Remove properties files
        BundleUtil.get().removeBundle(getClass());
	}

}
