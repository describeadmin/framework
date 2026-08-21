package io.github.describeadmin.mybatis.core;

import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import io.github.describeadmin.common.api.DataScopeContext;
import io.github.describeadmin.common.api.DataScopeProvider;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;

import java.util.Map;
import java.util.Set;

/**
 * 按部门做行级过滤的 {@link MultiDataPermissionHandler} 实现。
 *
 * <p>MyBatis-Plus 的 {@code DataPermissionInterceptor} 会对 SQL 里出现的<b>每一张表</b>
 * 调用一次 {@link #getSqlSegment}，覆盖 SELECT/UPDATE/DELETE——这正是
 * {@code BaseController.get()/update()/delete()} 这类走 {@code selectById}/
 * {@code updateById} 的路径也能被管住的原因，只改 {@code buildListWrapper()} 做不到。
 *
 * <p>表要不要参与过滤是显式登记（见 {@code tableToDeptColumn}），不是每张表自动被管；
 * 未登记的表直接放行，业务方自己的表默认不受影响。
 *
 * <p>拼进 SQL 片段里的 id 都来自登录态（{@link DataScopeContext}），不是请求参数，
 * 没有注入面——这与 MyBatis-Plus 官方多租户示例的做法一致。
 *
 * <p>非兼容性承诺范围，实现细节可能变化。
 */
public class DeptDataPermissionHandler implements MultiDataPermissionHandler {

    private final Map<String, String> tableToDeptColumn;
    private final DataScopeProvider dataScopeProvider;

    public DeptDataPermissionHandler(Map<String, String> tableToDeptColumn, DataScopeProvider dataScopeProvider) {
        this.tableToDeptColumn = Map.copyOf(tableToDeptColumn);
        this.dataScopeProvider = dataScopeProvider;
    }

    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        String deptColumn = tableToDeptColumn.get(table.getName());
        if (deptColumn == null) {
            // 表未登记参与数据权限，不过滤
            return null;
        }
        DataScopeContext ctx = dataScopeProvider.current().orElse(null);
        if (ctx == null) {
            // 无登录上下文（如系统内部调用），不过滤——与 CurrentUserProvider.NOOP、
            // PermissionChecker.PERMIT_ALL 在鉴权层缺席时的取舍一致
            return null;
        }
        String condition = buildCondition(ctx, deptColumn);
        if (condition == null) {
            // ALL 档不过滤
            return null;
        }
        try {
            return CCJSqlParserUtil.parseCondExpression(condition);
        } catch (JSQLParserException e) {
            // condition 由本类自己拼装，不含用户输入，解析失败说明拼装逻辑本身有 bug
            throw new IllegalStateException("数据权限条件解析失败: " + condition, e);
        }
    }

    private String buildCondition(DataScopeContext ctx, String deptColumn) {
        return switch (ctx.scopeType()) {
            case ALL -> null;
            case DEPT -> deptColumn + " = " + ctx.deptId();
            case DEPT_AND_CHILD -> deptColumn + " IN (SELECT id FROM sys_dept WHERE id = "
                    + ctx.deptId() + " OR FIND_IN_SET(" + ctx.deptId() + ", ancestors))";
            case CUSTOM -> customCondition(deptColumn, ctx.customDeptIds());
            case SELF -> "create_by = " + ctx.userId();
        };
    }

    private String customCondition(String deptColumn, Set<Long> customDeptIds) {
        if (customDeptIds == null || customDeptIds.isEmpty()) {
            // 自定义范围但一个部门都没配 —— 什么都看不见，而不是退化成看得见一切
            return "1 = 0";
        }
        StringBuilder sb = new StringBuilder(deptColumn).append(" IN (");
        int i = 0;
        for (Long deptId : customDeptIds) {
            if (i++ > 0) {
                sb.append(',');
            }
            sb.append(deptId);
        }
        return sb.append(')').toString();
    }
}
