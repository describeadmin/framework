package io.github.describeadmin.common.api;

import java.util.Set;

/**
 * 当前登录用户的数据权限上下文，供 {@link DataScopeProvider} 返回。
 *
 * <p>本类型位于 {@code api} 包下，属于兼容性承诺范围。
 *
 * @param userId        当前用户 ID，{@link DataScopeType#SELF} 档据此比对 {@code create_by}
 * @param deptId        当前用户所属部门 ID；{@link DataScopeType#DEPT} /
 *                      {@link DataScopeType#DEPT_AND_CHILD} 档据此生效——取的是用户
 *                      <b>当前</b>部门，不是角色配置时固化的部门，用户调岗后自动生效
 * @param scopeType     当前用户全部角色合并后的有效范围
 * @param customDeptIds {@code scopeType} 为 {@link DataScopeType#CUSTOM} 时的部门 ID 集合；
 *                      其余档为空集
 */
public record DataScopeContext(Long userId, Long deptId, DataScopeType scopeType, Set<Long> customDeptIds) {
}
