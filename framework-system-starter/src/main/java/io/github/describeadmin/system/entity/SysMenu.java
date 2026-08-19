package io.github.describeadmin.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.describeadmin.mybatis.api.BaseEntity;

import java.util.ArrayList;
import java.util.List;

/** 菜单与权限点。 */
@TableName("sys_menu")
public class SysMenu extends BaseEntity {

    /** 菜单类型：DIR 目录 / MENU 菜单 / BUTTON 按钮。 */
    public static final String TYPE_DIR = "DIR";
    public static final String TYPE_MENU = "MENU";
    public static final String TYPE_BUTTON = "BUTTON";

    private Long parentId;
    private String menuName;
    private String menuType;
    private String permCode;
    private String path;
    private String component;
    private String icon;
    private Integer sort;
    private Integer visible;

    /** 非持久化字段：构建菜单树时装子节点。 */
    @TableField(exist = false)
    private List<SysMenu> children = new ArrayList<>();

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getMenuName() { return menuName; }
    public void setMenuName(String menuName) { this.menuName = menuName; }
    public String getMenuType() { return menuType; }
    public void setMenuType(String menuType) { this.menuType = menuType; }
    public String getPermCode() { return permCode; }
    public void setPermCode(String permCode) { this.permCode = permCode; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getComponent() { return component; }
    public void setComponent(String component) { this.component = component; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
    public Integer getVisible() { return visible; }
    public void setVisible(Integer visible) { this.visible = visible; }
    public List<SysMenu> getChildren() { return children; }
    public void setChildren(List<SysMenu> children) { this.children = children; }
}
