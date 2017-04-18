package com.byes.ignition.gateway.utils;

import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.common.licensing.LicenseState;

import com.inductiveautomation.ignition.gateway.clientcomm.ClientReqSession;
import com.inductiveautomation.ignition.gateway.model.AbstractGatewayModuleHook;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

import com.inductiveautomation.metro.api.ServiceManager;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

// 2.0.2 : exposition fonction pour insérer dans audit
import com.inductiveautomation.ignition.common.script.ScriptManager;
import com.inductiveautomation.ignition.common.script.hints.PropertiesFileDocProvider;

public class GatewayHook extends AbstractGatewayModuleHook {

	Logger logger;
	GatewayContext context;

    public boolean isFreeModule(){
        return true;
    }

    private GatewayScriptModule scriptModule = new GatewayScriptModule();

	public GatewayHook() {
		logger = LogManager.getLogger(this.getClass());
	}

	@Override
	public void setup(GatewayContext context) {
		try {
            logger.info("setup()");
			this.context = context;
//            scriptModule.setContext(context);

		} catch (Exception e) {
			logger.fatal("Error setting module.", e);
		}
	}

    @Override
    public void initializeScriptManager(ScriptManager manager) {
        logger.info("initializeScriptManager()");

        super.initializeScriptManager(manager);

        // no gateway scoped function
		/*
        manager.addScriptModule(
                "system.audit",
                scriptModule,
                new PropertiesFileDocProvider());
                */
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
