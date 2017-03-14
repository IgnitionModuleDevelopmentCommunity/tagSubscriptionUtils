package com.byes.ignition.designer.utils;

import com.byes.ignition.client.utils.ClientScriptModule;
import com.byes.ignition.client.utils.ClientScriptModuleDataset;
import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.common.script.hints.PropertiesFileDocProvider;
import com.inductiveautomation.ignition.designer.model.AbstractDesignerModuleHook;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.inductiveautomation.ignition.common.script.ScriptManager;

public class DesignerHook extends AbstractDesignerModuleHook {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private DesignerContext designerContext = null;
    private ClientScriptModule clientScriptModule = null;
    private ClientScriptModuleDataset clientScriptModuleDataset = null;

    @Override
    public void startup(DesignerContext context, LicenseState activationState) throws Exception {
        logger.info("startup()");
        super.startup(context, activationState);
        this.designerContext = context;
    }

    @Override
    public void initializeScriptManager(ScriptManager manager) {
        logger.info("initializeScriptManager()");
        super.initializeScriptManager(manager);

        clientScriptModule = new ClientScriptModule(designerContext);
        clientScriptModuleDataset = new ClientScriptModuleDataset(designerContext);

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
        logger.info("shutdown()");
        super.shutdown();
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
