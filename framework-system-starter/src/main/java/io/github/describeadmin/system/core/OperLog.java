package io.github.describeadmin.system.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 显式声明一个自定义端点参与操作日志记录。
 *
 * <p>{@code BaseController} 的 create/update/delete 三个通用端点已经被
 * {@code OperLogAspect} 自动记录，不需要标注；本注解只用于业务/框架自己写的
 * <b>自定义</b>写端点（如 {@code SysUserController.resetPassword}）——
 * 与自定义端点用 {@code @PreAuthorize} 显式声明权限点是同一套心智模型：
 * 框架托底通用路径，自定义路径显式声明。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OperLog {

    /** 模块，如 {@code system:user}，通常与该端点的权限点前缀一致。 */
    String module();

    /** 操作描述，如"重置密码"。 */
    String description();
}
