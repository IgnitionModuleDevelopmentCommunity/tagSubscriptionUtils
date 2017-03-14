package com.byes.ignition.gateway.utils;

import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GatewayScriptModule {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private GatewayContext context;


    public GatewayScriptModule() {
        logger.info("Constructor GatewayScriptModule");
    }

    public void setContext(GatewayContext context){
        logger.info("set GatewayContext");
        this.context = context;
    }

}
