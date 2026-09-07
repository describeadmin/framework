package io.github.describeadmin.it;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 集成测试宿主应用。
 *
 * <p>以真实业务方姿态消费框架：只引 starter，一行框架内部装配都不碰。
 * {@code fixture} 包下有一个最小业务模块（project），用来驱动 {@code BaseEntity} /
 * {@code BaseService} / {@code BaseController} 的运行时行为；系统管理（用户/角色/菜单/
 * 部门/字典/参数/操作日志/数据权限）由 framework-system-starter 提供，测试里一行不写。
 */
@SpringBootApplication
@MapperScan("io.github.describeadmin.it.fixture")
public class ItApplication {
    public static void main(String[] args) {
        SpringApplication.run(ItApplication.class, args);
    }
}
