package com.winteree.core.dao;

import com.winteree.core.dao.entity.RoleDO;
import com.winteree.core.dao.entity.RoleDOExample;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface RoleDOMapper {
    long countByExample(RoleDOExample example);

    int deleteByExample(RoleDOExample example);

    int deleteByPrimaryKey(Long id);

    int insert(RoleDO record);

    int insertSelective(RoleDO record);

    List<RoleDO> selectByExample(RoleDOExample example);

    RoleDO selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("record") RoleDO record, @Param("example") RoleDOExample example);

    int updateByExample(@Param("record") RoleDO record, @Param("example") RoleDOExample example);

    int updateByPrimaryKeySelective(RoleDO record);

    int updateByPrimaryKey(RoleDO record);
}