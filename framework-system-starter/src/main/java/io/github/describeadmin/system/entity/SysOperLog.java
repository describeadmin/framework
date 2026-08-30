package io.github.describeadmin.system.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志。
 *
 * <p><b>不继承 {@link io.github.describeadmin.mybatis.api.BaseEntity}</b>：这是一张
 * 只追加、不给用户改的审计表，{@code BaseEntity} 的审计字段/逻辑删除/乐观锁语义不适用——
 * 与 {@code SysOnlineController} 不继承 {@code BaseController} 是同一类判断，
 * 只是这里换成了实体这一层。写入完全由 {@code OperLogAspect} 一次性控制，
 * 不需要 {@code MetaObjectHandler} 自动填充。
 */
@TableName("sys_oper_log")
public class SysOperLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_FAIL = 0;

    @TableId
    private Long id;

    /** 模块，取自 {@code permPrefix()} 或 {@code @OperLog} 注解，如 {@code system:dept}。 */
    private String module;

    /** 操作描述，如"创建部门"。 */
    private String description;

    private String httpMethod;
    private String requestUrl;
    private Long operatorId;
    private String operatorName;
    private String operatorIp;

    /** 脱敏后的请求参数 JSON，截断到 2000 字符。 */
    private String requestParam;

    /** {@link #STATUS_SUCCESS} / {@link #STATUS_FAIL}。 */
    private Integer status;

    private String errorMsg;

    /** 耗时，毫秒。 */
    private Long costTime;

    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }
    public String getRequestUrl() { return requestUrl; }
    public void setRequestUrl(String requestUrl) { this.requestUrl = requestUrl; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public String getOperatorIp() { return operatorIp; }
    public void setOperatorIp(String operatorIp) { this.operatorIp = operatorIp; }
    public String getRequestParam() { return requestParam; }
    public void setRequestParam(String requestParam) { this.requestParam = requestParam; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
    public Long getCostTime() { return costTime; }
    public void setCostTime(Long costTime) { this.costTime = costTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
