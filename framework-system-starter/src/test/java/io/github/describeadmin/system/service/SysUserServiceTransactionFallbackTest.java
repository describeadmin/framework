package io.github.describeadmin.system.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

/**
 * {@code SysUserService} 构造期解析 {@code TransactionTemplate} 的降级路径。
 *
 * <p>这段降级是为"多数据源、多个 {@code TransactionManager} 且都没标 {@code @Primary}"
 * 的业务方准备的：拿不到模板就退化为无事务执行 + 一条 WARN，而不是拒绝启动。
 *
 * <p><b>本类存在的理由</b>：{@code ObjectProvider.getIfAvailable()} 在候选<b>多于一个</b>
 * 时会抛 {@code NoUniqueBeanDefinitionException}，而"多数据源各配一个
 * {@code TransactionTemplate}"恰恰是上面那批业务方最自然的写法——用它的话，
 * 降级逻辑会在它唯一该生效的场景里把启动打挂，且报的是一个不指向真因的 Bean 异常。
 * 必须是 {@code getIfUnique()}。
 */
@DisplayName("SysUserService 事务模板降级")
class SysUserServiceTransactionFallbackTest {

    @Test
    @DisplayName("容器里有多个 TransactionTemplate 时降级，而不是抛 NoUniqueBeanDefinitionException")
    void multipleTemplatesFallBackInsteadOfFailing() {
        ObjectProvider<TransactionTemplate> provider = providerWithTemplates(2);

        assertThatCode(() -> newService(provider))
                .as("多候选必须走降级；抛异常等于在降级唯一该生效的场景里把应用启动打挂")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("容器里一个 TransactionTemplate 都没有时同样降级")
    void noTemplateFallsBack() {
        assertThatCode(() -> newService(providerWithTemplates(0)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("容器里恰好一个时正常拿到，不走降级")
    void singleTemplateIsUsed() {
        ObjectProvider<TransactionTemplate> provider = providerWithTemplates(1);

        assertThat(provider.getIfUnique())
                .as("前置：唯一候选时 getIfUnique 必须返回它，否则本组测试证明不了任何事")
                .isNotNull();
        assertThatCode(() -> newService(provider)).doesNotThrowAnyException();
    }

    /**
     * 构造 {@code SysUserService}，除 provider 外的依赖一律传 {@code null}——
     * 构造函数只做字段赋值与本次要验证的 provider 解析，不触碰其余依赖。
     */
    private static SysUserService newService(ObjectProvider<TransactionTemplate> provider) {
        return new SysUserService(null, null, null, null, null, null, null, null, provider);
    }

    private static ObjectProvider<TransactionTemplate> providerWithTemplates(int count) {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        for (int i = 0; i < count; i++) {
            RootBeanDefinition definition = new RootBeanDefinition(TransactionTemplate.class);
            definition.getConstructorArgumentValues()
                    .addGenericArgumentValue(mock(PlatformTransactionManager.class));
            factory.registerBeanDefinition("transactionTemplate" + i, definition);
        }
        return factory.getBeanProvider(TransactionTemplate.class);
    }
}
