package io.github.describeadmin.common.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 框架版本，以及插件的兼容性自检。
 *
 * <p><b>解决什么问题</b>：插件以 {@code provided} 依赖框架，<b>运行时的框架版本由业务方决定</b>，
 * 不是插件构建时的那个。两个方向的风险极不对称：
 * <ul>
 *   <li>插件旧、框架新 —— 通常没事，只要框架没破坏 {@code api/}</li>
 *   <li><b>插件新、框架旧 —— {@code NoSuchMethodError} / {@code NoClassDefFoundError}</b>，
 *       而且不在启动时暴露，要等到某个具体请求走到那行代码才炸</li>
 * </ul>
 * 本类的作用就是把后一种情况提前到启动阶段，并给出一句能照着做的话。
 *
 * <p><b>版本号为什么放资源文件而不是 MANIFEST</b>：{@code Package.getImplementationVersion()}
 * 只在从 jar 运行时有值，在 IDE 里跑、在 surefire 里跑都是 {@code null}
 * （那时类来自 {@code target/classes} 而不是 jar）。那会让自检在开发期永远失效，
 * 而开发期恰恰是最该发现版本不匹配的时候。资源文件在两种情形下都读得到。
 *
 * <p>本类位于 {@code api} 包下，属于兼容性承诺范围——它本身就是插件要依赖的契约。
 */
public final class FrameworkVersion {

    private static final Logger log = LoggerFactory.getLogger(FrameworkVersion.class);

    private static final String RESOURCE = "META-INF/describeadmin-framework.properties";

    /** 读不到版本时的取值。 */
    public static final String UNKNOWN = "unknown";

    private static final String CURRENT = load();

    private FrameworkVersion() {
    }

    /**
     * @return 当前运行的框架版本，如 {@code 0.2.0} 或 {@code 0.2.0-SNAPSHOT}；
     *         读不到时返回 {@link #UNKNOWN}
     */
    public static String current() {
        return CURRENT;
    }

    /**
     * 插件在自动配置中调用，声明自己构建时依赖的框架版本。
     *
     * <p>处置方式按风险区别对待，不搞"一律拒绝"：
     * <ul>
     *   <li><b>框架比要求的旧 → 抛异常，启动失败。</b>这是真正会出事的方向，
     *       让它在启动阶段以一句可读的信息失败，远好过运行期某个请求里的 NoSuchMethodError</li>
     *   <li><b>主版本不同 → 抛异常。</b>跨大版本的破坏性变更按 SemVer 是被允许的，
     *       不能假设还兼容</li>
     *   <li><b>框架更新但主版本相同 → 只记 WARN，不阻断。</b>一律拒绝会让每个框架小版本
     *       都逼所有插件重新发一遍，而这类组合大多数时候是好的。
     *       但 <b>0.x 期间这条 WARN 要当回事</b>——SemVer 对 0.x 不作保证，
     *       本项目的 0.2.0 就带过 Breaking Change</li>
     * </ul>
     *
     * <p>读不到框架版本时放行并记 WARN：宁可漏报，不可因为自检机制本身把应用挡在门外。
     *
     * @param pluginName    插件名，用于错误信息，如 {@code framework-cache-redis-starter}
     * @param requiredSince 插件要求的最低框架版本，通常就是它构建时依赖的版本
     * @throws IllegalStateException 版本不兼容时抛出，使应用启动失败
     */
    public static void requireCompatible(String pluginName, String requiredSince) {
        String current = current();
        if (UNKNOWN.equals(current)) {
            log.warn("无法确定框架版本，跳过 {} 的兼容性自检。"
                    + "若 framework-common 被重新打包过，请确认 {} 未被剔除",
                    pluginName, RESOURCE);
            return;
        }

        int[] running = parse(current);
        int[] required = parse(requiredSince);
        if (running == null || required == null) {
            log.warn("版本号无法解析（运行中={}, 要求={}），跳过 {} 的兼容性自检",
                    current, requiredSince, pluginName);
            return;
        }

        if (running[0] != required[0]) {
            throw new IllegalStateException(String.format(
                    "%s 与当前框架版本不兼容：插件要求 %s，实际运行 %s，主版本不同。"
                            + "请把插件升级到与框架同一主版本的版本。",
                    pluginName, requiredSince, current));
        }
        if (compare(running, required) < 0) {
            throw new IllegalStateException(String.format(
                    "%s 与当前框架版本不兼容：插件要求 %s 或更高，实际运行 %s。"
                            + "请把 describeadmin.version 升到 %s 以上，或改用为 %s 构建的插件版本。",
                    pluginName, requiredSince, current, requiredSince, current));
        }
        if (running[0] == 0 && running[1] != required[1]) {
            log.warn("{} 是为框架 {} 构建的，当前运行 {}。"
                    + "0.x 期间 SemVer 不保证小版本兼容，请确认插件已针对该版本验证过",
                    pluginName, requiredSince, current);
        }
    }

    private static String load() {
        try (InputStream in = FrameworkVersion.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return UNKNOWN;
            }
            Properties properties = new Properties();
            properties.load(in);
            String value = properties.getProperty("version");
            // 资源过滤没生效时读到的是未展开的 ${project.version} 字面量，
            // 那种值比 unknown 更有欺骗性，必须识别出来
            if (value == null || value.isBlank() || value.startsWith("${")) {
                return UNKNOWN;
            }
            return value.trim();
        } catch (IOException e) {
            return UNKNOWN;
        }
    }

    /**
     * 解析出 major/minor/patch，忽略 {@code -SNAPSHOT} 之类的后缀。
     *
     * @return 三元组；无法解析时返回 {@code null}
     */
    private static int[] parse(String version) {
        if (version == null || version.isBlank()) {
            return null;
        }
        String core = version.trim();
        int dash = core.indexOf('-');
        if (dash >= 0) {
            core = core.substring(0, dash);
        }
        String[] parts = core.split("\\.");
        int[] result = {0, 0, 0};
        try {
            for (int i = 0; i < Math.min(3, parts.length); i++) {
                result[i] = Integer.parseInt(parts[i]);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return result;
    }

    private static int compare(int[] left, int[] right) {
        for (int i = 0; i < 3; i++) {
            if (left[i] != right[i]) {
                return Integer.compare(left[i], right[i]);
            }
        }
        return 0;
    }
}
