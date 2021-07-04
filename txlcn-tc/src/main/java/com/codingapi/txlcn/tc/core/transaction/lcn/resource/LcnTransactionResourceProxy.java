/*
 * Copyright 2017-2019 CodingApi .
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.codingapi.txlcn.tc.core.transaction.lcn.resource;

import com.codingapi.txlcn.common.exception.TCGlobalContextException;
import com.codingapi.txlcn.tc.aspect.weave.ConnectionCallback;
import com.codingapi.txlcn.tc.core.DTXLocalContext;
import com.codingapi.txlcn.tc.core.context.TCGlobalContext;
import com.codingapi.txlcn.tc.support.TxLcnBeanHelper;
import com.codingapi.txlcn.tc.support.resouce.TransactionResourceProxy;
import com.codingapi.txlcn.tracing.TracingContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.util.Objects;

/**
 * @author lorne
 */
@Service(value = "transaction_lcn")
@Slf4j
public class LcnTransactionResourceProxy implements TransactionResourceProxy {

    @Autowired
    private TxLcnBeanHelper txLcnBeanHelper;

    private final TCGlobalContext globalContext;

    @Autowired
    public LcnTransactionResourceProxy(TCGlobalContext globalContext) {
        this.globalContext = globalContext;
    }

    @Override
    public Connection proxyConnection(ConnectionCallback connectionCallback) throws Throwable {
        // 临时，先开启LCN事务，好生成groupId
        globalContext.startTx();
        System.out.println("本地线程数据：" + TracingContext.tracing().fields());

        // DTXLocalContext dtxLocalContext = DTXLocalContext.cur();
        DTXLocalContext dtxLocalContext = DTXLocalContext.getOrNew();
        dtxLocalContext.setGroupId(TracingContext.tracing().groupId());

        Boolean isLocalTransation = !(Objects.nonNull(dtxLocalContext) && dtxLocalContext.isProxy());
        log.info("isLocalTransation: " + isLocalTransation);

        // 1. 是分布式事务

        // 2. 不是分布式事务



        String groupId = dtxLocalContext.getGroupId();
        log.trace("获取到一个groupId：{}", groupId);

        // 1. 从缓存里面读取连接
        try {
            return globalContext.getLcnConnection(groupId);
        }
        // 2. 缓存没有代理连接，则新建一个放进缓存里面
        catch (TCGlobalContextException e) {
            LcnConnectionProxy lcnConnectionProxy = new LcnConnectionProxy(connectionCallback.call(), isLocalTransation);
            globalContext.setLcnConnection(groupId, lcnConnectionProxy);
            lcnConnectionProxy.setAutoCommit(false);
            return lcnConnectionProxy;
        }
    }
}
