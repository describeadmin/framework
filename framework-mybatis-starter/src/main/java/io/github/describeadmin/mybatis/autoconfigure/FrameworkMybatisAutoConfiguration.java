package io.github.describeadmin.mybatis.autoconfigure;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import io.github.describeadmin.mybatis.core.AuditMetaObjectHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * framework-mybatis-starter 的自动配置。
 */
@AutoConfiguration
@ConditionalOnClass(MybatisPlusInterceptor.class)
@EnableConfigurationProperties(FrameworkMybatisProperties.class)
public class FrameworkMybatisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor(FrameworkMybatisProperties properties) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 分页：方言取自配置，不硬编码，便于切换到国产化数据库
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(properties.getDbType());
        pagination.setMaxLimit(properties.getMaxLimit());
        pagination.setOverflow(properties.isOverflow());
        interceptor.addInnerInterceptor(pagination);

        // 乐观锁，配合 BaseEntity 的 @Version
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        return interceptor;
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditMetaObjectHandler auditMetaObjectHandler() {
        return new AuditMetaObjectHandler();
    }
}
