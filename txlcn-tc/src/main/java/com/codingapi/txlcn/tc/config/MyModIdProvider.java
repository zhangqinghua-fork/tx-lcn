package com.codingapi.txlcn.tc.config;

import com.codingapi.txlcn.common.util.id.ModIdProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MyModIdProvider implements ModIdProvider {

    @Autowired
    private ServerConfig serverConfig;

    public String getSeverID() {
        String serverId = serverConfig.getServerID();
        System.out.println("----------------====MyModIdProvide.getSeverID=============: " + serverId);
        return serverId;
    }

    @Override
    public String modId() {
        return getSeverID();
    }
}
