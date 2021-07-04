## 5.0.3 date 2021-06-27
参考[TX-LCN补充说明：解决集群节点下，分布式事务回调用，会路由到非发起方机器上去](https://blog.csdn.net/zhuwei_clark/article/details/103711929)修复了次问题。

## 本地事务
1. TransactionAspect 代理所有连接，优先级最高。
2. DTXResourceWeaver 当要获取一个连接时，判断是否开启了LCN事务，没有开启获取普通连接，开启了获取LCN代理连接。

## 远程事务
#### 请求方
#### 接收方
1. SpringTracingApplier.preHandle 拦截远程调用请求。
2. Tracings.apply 读取上一个服务的LCN事务信息（groupId、appList）并写入当前线程。
3.1 没开启@Transation 没开启@LcnTransaction
    3.1.1 正常执行业务
    3.2.2 发起远程调用，继续彻底LCN事物信息 Tracings.transmit

3.2 已开启@Transation 没开启@LcnTransaction
    3.2.1 不走LCN代理连接
    3.2.2 发起远程调用，继续彻底LCN事物信息 Tracings.transmit

3.3 已开启@Transation 已开启@LcnTransaction
    3.3.1 获取LCN代理连接
    3.3.2 发起远程调用，继续彻底LCN事物信息 Tracings.transmit

## LcnConnectionProxy 代理连接工作原理
1. LcnConnectionProxy implements Connection
2. connection.setAutoCommit(false)
3. 当前事务结束后，调用 connection.commit 方法。但是 LcnConnectionProxy 把这个方法给注释了不执行。就一直没有提交事务。
4. TM自动通知或者TC主动查询得知状态，然后在LcnConnectionProxy.notify处理提交或者回滚方法。
    4.1 主动查询
        4.1.1 TransactionAspect.runWithLcnTransaction                       切面，一切的起源
        4.1.2 DTXLogicWeaver.runTransaction                                 执行事务（LCN、TXC、TCC）
        4.1.3 DTXServiceExecutor.transactionRunning                         执行事务（业务执行前、业务执行中、业务执行成功，然后业务执行成功在加入事物组？为什么？不应该是在执行之前加入吗）
        4.1.1 TransactionControlTemplate.joinGroup                          加入事务

        4.1.2 SimpleDTXChecking.startDelayCheckingAsync                     定时异步检测

        4.1.3 SimpleDTXChecking.onAskTransactionStateException              定时：查询事务状态
        4.1.4 TransactionCleanTemplate.cleanWithoutAspectLog                定时：清理事务（成功或者失败）
        4.1.5 LcnTransactionResourceProxy.clear                             定时：清理事务（成功或者失败）
        4.1.6 LcnConnectionProxy.notify                                     定时：清理事务（成功或者失败）

    4.2 异步通知
        4.1.1 TransactionAspect.runWithLcnTransaction                       切面，一切的起源
        4.1.2 DTXLogicWeaver.runTransaction                                 执行事务（LCN、TXC、TCC）
        4.1.3 DTXServiceExecutor.transactionRunning                         执行事务（业务执行前、业务执行中、业务执行成功，然后业务执行成功在加入事物组？为什么？不应该是在执行之前加入吗）
        4.1.1 TransactionControlTemplate.joinGroup                          加入事务
        4.2.2 LoopMessenger.joinGroup                                       加入事务

        4.2.3 LoopMessenger.request                                         Netty 加入事务组
        4.2.3 LoopMessenger.request0                                        Netty 加入事务组
        4.2.3 LoopMessenger.request                                         Netty 加入事务组
        4.2.3 NettyRpcClient.request                                        Netty 加入事务组
        4.2.3 NettyRpcClient.request0                                       Netty 加入事务组
        4.2.3 SocketManager.request                                         发送Netty信息
        4.2.2 RpcCmdDecoder.channelRead0                                    异步：解析器
        4.2.3 RpcAnswerHandler.channelRead0                                 异步：解析器
        4.2.5 ClientRpcAnswer.callback                                      异步：处理异步回调
        4.2.6 DefaultNotifiedUnitService.execute                            异步：处理异步通知
        4.1.7 TransactionCleanTemplate.clean                                异步：清理事务（成功或者失败）

        4.1.8 TransactionCleanTemplate.cleanWithoutAspectLog                异步：清理事务（成功或者失败）
        4.1.9 LcnTransactionResourceProxy.clear                             异步：清理事务（成功或者失败）
        4.1.0 LcnConnectionProxy.notify                                     异步：清理事务（成功或者失败）

5. 也就是说，即使没有开启@Transation注解，事务也自动生效。

## DTXLocalContext.cur().getGroupId() 生成原理
1. TransactionAspect.runWithLcnTransaction                                  切面，一切的起源
2. DTXLogicWeaver.runTransaction                                            执行事务（LCN、TXC、TCC）
    2.1 DTXLocalContext.getOrNew()                                          创建线程对象，并缓存在ThreadLocal<DTXLocalContext>
    2.2 DefaultGlobalContext.startTx                                        在当前线程开启事务
        2.1.1 TracingContext.beginTransactionGroup                          生成groupId，并缓存在ThreadLocal<TracingContext>
    2.3 dtxLocalContext.setGroupId(txContext.getGroupId())                  给dtxLocalContext设置groupId

2. DataSourceAspect.around                                                  拦截获取线程的需求
    2.1 DTXResourceWeaver.getConnection                                     获取连接
        2.2 LcnTransactionResourceProxy.proxyConnection                     获取groupId