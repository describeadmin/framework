package io.github.describeadmin.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.describeadmin.mybatis.api.BaseEntity;

/** 字典数据项，通过 {@link #dictType} 关联到 {@link SysDictType#getDictType()}。 */
@TableName("sys_dict_data")
public class SysDictData extends BaseEntity {

    private String dictType;
    private String dictLabel;
    private String dictValue;
    private Integer sort;
    private Integer status;

    public String getDictType() { return dictType; }
    public void setDictType(String dictType) { this.dictType = dictType; }
    public String getDictLabel() { return dictLabel; }
    public void setDictLabel(String dictLabel) { this.dictLabel = dictLabel; }
    public String getDictValue() { return dictValue; }
    public void setDictValue(String dictValue) { this.dictValue = dictValue; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
