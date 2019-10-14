package com.bin.apps.mapper;

import com.bin.apps.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

/**
 * @author hongxubin
 * @date 2019/10/11-下午9:53
 */
@Mapper
public interface UserMapper {
    User Sel(int id);
}
