package io.github.describeadmin.system.core;

import io.github.describeadmin.security.core.DefaultPasswordPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link DevAdminSeeder#readReusablePassword} 的单元测试：库里没有用户时，口令文件里的口令能否复用。
 */
@DisplayName("dev-seed 复用已有口令文件")
class DevAdminSeederTest {

    /** 与框架默认口径一致：8 位 + 至少 3 类字符。 */
    private final DefaultPasswordPolicy policy = new DefaultPasswordPolicy(true, 8, 3);

    @TempDir
    Path dir;

    @Test
    @DisplayName("文件不存在时返回 null，由调用方随机生成")
    void missingFileYieldsNull() {
        assertThat(DevAdminSeeder.readReusablePassword(dir.resolve(".passwd"), policy, "admin")).isNull();
    }

    @Test
    @DisplayName("文件只有空白时返回 null")
    void blankFileYieldsNull() throws Exception {
        Path file = Files.writeString(dir.resolve(".passwd"), "  \n", StandardCharsets.UTF_8);
        assertThat(DevAdminSeeder.readReusablePassword(file, policy, "admin")).isNull();
    }

    @Test
    @DisplayName("复用文件中的口令，去掉首尾空白（含 CRLF）")
    void reusesStrippedContent() throws Exception {
        Path file = Files.writeString(dir.resolve(".passwd"), "Abc#2345xyz\r\n", StandardCharsets.UTF_8);
        assertThat(DevAdminSeeder.readReusablePassword(file, policy, "admin")).isEqualTo("Abc#2345xyz");
    }

    @Test
    @DisplayName("口令不满足 PasswordPolicy 时拒绝启动，不悄悄改为随机生成")
    void rejectsNonCompliantPassword() throws Exception {
        Path file = Files.writeString(dir.resolve(".passwd"), "admin123", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> DevAdminSeeder.readReusablePassword(file, policy, "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不满足 PasswordPolicy");
    }
}
