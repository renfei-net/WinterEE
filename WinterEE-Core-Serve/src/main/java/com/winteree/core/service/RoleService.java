package com.winteree.core.service;

import com.winteree.api.entity.DataScopeEnum;
import com.winteree.api.entity.RoleDTO;
import com.winteree.api.exception.FailureException;
import com.winteree.api.exception.ForbiddenException;
import com.winteree.core.dao.entity.RoleDO;

import java.util.List;

/**
 * <p>Title: RoleService</p>
 * <p>Description: 角色服务</p>
 *
 * @author RenFei
 * @date : 2020-04-20 13:48
 */
public interface RoleService {

    /**
     * 根据UUID获取角色对象
     *
     * @param uuid UUID
     * @return 角色对象
     */
    RoleDO getRoleByUuid(String uuid);

    /**
     * 获取角色列表
     *
     * @param tenantUuid 租户ID
     * @return 角色列表
     */
    List<RoleDTO> getRoleList(String tenantUuid);

    /**
     * 添加角色
     *
     * @param roleDTO 角色信息传输对象
     * @return 结果
     */
    int addRole(RoleDTO roleDTO) throws ForbiddenException, FailureException;

    /**
     * 修改角色
     *
     * @param roleDTO 角色传输对象
     * @return 结果
     */
    int updateRole(RoleDTO roleDTO) throws ForbiddenException, FailureException;

    /**
     * 删除角色
     *
     * @param uuid 角色ID
     * @return 删除结果
     */
    int deleteRole(String uuid) throws ForbiddenException, FailureException;

    /**
     * 根据角色ID列表获取菜单UUID列表
     *
     * @param roleUuid 角色ID列表
     * @return
     */
    List<String> getMenuUuidByRoleUuid(List<String> roleUuid);

    /**
     * 获取数据权限范围（取最大值）
     *
     * @return 数据权限范围
     */
    DataScopeEnum getDataScope();

    /**
     * 获取数据权限范围（取最大值）
     *
     * @param accountUuid 账户UUID
     * @return
     */
    DataScopeEnum getDataScope(String accountUuid);

    /**
     * 根据用户UUID获取角色列表UUID
     *
     * @param userUuid 用户UUID
     * @return 角色列表UUID
     */
    List<String> selectRoleUuidByUserUuid(String userUuid);
}
