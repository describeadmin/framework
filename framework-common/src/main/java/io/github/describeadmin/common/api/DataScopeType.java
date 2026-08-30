package io.github.describeadmin.common.api;

/**
 * 数据权限范围。
 *
 * <p>挂在角色（{@code sys_role.data_scope}）上，与角色本身的菜单/按钮权限是两回事——
 * 前者管"看得到哪些数据行"，后者管"能点哪些功能"。
 *
 * <p>一个用户身兼多角色时如何合并为单一有效范围，是 framework-system-starter 的
 * {@code DataScopeResolver}（业务逻辑，非兼容性承诺范围）要处理的问题，
 * <b>不由本枚举的声明顺序或 {@link #ordinal()} 决定</b>——两者是分开的关注点，
 * 混在一起会让"改一下枚举顺序"变成一次隐蔽的合并策略变更。
 *
 * <p>{@code code} 取值与若依（RuoYi）等同类框架的既有约定一致，降低本框架使用者的
 * 认知负担。
 *
 * <p>本类型位于 {@code api} 包下，属于兼容性承诺范围。
 */
public enum DataScopeType {

    /** 全部数据，不过滤。 */
    ALL(1),
    /** 自定义部门列表，具体部门见 {@code sys_role_dept}。 */
    CUSTOM(2),
    /** 仅本部门。 */
    DEPT(3),
    /** 本部门及其下级部门。 */
    DEPT_AND_CHILD(4),
    /** 仅本人创建的数据。 */
    SELF(5);

    private final int code;

    DataScopeType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static DataScopeType ofCode(int code) {
        for (DataScopeType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的数据权限范围代码: " + code);
    }
}
