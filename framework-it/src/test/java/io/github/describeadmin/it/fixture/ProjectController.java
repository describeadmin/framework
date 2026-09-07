package io.github.describeadmin.it.fixture;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.describeadmin.mybatis.api.BaseController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 集成测试用的业务 Controller，形态与 codegen 生成物一致。
 *
 * <p>显式覆写 {@link #permPrefix()}，与 {@code menu-biz_project.sql} 登记的
 * {@code project:*} 权限点对齐——{@code PermissionEnforcementIT} 依赖这一点验证
 * 「推导前缀与授权数据不一致会导致连 ADMIN 都 403」。
 */
@RestController
@RequestMapping("/api/project")
public class ProjectController extends BaseController<ProjectService, ProjectMapper, ProjectEntity> {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Override
    protected ProjectService getService() {
        return projectService;
    }

    @Override
    public String permPrefix() {
        return "project";
    }

    @Override
    protected Wrapper<ProjectEntity> buildListWrapper(Map<String, String> params) {
        QueryWrapper<ProjectEntity> wrapper = new QueryWrapper<>();
        wrapper.likeRight(text(params, "projectName") != null, "project_name", text(params, "projectName"));
        wrapper.eq(text(params, "projectCode") != null, "project_code", text(params, "projectCode"));
        wrapper.eq(asLong(params, "ownerDeptId") != null, "owner_dept_id", asLong(params, "ownerDeptId"));
        wrapper.ge(asDate(params, "startDateStart") != null, "start_date", asDate(params, "startDateStart"));
        wrapper.le(asDate(params, "startDateEnd") != null, "start_date", asDate(params, "startDateEnd"));
        wrapper.eq(asInt(params, "status") != null, "status", asInt(params, "status"));
        return wrapper;
    }
}
