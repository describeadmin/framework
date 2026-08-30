package io.github.describeadmin.mybatis.api;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.PageQuery;
import io.github.describeadmin.common.api.PageResult;
import io.github.describeadmin.common.api.PermissionChecker;
import io.github.describeadmin.common.api.Result;
import io.github.describeadmin.common.api.ResultCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * Controller 基类，提供标准 CRUD 端点。
 *
 * <p>业务 Controller 继承本类后即拥有 list / get / create / update / delete 五个端点，
 * 只需补充业务特有接口。所有方法均返回 {@link Result}，异常交给全局异常处理器。
 *
 * <p>子类需标注 {@code @RestController} 与 {@code @RequestMapping("/api/xxx")}。
 * 需要收窄能力时覆写对应方法并加 {@code @Override}，或直接不继承本类。
 *
 * <p>本类位于 {@code api} 包下，属于兼容性承诺范围。
 *
 * @param <S> Service 类型
 * @param <M> Mapper 类型
 * @param <T> 实体类型
 */
public abstract class BaseController<S extends BaseService<M, T>,
        M extends BaseMapper<T>, T extends BaseEntity> {

    private PermissionChecker permissionChecker = PermissionChecker.PERMIT_ALL;

    private volatile String cachedPermPrefix;

    /**
     * 注入权限校验器。
     *
     * <p><b>用 setter 而非构造注入是有意的</b>：子类的构造函数签名
     * （{@code public XxxController(XxxService service)}）已经被 codegen 的模板和
     * 既有业务代码固定下来，往基类构造函数加参数会要求每个子类同步改签名——
     * 那是一次波及全部业务仓库的破坏性变更。setter 注入对子类完全透明。
     *
     * <p>{@code required = false}：未引入 framework-security-starter 时没有
     * {@code PermissionChecker} Bean，Spring 直接跳过本次调用，字段保持
     * {@link PermissionChecker#PERMIT_ALL}——那种场景下应用本来就没有认证，谈不上权限。
     */
    @Autowired(required = false)
    public void setPermissionChecker(PermissionChecker permissionChecker) {
        this.permissionChecker = permissionChecker == null
                ? PermissionChecker.PERMIT_ALL
                : permissionChecker;
    }

    /**
     * @return 业务 Service 实例，由子类提供（通常构造注入）
     */
    protected abstract S getService();

    /**
     * 本 Controller 的权限点前缀，通用端点在其后拼接 {@code :list} / {@code :add} /
     * {@code :edit} / {@code :remove}。
     *
     * <p>默认从子类的 {@code @RequestMapping} 路径推导：去掉 {@code /api} 前缀，
     * 其余段用 {@code :} 连接。这条规则与既有权限点天然吻合，两侧都已核对：
     * <ul>
     *   <li>{@code /api/system/user} → {@code system:user}，
     *       对应 {@code seed-rbac.sql} 里的 {@code system:user:list}</li>
     *   <li>{@code /api/project} → {@code project}，
     *       对应 codegen 的 {@code MenuSqlGenerator} 产出的 {@code project:list}</li>
     * </ul>
     *
     * <p><b>什么时候必须覆写</b>：spec 里自定义了 {@code apiPrefix} 使其与模块名不一致时，
     * 推导结果会与 {@code menu-*.sql} 里登记的权限点对不上。codegen 已改为
     * 在这种情况下直接生成覆写，业务方手写的 Controller 需自行注意。
     *
     * <p><b>为什么是 {@code public} 而不是 {@code protected}</b>：操作日志切面
     * （{@code framework-system-starter} 的 {@code OperLogAspect}）需要跨包读取它，
     * 用它给自动记录的日志打上"模块"标签。放宽可见性是纯粹的加法，不影响任何既有调用方。
     *
     * @return 权限点前缀，如 {@code system:user}
     */
    public String permPrefix() {
        String cached = cachedPermPrefix;
        if (cached == null) {
            cached = derivePermPrefix();
            cachedPermPrefix = cached;
        }
        return cached;
    }

    /**
     * 校验当前用户是否持有 {@link #permPrefix()} 下的某个动作权限。
     *
     * <p>子类新增的自定义端点也可以调用本方法，或者直接用
     * {@code @PreAuthorize("hasAuthority('system:user:edit')")}——两者最终产出的
     * 拒绝响应完全一致。
     *
     * @param action 动作，如 {@code list} / {@code add} / {@code edit} / {@code remove}
     */
    protected void requirePermission(String action) {
        permissionChecker.require(permPrefix() + ":" + action);
    }

    private String derivePermPrefix() {
        // getUserClass 剥掉可能存在的 CGLIB 代理，否则拿到的是 XxxController$$SpringCGLIB$$0
        Class<?> target = ClassUtils.getUserClass(getClass());
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(target, RequestMapping.class);
        String path = mapping == null || mapping.value().length == 0 ? "" : mapping.value()[0];

        String normalized = path.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.equals("api")) {
            normalized = "";
        } else if (normalized.startsWith("api/")) {
            normalized = normalized.substring("api/".length());
        }

        if (normalized.isEmpty()) {
            throw new IllegalStateException(
                    "无法从 " + target.getName() + " 的 @RequestMapping 推导权限点前缀，"
                            + "请覆写 permPrefix() 显式返回，例如 \"system:user\"");
        }
        return normalized.replace('/', ':');
    }

    /**
     * 分页列表。
     *
     * <p>筛选条件由 {@link #buildListWrapper(Map)} 决定，默认不筛选。
     *
     * <p><b>为什么把原始参数整体接进来，而不是让子类各自声明 {@code @RequestParam}</b>：
     * 子类若声明一个签名不同的 {@code list(...)} 并标 {@code @GetMapping}，
     * 它<b>不是覆写而是重载</b>，于是同一个 {@code GET} 路径上出现两个映射，
     * Spring 启动时直接报 Ambiguous mapping。留一个签名固定的入口 + 一个覆写点，
     * 从结构上杜绝这种写法（本项目实测踩过）。
     */
    @GetMapping
    public Result<PageResult<T>> list(PageQuery query,
                                      @RequestParam(required = false) Map<String, String> params) {
        requirePermission("list");
        return Result.ok(getService().page(query, buildListWrapper(
                params == null ? Map.of() : params)));
    }

    /**
     * 构造列表查询的筛选条件。默认不筛选。
     *
     * <p>子类覆写本方法即可支持条件查询；codegen 会按 spec 里的 {@code query} 字段
     * 自动生成实现。参数是原始查询串（含 {@code current}/{@code size}，忽略即可），
     * 类型转换由子类负责——框架不知道每个字段该转成什么。
     *
     * @param params 请求的全部查询参数，不为 null
     * @return 筛选条件；返回 {@code null} 表示不筛选
     */
    protected Wrapper<T> buildListWrapper(Map<String, String> params) {
        return null;
    }

    @GetMapping("/{id}")
    public Result<T> get(@PathVariable Long id) {
        // 查看详情复用查看权限：seed-rbac.sql 与 codegen 都只产出
        // list/add/edit/remove 四个动作，不存在单独的详情权限点
        requirePermission("list");
        T entity = getService().getById(id);
        if (entity == null) {
            throw new BizException(ResultCode.NOT_FOUND, "记录不存在: " + id);
        }
        return Result.ok(entity);
    }

    @PostMapping
    public Result<T> create(@RequestBody T entity) {
        requirePermission("add");
        // 主键由数据库自增或全局 ID 策略生成，忽略客户端传入值，防止越权指定主键
        entity.setId(null);
        getService().save(entity);
        return Result.ok(entity);
    }

    @PutMapping("/{id}")
    public Result<T> update(@PathVariable Long id, @RequestBody T entity) {
        requirePermission("edit");
        // 以路径上的 id 为准，避免 body 与 path 不一致导致改错记录
        entity.setId(id);
        if (!getService().updateById(entity)) {
            // updateById 返回 false 的两种可能：记录不存在，或乐观锁版本冲突
            throw new BizException(ResultCode.NOT_FOUND, "记录不存在或已被他人修改: " + id);
        }
        return Result.ok(getService().getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        requirePermission("remove");
        // BaseEntity 上有 @TableLogic，这里执行的是逻辑删除
        if (!getService().removeById(id)) {
            throw new BizException(ResultCode.NOT_FOUND, "记录不存在: " + id);
        }
        return Result.ok();
    }
}
