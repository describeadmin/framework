package io.github.describeadmin.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.describeadmin.cache.api.UniqueGuard;
import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.ResultCode;
import io.github.describeadmin.mybatis.api.BaseService;
import io.github.describeadmin.security.api.PasswordPolicy;
import io.github.describeadmin.security.api.TokenStore;
import io.github.describeadmin.system.entity.SysUser;
import io.github.describeadmin.system.entity.SysUserPasswordHistory;
import io.github.describeadmin.system.mapper.SysRelationMapper;
import io.github.describeadmin.system.mapper.SysUserMapper;
import io.github.describeadmin.system.mapper.SysUserPasswordHistoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

/** 用户管理。 */
@Service
public class SysUserService extends BaseService<SysUserMapper, SysUser> {

    private static final Logger log = LoggerFactory.getLogger(SysUserService.class);

    /** 参数键：密码历史不可重用数（&gt; 0 生效）。与 seed-rbac.sql 的内置参数对应。 */
    private static final String CFG_HISTORY_COUNT = "sys.password.history-count";

    private final SysRelationMapper relationMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenStore tokenStore;
    private final PasswordPolicy passwordPolicy;
    private final SysUserPasswordHistoryMapper passwordHistoryMapper;
    private final SysConfigService configService;
    private final UniqueGuard uniqueGuard;
    private final TransactionOperations transactionOperations;

    public SysUserService(SysRelationMapper relationMapper, PasswordEncoder passwordEncoder, TokenStore tokenStore,
                          PasswordPolicy passwordPolicy, SysUserPasswordHistoryMapper passwordHistoryMapper,
                          SysConfigService configService, UniqueGuard uniqueGuard,
                          ObjectProvider<TransactionTemplate> transactionTemplateProvider) {
        this.relationMapper = relationMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenStore = tokenStore;
        this.passwordPolicy = passwordPolicy;
        this.passwordHistoryMapper = passwordHistoryMapper;
        this.configService = configService;
        this.uniqueGuard = uniqueGuard;
        this.transactionOperations = resolveTransactionOperations(transactionTemplateProvider);
    }

    /**
     * 解析编程式事务模板，拿不到时退化为"无事务执行"并告警。
     *
     * <p>Spring Boot 只在容器里<b>恰好一个</b> {@code TransactionManager} 时才自动配
     * {@code TransactionTemplate}（{@code TransactionAutoConfiguration} 上的
     * {@code @ConditionalOnSingleCandidate}）。多数据源、配了两个事务管理器又都没标
     * {@code @Primary} 的业务方拿不到这个 Bean——硬依赖它会让这些应用<b>在一个补丁版本上
     * 突然启不来</b>，报的还是 {@code NoSuchBeanDefinitionException} 这种不指向真因的错。
     *
     * <p>这里的取舍是"能启动 + 一条照着做就能修的 WARN"，而不是"直接拒绝启动"：
     * 退化后 {@link #createUser} 的多条写入不再原子（建号失败可能留下没有角色的用户），
     * 但这只发生在多事务管理器且未指定主库的场景，且日志已明说代价与修法。
     */
    private static TransactionOperations resolveTransactionOperations(
            ObjectProvider<TransactionTemplate> provider) {
        TransactionTemplate template = provider.getIfAvailable();
        if (template != null) {
            return template;
        }
        log.warn("容器中没有 TransactionTemplate（通常是配了多个 TransactionManager 且都没标 @Primary），"
                + "创建用户的多条写入将不在同一事务中执行。修法：给其中一个 TransactionManager 标 @Primary，"
                + "或自行声明一个 TransactionTemplate Bean");
        return TransactionOperations.withoutTransaction();
    }

    public SysUser findByUsername(String username) {
        // 逻辑删除由 @TableLogic 自动过滤，这里无需再写 deleted = 0
        return getOne(new QueryWrapper<SysUser>().eq("username", username), false);
    }

    /**
     * 按手机号查询。供手机验证码登录等 {@code AuthProvider} 插件把凭证换成 userId 用——
     * 插件不需要自建映射表，手机号本身就是核心字段，直接查这里即可，
     * 拿到 userId 后再调 {@code AuthUserLoader.loadByUserId} 拼出完整用户。
     *
     * <p>也是 {@link #assertMobileEmailAvailable(Long, String, String)} 的唯一性校验入口，
     * 因此走 {@link SysUserMapper#selectByMobileIgnoreDataScope(String)}——"这个手机号
     * 是否已被占用"是全局问题，不受调用方数据权限范围影响，见该方法的类注释。
     */
    public SysUser findByMobile(String mobile) {
        return getBaseMapper().selectByMobileIgnoreDataScope(mobile);
    }

    /** 按邮箱查询，用途与不受数据权限影响的原因同 {@link #findByMobile(String)}。 */
    public SysUser findByEmail(String email) {
        return getBaseMapper().selectByEmailIgnoreDataScope(email);
    }

    /**
     * 自助场景专用：查询当前登录用户自己的账号，忽略数据权限过滤。
     *
     * <p>"我能不能看我自己的账号"与"我能看哪些人的数据"是两个问题，
     * 详见 {@link SysUserMapper#selectSelfById(Long)}。<b>调用方必须自行保证
     * 传入的就是当前登录用户自己的 id</b>，本方法不做权限校验。
     */
    public SysUser getOwnById(Long userId) {
        return getBaseMapper().selectSelfById(userId);
    }

    /**
     * 校验手机号/邮箱未被其他未删除用户占用，非空时才校验。
     *
     * <p>{@code selfId} 是当前正在创建/编辑的用户自身 id：新建时传 {@code null}
     * （不存在"自己"），编辑时传当前 userId，排除"改别的字段但手机号没变"这种误判。
     * 逻辑删除下不能建唯一索引（同 username，见 schema-rbac.sql 注释），唯一性只能在这里保证。
     *
     * <p><b>⚠️ 本方法自身不加锁</b>，只回答"此刻这个手机号/邮箱可不可用"。并发唯一性
     * 由调用方负责：写入路径必须用 {@link #withMobileEmailLock} 把"校验 + 写入"包进
     * 同一个临界区（{@link #createUser}、{@link #updateOwnProfile}、
     * {@link #updateWithUniqueCheck} 都是这么做的）。
     *
     * <p>这里刻意不加锁，是因为 {@link UniqueGuard} 底层的锁<b>不可重入</b>：
     * 入口持锁、本方法再持同一把键，内层必然拿不到锁，单线程也会 100% 抛"操作冲突"
     * （见 {@code UniqueGuard} 的类注释）。而且就算能重入，本方法自己加锁也没用——
     * 锁在方法返回时就释放了，调用方随后的写入根本不在锁里。
     *
     * <p>单独调用本方法是合法的"可用性探测"（如给前端做实时校验），
     * 但它<b>不构成</b>并发保护：返回可用 ≠ 写入时仍然可用。
     */
    public void assertMobileEmailAvailable(Long selfId, String mobile, String email) {
        if (StringUtils.hasText(mobile)) {
            SysUser exist = findByMobile(mobile);
            if (exist != null && !exist.getId().equals(selfId)) {
                throw new BizException(ResultCode.BAD_REQUEST, "手机号已被占用: " + mobile);
            }
        }
        if (StringUtils.hasText(email)) {
            SysUser exist = findByEmail(email);
            if (exist != null && !exist.getId().equals(selfId)) {
                throw new BizException(ResultCode.BAD_REQUEST, "邮箱已被占用: " + email);
            }
        }
    }

    /**
     * 在手机号、邮箱两把键级锁内执行 {@code action}（字段为空时该把锁自动跳过）。
     *
     * <p>两把键不同，且 {@code tryLock} 不等待，嵌套无死锁可能。<b>action 内部不得
     * 再次锁同一把键</b>——锁不可重入，见 {@link UniqueGuard} 的类注释。
     */
    private <T> T withMobileEmailLock(String mobile, String email, Supplier<T> action) {
        return uniqueGuard.execute("user:mobile", mobile, () ->
                uniqueGuard.execute("user:email", email, action));
    }

    /**
     * 创建用户。
     *
     * <p>用户名唯一性在应用层校验而非数据库唯一索引——逻辑删除下建唯一索引会导致
     * 删除后无法复用同名账号（见 schema-rbac.sql 的注释）。手机号/邮箱同理，见
     * {@link #assertMobileEmailAvailable(Long, String, String)}。
     *
     * <p><b>并发唯一性由 {@link UniqueGuard} 保证</b>：username/mobile/email 各占一把
     * 键级锁，同一值的并发创建只有一个能通过校验。事务刻意用
     * {@link TransactionTemplate} 而不是 {@code @Transactional} 放在锁<b>内</b>——
     * 保证 锁 → 事务 → 提交 → 释放锁 的顺序；若顺序反过来（锁在事务里），
     * "锁已释放、事务未提交"的窗口会让下一个请求的校验读不到本条未提交的插入。
     *
     * <p>锁只在这一层加，锁内的校验一律走不加锁的
     * {@link #assertMobileEmailAvailable(Long, String, String)}——锁不可重入，
     * 两层都锁同一把键会让所有带手机号/邮箱的建号请求必现"操作冲突"。
     */
    public SysUser createUser(SysUser user, String rawPassword, List<Long> roleIds) {
        if (!StringUtils.hasText(rawPassword)) {
            throw new BizException(ResultCode.BAD_REQUEST, "初始密码不能为空");
        }
        return uniqueGuard.execute("user:username", user.getUsername(), () ->
                withMobileEmailLock(user.getMobile(), user.getEmail(), () ->
                        transactionOperations.execute(status ->
                                doCreateUser(user, rawPassword, roleIds))));
    }

    private SysUser doCreateUser(SysUser user, String rawPassword, List<Long> roleIds) {
        if (findByUsername(user.getUsername()) != null) {
            throw new BizException(ResultCode.BAD_REQUEST, "用户名已存在: " + user.getUsername());
        }
        assertMobileEmailAvailable(null, user.getMobile(), user.getEmail());
        passwordPolicy.validate(rawPassword, user.getUsername());
        String encoded = passwordEncoder.encode(rawPassword);
        user.setId(null);
        user.setPassword(encoded);
        if (user.getStatus() == null) {
            user.setStatus(1);
        }
        // 管理员建号：设的是初始口令，要求本人首次登录强制改密
        user.setPwdResetRequired(1);
        user.setPwdUpdateTime(LocalDateTime.now());
        save(user);
        assignRoles(user.getId(), roleIds);
        recordPasswordHistory(user.getId(), encoded);
        return user;
    }

    /**
     * 重置密码（管理员改别人）。入参是明文，存储的是哈希。
     *
     * <p>改密后立即吊销该用户已签发的全部令牌——不这样做的话，被改密的用户
     * 已登录的会话仍然有效，要等令牌自然过期才失效，不符合"改密立即失效"的直觉预期
     * （见 {@link TokenStore#revokeAllOf(Long)} 的 javadoc）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long userId, String rawPassword) {
        if (!StringUtils.hasText(rawPassword)) {
            throw new BizException(ResultCode.BAD_REQUEST, "密码不能为空");
        }
        SysUser exist = getById(userId);
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在: " + userId);
        }
        passwordPolicy.validate(rawPassword, exist.getUsername());
        assertNotRecentlyUsed(userId, rawPassword);
        String encoded = passwordEncoder.encode(rawPassword);
        SysUser update = new SysUser();
        update.setId(userId);
        update.setVersion(exist.getVersion());
        update.setPassword(encoded);
        // 管理员重置：同样要求对方下次登录强制改密，并刷新「定期过期」计时起点
        update.setPwdResetRequired(1);
        update.setPwdUpdateTime(LocalDateTime.now());
        updateById(update);
        recordPasswordHistory(userId, encoded);
        tokenStore.revokeAllOf(userId);
    }

    /**
     * 自助改密（当前登录用户改自己的密码）。
     *
     * <p>与 {@link #resetPassword(Long, String)} 的区别：这里要校验旧密码，
     * 而不是管理员凭权限直接覆盖。成功后同样吊销全部令牌——本次修改所在的这一个
     * 请求令牌也会被吊销，前端应据此引导用户用新密码重新登录，而不是指望原地继续用旧令牌。
     *
     * <p><b>密码策略校验必须放在旧密码比对之后</b>：先确认调用方确实知道旧密码，
     * 再校验新密码是否合规——否则"旧密码错误"场景会被"新密码不合规"提前拦截，
     * 调用方拿到的错误信息会文不对题（且会误伤只想验证旧密码校验分支的调用方/测试）。
     *
     * <p>查询与写入都走 {@link SysUserMapper#selectSelfById(Long)}/
     * {@link SysUserMapper#updateSelfPassword(Long, String)}，绕开数据权限过滤——
     * 原因见这两个方法的类注释：自助改自己的密码不该受"我能看哪些人的数据"约束。
     */
    @Transactional(rollbackFor = Exception.class)
    public void changeOwnPassword(Long userId, String oldPassword, String newPassword) {
        if (!StringUtils.hasText(newPassword)) {
            throw new BizException(ResultCode.BAD_REQUEST, "新密码不能为空");
        }
        SysUser exist = getOwnById(userId);
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在: " + userId);
        }
        if (!passwordEncoder.matches(oldPassword == null ? "" : oldPassword, exist.getPassword())) {
            throw new BizException(ResultCode.AUTH_FAILED, "原密码不正确");
        }
        passwordPolicy.validate(newPassword, exist.getUsername());
        if (newPassword.equals(oldPassword)) {
            throw new BizException(ResultCode.BAD_REQUEST, "新密码不能与旧密码相同");
        }
        assertNotRecentlyUsed(userId, newPassword);
        String encoded = passwordEncoder.encode(newPassword);
        getBaseMapper().updateSelfPassword(userId, encoded);
        recordPasswordHistory(userId, encoded);
        tokenStore.revokeAllOf(userId);
    }

    /**
     * 密码历史校验：{@code sys.password.history-count > 0} 时，新密码不得命中该用户最近 N 条历史。
     * 关闭时（默认）直接放行，各设密码方法自身的"新≠当前旧"校验仍然生效。
     */
    private void assertNotRecentlyUsed(Long userId, String rawPassword) {
        int historyCount = passwordHistoryCount();
        if (userId == null || historyCount <= 0) {
            return;
        }
        for (String oldHash : passwordHistoryMapper.selectRecentHashes(userId, historyCount)) {
            if (passwordEncoder.matches(rawPassword, oldHash)) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "新密码不能与最近 " + historyCount + " 次使用过的密码相同");
            }
        }
    }

    /**
     * 记录一条历史密码。始终写入（关闭历史校验时也写，以便日后开启即有数据可比）；
     * 开启时顺带裁剪，只保留最近 N 条。
     */
    private void recordPasswordHistory(Long userId, String encodedHash) {
        SysUserPasswordHistory history = new SysUserPasswordHistory();
        history.setUserId(userId);
        history.setPasswordHash(encodedHash);
        history.setCreateTime(LocalDateTime.now());
        passwordHistoryMapper.insert(history);
        int historyCount = passwordHistoryCount();
        if (historyCount > 0) {
            passwordHistoryMapper.pruneToRecent(userId, historyCount);
        }
    }

    private int passwordHistoryCount() {
        String value = configService.getValue(CFG_HISTORY_COUNT, "0");
        try {
            return Math.max(Integer.parseInt(value.trim()), 0);
        } catch (NumberFormatException | NullPointerException e) {
            return 0;
        }
    }

    /**
     * 自助改资料（当前登录用户改自己的姓名/手机号/邮箱）。
     *
     * <p>用户名与角色不接受修改——本方法只接受 nickname/mobile/email 三个字段，
     * 调用方（{@code AuthController}）也只应该从这三个字段拼请求体，不给"改用户名/改角色"
     * 留任何入口。手机号/邮箱允许传空串/null 来清空，与 {@code SysUserController.update}
     * 对管理员编辑场景的既有行为一致。
     *
     * <p>查询与写入都走 {@link SysUserMapper#selectSelfById(Long)}/
     * {@link SysUserMapper#updateSelfProfile(Long, String, String, String)}，
     * 绕开数据权限过滤，原因同 {@link #changeOwnPassword(Long, String, String)}。
     *
     * <p><b>并发唯一性</b>：手机号/邮箱的校验+更新由 {@link UniqueGuard} 串行化。
     * 写入是单条 UPDATE，锁内即自动提交，不存在"锁释放了事务未提交"的窗口——
     * 因此这里<b>刻意不加</b> {@code @Transactional}：加了反而把提交推迟到锁外，
     * 正是 {@code UniqueGuard} 自己警告的那个窗口（下一个请求的校验读不到本次未提交的更新）。
     */
    public void updateOwnProfile(Long userId, String nickname, String mobile, String email) {
        if (!StringUtils.hasText(nickname)) {
            throw new BizException(ResultCode.BAD_REQUEST, "姓名不能为空");
        }
        withMobileEmailLock(mobile, email, () -> {
            // 锁内一律调不加锁的校验：两层都锁同一把键会必现"操作冲突"
            assertMobileEmailAvailable(userId, mobile, email);
            SysUser exist = getOwnById(userId);
            if (exist == null) {
                throw new BizException(ResultCode.NOT_FOUND, "用户不存在: " + userId);
            }
            getBaseMapper().updateSelfProfile(userId, nickname, mobile, email);
            return null;
        });
    }

    /**
     * 管理员编辑用户：手机号/邮箱唯一性校验与写入在<b>同一个临界区</b>内完成。
     *
     * <p>此前的写法是 controller 先调
     * {@link #assertMobileEmailAvailable(Long, String, String)}、再调
     * {@code updateById}——校验加的锁在校验返回时就释放了，写入完全裸奔，
     * 管理员编辑路径上的并发唯一性等于没有。锁必须包住写入才有意义。
     *
     * <p>写入是单条 UPDATE，锁内自动提交，不需要（也不应该加）{@code @Transactional}，
     * 理由同 {@link #updateOwnProfile}。
     *
     * @return 是否更新成功；{@code false} 表示记录不存在或乐观锁版本不匹配
     */
    public boolean updateWithUniqueCheck(Long id, SysUser entity) {
        entity.setId(id);
        return Boolean.TRUE.equals(withMobileEmailLock(entity.getMobile(), entity.getEmail(), () -> {
            assertMobileEmailAvailable(id, entity.getMobile(), entity.getEmail());
            return updateById(entity);
        }));
    }
    /** 重新授予角色：先清空再插入，是"重建"而非"增量修改"。 */
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds) {
        relationMapper.deleteUserRoles(userId);
        if (roleIds != null && !roleIds.isEmpty()) {
            relationMapper.insertUserRoles(userId, roleIds);
        }
    }

    public List<Long> roleIdsOf(Long userId) {
        return relationMapper.selectRoleIdsByUserId(userId);
    }
}
