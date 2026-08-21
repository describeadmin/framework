package io.github.describeadmin.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.describeadmin.mybatis.api.BaseEntity;

import java.util.ArrayList;
import java.util.List;

/** 部门。 */
@TableName("sys_dept")
public class SysDept extends BaseEntity {

    private Long parentId;
    private String deptName;
    private String leader;
    private String phone;
    private Integer sort;
    private Integer status;

    /**
     * 祖先部门 id，逗号分隔，从根到直接父级，不含自身、不含合成的顶级标记——
     * 顶级部门（{@code parentId=0}）的本字段是空串。由
     * {@code SysDeptService.createDept()}/{@code updateDept()} 维护，业务方不应直接赋值。
     * 供"本部门及以下"这档数据权限用 {@code FIND_IN_SET} 判断下级关系。
     */
    private String ancestors;

    @TableField(exist = false)
    private List<SysDept> children = new ArrayList<>();

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public String getLeader() { return leader; }
    public void setLeader(String leader) { this.leader = leader; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getAncestors() { return ancestors; }
    public void setAncestors(String ancestors) { this.ancestors = ancestors; }
    public List<SysDept> getChildren() { return children; }
    public void setChildren(List<SysDept> children) { this.children = children; }
}
