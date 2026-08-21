package io.github.describeadmin.storage.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * framework-storage-starter 的配置项，前缀 {@code describeadmin.storage}。
 */
@ConfigurationProperties(prefix = "describeadmin.storage")
public class FrameworkStorageProperties {

    private final Local local = new Local();

    public Local getLocal() {
        return local;
    }

    /** 框架内置的本地磁盘实现专用配置；换成集中式实现（如 OSS 插件）后本组配置被忽略。 */
    public static class Local {

        /**
         * 存储根目录。
         *
         * <p>相对路径的解析基准是进程启动时的工作目录（JVM working directory）——
         * 这在容器化部署下不可预测（取决于容器入口脚本、systemd unit 等如何启动进程）。
         * <b>生产环境务必显式配置为绝对路径</b>，不要依赖这个默认值。
         */
        private String rootDir = "./storage-data";

        /** 拼接到 {@link io.github.describeadmin.storage.api.StorageProvider#url} 返回值前的前缀。 */
        private String urlPrefix = "/storage/";

        /** 同 key 重复写入时是否允许覆盖；关闭后对已存在的 key 再次 put 会抛异常。 */
        private boolean overwriteExisting = true;

        public String getRootDir() {
            return rootDir;
        }

        public void setRootDir(String rootDir) {
            this.rootDir = rootDir;
        }

        public String getUrlPrefix() {
            return urlPrefix;
        }

        public void setUrlPrefix(String urlPrefix) {
            this.urlPrefix = urlPrefix;
        }

        public boolean isOverwriteExisting() {
            return overwriteExisting;
        }

        public void setOverwriteExisting(boolean overwriteExisting) {
            this.overwriteExisting = overwriteExisting;
        }
    }
}
