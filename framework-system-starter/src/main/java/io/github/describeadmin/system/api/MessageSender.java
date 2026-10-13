package io.github.describeadmin.system.api;

import java.util.Collection;

/**
 * 站内信发送入口。业务方注入本接口发消息，框架默认实现是 {@code SysMessageService}。
 *
 * <p>与 {@code NotifyChannel} 是两回事：{@code NotifyChannel} 把消息推到外部系统（钉钉、短信），
 * 发完不管；站内信落库、有已读状态，收件人在顶栏铃铛与消息中心查看。需要"站内信 + 外推"时
 * 由业务方分别调用两者，不要把站内信注册成一个 {@code NotifyChannel}。
 *
 * <p>框架只提供这个服务端 API，<b>不开放"用户给用户发消息"的 HTTP 接口</b>——谁能给谁发、
 * 防刷、内容审核都是业务规则。留言这类功能由业务方实现，校验通过后调用 {@link #send}。
 */
public interface MessageSender {

    /** 单次发送的收件人上限。超过即抛错，不截断；全员通知属于"公告"，不走站内信。 */
    int MAX_RECIPIENTS = 2000;

    /**
     * 给一批用户各发一条站内信。
     *
     * <p>参与调用方事务（{@code REQUIRED}）：业务事务回滚，消息一并回滚，
     * 不会出现"工单没建成、审批人却收到待办"。
     *
     * <p>收件人去重、剔除 {@code null}；为空时什么也不做。不校验用户是否存在。
     *
     * @throws IllegalArgumentException 收件人超过 {@link #MAX_RECIPIENTS}
     */
    void send(Collection<Long> userIds, MessageDraft draft);

    /**
     * 把某个业务对象相关的未读消息<b>对所有收件人</b>标为已读，返回受影响条数。
     *
     * <p>典型场景是或签：一张工单同时发给三位审批人，其中一人审完后调用本方法，
     * 另外两人收件箱里那条"待审批"随之变为已读，不会点进去才发现已被处理。
     *
     * <p>收件箱不跟踪业务状态（审批是否完成以业务表为准），本方法只是收尾；
     * 忘了调用的后果是多一条未读，不会出错。
     */
    int markReadByBiz(String category, String bizKey);
}
