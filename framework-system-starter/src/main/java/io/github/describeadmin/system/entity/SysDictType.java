package io.github.describeadmin.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.describeadmin.mybatis.api.BaseEntity;

/** 字典类型。 */
@TableName("sys_dict_type")
public class SysDictType extends BaseEntity {

    private String dictType;
    private String dictName;
    private Integer status;

    public String getDictType() { return dictType; }
    public void setDictType(String dictType) { this.dictType = dictType; }
    public String getDictName() { return dictName; }
    public void setDictName(String dictName) { this.dictName = dictName; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
