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
 * 开发种子管理员：库里没有任何用户时，创建一个管理员账号并<b>随机生成</b>口令，
 * BCrypt 入库，明文写到 {@code describeadmin.system.dev-seed.password-file} 并打印到启动日志。
 *
 * <p>取代了原先 {@code seed-rbac.sql} 里写死 {@code admin / admin123} 的那条 INSERT——
 * 静态 SQL 无法生成随机值 / 写文件，所以这一步必须程序化。
 *
 * <p>只在 {@code describeadmin.system.dev-seed.enabled=true} 时装配（见
 * {@code FrameworkSystemAutoConfiguration}），生产 profile 不打开即完全不存在。
 * 幂等：库里已有任何用户就整体跳过，不重新生成、不覆盖口令文件。
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
                    + "，seed-rbac.sql 应先建好它");
        }

        String rawPassword = RandomPasswordGenerator.generate(passwordPolicy, props.getAdminUsername());

        SysUser admin = new SysUser();
        admin.setUsername(props.getAdminUsername());
        admin.setNickname("超级管理员");
        admin.setStatus(1);
        SysUser created = userService.createUser(admin, rawPassword, List.of(adminRole.getId()));

        // createUser 会置 pwd_reset_required=1（管理员建号语义）。种子管理员用的是随机强口令，
        // 不需要再强制首次改密——Part 2 的强制改密只针对「管理员为别人建号 / 重置密码」。
        SysUser clearFlag = new SysUser();
        clearFlag.setId(created.getId());
        clearFlag.setVersion(created.getVersion());
        clearFlag.setPwdResetRequired(0);
        userService.updateById(clearFlag);

        writePasswordFile(rawPassword);
    }

    private void writePasswordFile(String rawPassword) {
        Path path = Path.of(props.getPasswordFile());
        if (!path.isAbsolute()) {
            path = Path.of(System.getProperty("user.dir")).resolve(props.getPasswordFile());
        }
        try {
            Files.writeString(path, rawPassword + System.lineSeparator(), StandardCharsets.UTF_8);
            tryRestrictPermissions(path);
        } catch (IOException e) {
            log.warn("dev-seed: 写口令文件 {} 失败：{}", path.toAbsolutePath(), e.toString());
        }
        log.warn("========================================================================");
        log.warn(" dev-seed 生成初始管理员：{} / {}", props.getAdminUsername(), rawPassword);
        log.warn(" 明文口令已写入：{}", path.toAbsolutePath());
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
