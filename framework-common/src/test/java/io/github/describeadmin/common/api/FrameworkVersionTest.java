package io.github.describeadmin.common.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link FrameworkVersion} 的单元测试。
 */
@DisplayName("框架版本与插件兼容性自检")
class FrameworkVersionTest {

    @Nested
    @DisplayName("版本读取")
    class Reading {

        @Test
        @DisplayName("能读到真实版本号，而不是 unknown")
        void readsActualVersion() {
            // 这条同时守住了两件事：资源文件存在，且 Maven 的资源过滤确实生效。
            // 过滤没生效时读到的是字面量 ${project.version}，比 unknown 更有欺骗性
            assertThat(FrameworkVersion.current())
                    .isNotEqualTo(FrameworkVersion.UNKNOWN)
                    .doesNotContain("${")
                    .matches("\\d+\\.\\d+\\.\\d+.*");
        }

        @Test
        @DisplayName("在测试环境（类来自 target/classes 而非 jar）下同样读得到")
        void worksOutsideJar() {
            // 本用例跑的时候类就来自 target/classes。用 MANIFEST 方案时这里必然是 unknown，
            // 那意味着自检在开发期永远失效——而开发期恰恰最该发现版本不匹配
            assertThat(FrameworkVersion.current()).isNotEqualTo(FrameworkVersion.UNKNOWN);
        }
    }

    @Nested
    @DisplayName("兼容性判定")
    class Compatibility {

        @Test
        @DisplayName("框架版本与要求一致时放行")
        void sameVersionPasses() {
            assertThatNoException().isThrownBy(
                    () -> FrameworkVersion.requireCompatible("test-plugin", FrameworkVersion.current()));
        }

        @Test
        @DisplayName("框架比插件要求的旧时启动失败，且错误信息说得清怎么办")
        void olderFrameworkFails() {
            assertThatThrownBy(() -> FrameworkVersion.requireCompatible("test-plugin", "99.0.0"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("test-plugin")
                    .hasMessageContaining("99.0.0")
                    // 错误信息必须带上"现在是多少"，否则使用者还得自己去翻
                    .hasMessageContaining(FrameworkVersion.current());
        }

        @Test
        @DisplayName("主版本不同时启动失败——跨大版本的破坏性变更是被允许的，不能假设兼容")
        void differentMajorFails() {
            assertThatThrownBy(() -> FrameworkVersion.requireCompatible("test-plugin", "1.0.0"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("主版本不同");
        }

        @Test
        @DisplayName("框架更新但主版本相同时放行，不阻断")
        void newerFrameworkPasses() {
            // 一律拒绝会让每个框架小版本都逼所有插件重新发一遍
            assertThatNoException().isThrownBy(
                    () -> FrameworkVersion.requireCompatible("test-plugin", "0.0.1"));
        }

        @Test
        @DisplayName("版本号无法解析时放行，不因为自检机制本身把应用挡在门外")
        void unparsableVersionPasses() {
            assertThatNoException().isThrownBy(
                    () -> FrameworkVersion.requireCompatible("test-plugin", "不是版本号"));
            assertThatNoException().isThrownBy(
                    () -> FrameworkVersion.requireCompatible("test-plugin", null));
        }

        @Test
        @DisplayName("SNAPSHOT 后缀不影响判定")
        void snapshotSuffixIgnored() {
            assertThatNoException().isThrownBy(
                    () -> FrameworkVersion.requireCompatible("test-plugin", "0.0.1-SNAPSHOT"));
            assertThatThrownBy(() -> FrameworkVersion.requireCompatible("test-plugin", "99.0.0-SNAPSHOT"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
