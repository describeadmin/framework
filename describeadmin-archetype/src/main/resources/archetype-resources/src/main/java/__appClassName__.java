package ${package};

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用入口。
 */
@SpringBootApplication
// 只扫描你自己的 Mapper。框架的系统管理 Mapper 由 framework-system-starter 的自动配置
// 自行登记扫描路径，你不需要（也不应该）把框架的包写进来。
// 反过来这一行【必须有】—— 一旦工程里存在 @MapperScan，MyBatis 的自动扫描就不再生效，
// 不写的话扫不到的是你自己的 Mapper。
@MapperScan("${package}.**.mapper")
public class ${appClassName} {

    public static void main(String[] args) {
        SpringApplication.run(${appClassName}.class, args);
    }
}
