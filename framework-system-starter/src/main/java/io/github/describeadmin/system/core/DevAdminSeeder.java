package io.github.describeadmin.system.core;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.describeadmin.security.api.PasswordPolicy;
import io.github.describeadmin.system.autoconfigure.FrameworkSystemProperties;
import io.github.describeadmin.system.entity.SysRole;
import io.github.describeadmin.system.entity.SysUser;
import io.github.describeadmin.system.service.SysRoleService;
import io.github.describeadmin.system.service.SysUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

/**
 * 开发种子管理员：库里没有任何用户时，创建一个管理员账号，BCrypt 入库。
 *
 * <p>口令来源：{@code describeadmin.system.dev-seed.password-file} 已存在且非空时<b>复用</b>其中的口令
 * （重建本地库后 {@code .passwd}、前端 e2e 等处保存的口令仍然有效），否则<b>随机生成</b>，
 * 明文写到该文件并打印到启动日志。
 *
 * <p>取代了原先 {@code seed-rbac.sql}（现 {@code 0002-rbac-seed.sql}）里写死 {@code admin / admin123} 的那条 INSERT——
 * 静态 SQL 无法生成随机值 / 写文件，所以这一步必须程序化。
 *
 * <p>只在 {@code describeadmin.system.dev-seed.enabled=true} 时装配（见
 * {@code FrameworkSystemAutoConfiguration}），生产 profile 不打开即完全不存在。
 * 幂等：库里已有任何用户就整体跳过，不读、不写口令文件。
 */
public class DevAdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevAdminSeeder.class);

    private final SysUserService userService;
    private final SysRoleService roleService;
    private final PasswordPolicy passwordPolicy;
    private final FrameworkSystemProperties.DevSeed props;

    public DevAdminSeeder(SysUserService userService, SysRoleService roleService,
                          PasswordPolicy passwordPolicy, FrameworkSystemProperties.DevSeed props) {
        this.userService = userService;
        this.roleService = roleService;
        this.passwordPolicy = passwordPolicy;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userService.count() > 0) {
            log.debug("dev-seed: 库中已有用户，跳过管理员播种");
            return;
        }

        SysRole adminRole = roleService.getOne(
                new QueryWrapper<SysRole>().eq("role_code", props.getAdminRoleCode()), false);
        if (adminRole == null) {
            throw new IllegalStateException("dev-seed: 找不到角色 role_code=" + props.getAdminRoleCode()
                    + "，0002-rbac-seed.sql 应先建好它");
        }

        Path passwordFile = resolvePasswordFile();
        String reused = readReusablePassword(passwordFile, passwordPolicy, props.getAdminUsername());
        String rawPassword = reused != null
                ? reused
                : RandomPasswordGenerator.generate(passwordPolicy, props.getAdminUsername());

        SysUser admin = new SysUser();
        admin.setUsername(props.getAdminUsername());
        admin.setNickname("超级管理员");
        admin.setStatus(1);
        SysUser created = userService.createUser(admin, rawPassword, List.of(adminRole.getId()));

        // createUser 会置 pwd_reset_required=1（管理员建号语义）。种子管理员的口令要么随机生成、
        // 要么是开发者自己放进口令文件的，不需要再强制首次改密——Part 2 的强制改密只针对「管理员为别人建号 / 重置密码」。
        SysUser clearFlag = new SysUser();
        clearFlag.setId(created.getId());
        clearFlag.setVersion(created.getVersion());
        clearFlag.setPwdResetRequired(0);
        userService.updateById(clearFlag);

        if (reused != null) {
            log.warn("dev-seed 创建初始管理员 {}，口令复用自已有的 {}（仅供本地开发，切勿用于生产）",
                    props.getAdminUsername(), passwordFile.toAbsolutePath());
        } else {
            writePasswordFile(passwordFile, rawPassword);
        }
    }

    /**
     * 读取口令文件中可复用的口令：文件不存在或内容为空时返回 {@code null}（由调用方随机生成）。
     *
     * <p>内容首尾空白会被去掉（编辑器补的换行），因此口令本身不能以空白开头或结尾。
     * 文件读不出来、或口令不满足 {@link PasswordPolicy} 时<b>拒绝启动</b>而不是改为随机生成——
     * 那样会悄悄覆盖掉开发者自己放进去的口令，而 {@code .passwd} 看起来还是"在的"。
     */
    static String readReusablePassword(Path file, PasswordPolicy policy, String username) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new IllegalStateException("dev-seed: 读取口令文件 " + file.toAbsolutePath() + " 失败", e);
        }
        if (content.isEmpty()) {
            return null;
        }
        try {
            policy.validate(content, username);
        } catch (RuntimeException policyRejected) {
            throw new IllegalStateException("dev-seed: 口令文件 " + file.toAbsolutePath()
                    + " 中的口令不满足 PasswordPolicy（" + policyRejected.getMessage()
                    + "）。改成合规的口令，或删掉该文件让 dev-seed 随机生成", policyRejected);
        }
        return content;
    }

    private Path resolvePasswordFile() {
        Path path = Path.of(props.getPasswordFile());
        if (!path.isAbsolute()) {
            path = Path.of(System.getProperty("user.dir")).resolve(props.getPasswordFile());
        }
        return path;
    }

    private void writePasswordFile(Path path, String rawPassword) {
        boolean written;
        try {
            Files.writeString(path, rawPassword + System.lineSeparator(), StandardCharsets.UTF_8);
            tryRestrictPermissions(path);
            written = true;
        } catch (IOException e) {
            log.warn("dev-seed: 写口令文件 {} 失败：{}", path.toAbsolutePath(), e.toString());
            written = false;
        }
        log.warn("========================================================================");
        log.warn(" dev-seed 生成初始管理员：{} / {}", props.getAdminUsername(), rawPassword);
        if (written) {
            log.warn(" 明文口令已写入：{}", path.toAbsolutePath());
        } else {
            log.warn(" 口令文件未写入，以本条日志为准");
        }
        log.warn(" 仅供本地开发，切勿用于生产。");
        log.warn("========================================================================");
    }

    private static void tryRestrictPermissions(Path path) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows / 非 POSIX 文件系统：跳过
        }
    }
}
