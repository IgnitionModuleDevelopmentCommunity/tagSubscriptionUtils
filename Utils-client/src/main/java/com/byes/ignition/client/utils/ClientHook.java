package com.byes.ignition.client.utils;

import com.inductiveautomation.ignition.client.model.ClientContext;
import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.common.script.hints.PropertiesFileDocProvider;
import com.inductiveautomation.vision.api.client.AbstractClientModuleHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.inductiveautomation.ignition.common.script.ScriptManager;

public class ClientHook extends AbstractClientModuleHook {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private ClientContext clientContext = null;
    private ClientScriptModule clientScriptModule = null;
    private ClientScriptModuleDataset clientScriptModuleDataset = null;

    @Override
    public void startup(ClientContext context, LicenseState activationState) throws Exception {
        super.startup(context, activationState);
        this.clientContext = context;
    }

    @Override
    public void initializeScriptManager(ScriptManager manager) {
        super.initializeScriptManager(manager);
        clientScriptModule = new ClientScriptModule(clientContext);
        clientScriptModuleDataset = new ClientScriptModuleDataset(clientContext);

        manager.addScriptModule(
                "system.byes.utils",
                clientScriptModule,
                new PropertiesFileDocProvider());

        manager.addScriptModule(
                "system.byes.utils.dataset",
                clientScriptModuleDataset,
                new PropertiesFileDocProvider());

    }

    @Override
    public void shutdown() {
        super.shutdown();
        logger.info("shutdown()");
        if (clientScriptModule != null){
            clientScriptModule.stopCyclicUpdateTagClientDataset();
            clientScriptModule.shutdownCyclicUpdateTagClientDataset();
        }
        if (clientScriptModuleDataset != null){
            clientScriptModuleDataset.stopCyclicUpdateTagClientDataset();
            clientScriptModuleDataset.shutdownCyclicUpdateTagClientDataset();
        }
    }
}
