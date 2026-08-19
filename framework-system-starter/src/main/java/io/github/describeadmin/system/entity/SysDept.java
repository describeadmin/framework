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
    public List<SysDept> getChildren() { return children; }
    public void setChildren(List<SysDept> children) { this.children = children; }
}
