package io.github.describeadmin.it.fixture;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.describeadmin.mybatis.api.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 集成测试用的最小业务实体，形态与 codegen 生成物一致。
 *
 * <p>审计字段 / 主键 / 逻辑删除 / 乐观锁版本均由 {@link BaseEntity} 承担。
 * 框架自身代码不用 Lombok（CLAUDE.md 4.10），本 fixture 亦手写访问器。
 */
@TableName("biz_project")
public class ProjectEntity extends BaseEntity {

    private String projectName;
    private String projectCode;
    private Long ownerDeptId;
    private BigDecimal budget;
    private LocalDate startDate;
    private Integer status;
    private String remark;

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }

    public String getProjectCode() { return projectCode; }
    public void setProjectCode(String projectCode) { this.projectCode = projectCode; }

    public Long getOwnerDeptId() { return ownerDeptId; }
    public void setOwnerDeptId(Long ownerDeptId) { this.ownerDeptId = ownerDeptId; }

    public BigDecimal getBudget() { return budget; }
    public void setBudget(BigDecimal budget) { this.budget = budget; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
