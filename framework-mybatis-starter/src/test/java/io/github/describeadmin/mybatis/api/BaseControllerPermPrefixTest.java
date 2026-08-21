package io.github.describeadmin.mybatis.api;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.describeadmin.common.api.PermissionChecker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link BaseController} 权限点前缀推导与校验的单元测试。
 *
 * <p>这里刻意不起 Spring 上下文——推导逻辑是纯函数，值得用最快的方式覆盖它的边界。
 * 前缀推错的后果是"连 ADMIN 都被 403"，而错误信息里没有任何东西指向前缀，
 * 因此边界必须逐个钉死。
 */
@DisplayName("BaseController 权限点")
class BaseControllerPermPrefixTest {

    @Nested
    @DisplayName("前缀推导")
    class PrefixDerivation {

        @Test
        @DisplayName("系统管理路径推导结果与 seed-rbac.sql 里的权限点对得上")
        void systemPath() {
            assertThat(new SystemUserController().permPrefix()).isEqualTo("system:user");
        }

        @Test
        @DisplayName("单段业务路径推导为模块名，与 codegen 的 menu-*.sql 对得上")
        void singleSegmentPath() {
            assertThat(new ProjectController().permPrefix()).isEqualTo("project");
        }

        @Test
        @DisplayName("多余的前后斜杠不影响推导")
        void toleratesSlashes() {
            assertThat(new SloppySlashController().permPrefix()).isEqualTo("deep:nested:thing");
        }

        @Test
        @DisplayName("推导结果会被缓存，多次调用返回同一份")
        void resultIsCached() {
            ProjectController controller = new ProjectController();
            assertThat(controller.permPrefix()).isSameAs(controller.permPrefix());
        }

        @Test
        @DisplayName("推导不出前缀时抛出可操作的异常，而不是静默放行")
        void unresolvableFailsLoudly() {
            // 静默放行才是真正危险的：接口看起来正常工作，实际没有任何权限约束
            assertThatThrownBy(() -> new BareController().permPrefix())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("permPrefix")
                    .hasMessageContaining("BareController");
        }

        @Test
        @DisplayName("子类可以覆写推导结果")
        void overrideWins() {
            assertThat(new OverriddenController().permPrefix()).isEqualTo("custom:prefix");
        }
    }

    @Nested
    @DisplayName("权限校验")
    class Checking {

        @Test
        @DisplayName("未注入校验器时放行——此时应用本来就没有认证")
        void permitsAllWithoutChecker() {
            ProjectController controller = new ProjectController();
            // 不抛异常即为通过
            controller.requirePermission("remove");
        }

        @Test
        @DisplayName("动作被拼接到前缀之后，形成完整权限点")
        void composesFullPermissionCode() {
            RecordingChecker checker = new RecordingChecker();
            SystemUserController controller = new SystemUserController();
            controller.setPermissionChecker(checker);

            controller.requirePermission("add");
            controller.requirePermission("remove");

            assertThat(checker.asked).containsExactly("system:user:add", "system:user:remove");
        }
    }

    // ------------------------------------------------------------ 测试替身

    /** 只记录被问过哪些权限点，一律放行。 */
    private static final class RecordingChecker implements PermissionChecker {
        private final List<String> asked = new ArrayList<>();

        @Override
        public boolean hasPermission(String permission) {
            asked.add(permission);
            return true;
        }
    }

    @RequestMapping("/api/system/user")
    private static final class SystemUserController extends StubController {
    }

    @RequestMapping("/api/project")
    private static final class ProjectController extends StubController {
    }

    @RequestMapping("//api/deep/nested/thing/")
    private static final class SloppySlashController extends StubController {
    }

    /** 没有 {@code @RequestMapping}，推导必然失败。 */
    private static final class BareController extends StubController {
    }

    @RequestMapping("/api/whatever")
    private static final class OverriddenController extends StubController {
        @Override
        public String permPrefix() {
            return "custom:prefix";
        }
    }

    /**
     * 泛型参数在本组测试里无关紧要，用最小可编译的替身填充。
     */
    private abstract static class StubController
            extends BaseController<StubService, StubMapper, StubEntity> {
        @Override
        protected StubService getService() {
            throw new UnsupportedOperationException("本组测试不触达 Service");
        }
    }

    private static class StubEntity extends BaseEntity {
    }

    private interface StubMapper extends BaseMapper<StubEntity> {
    }

    private static class StubService extends BaseService<StubMapper, StubEntity> {
    }
}
