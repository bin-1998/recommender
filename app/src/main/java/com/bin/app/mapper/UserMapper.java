package com.bin.app.mapper;

import com.bin.app.entity.User;
import org.springframework.stereotype.Repository;

/**
 * @author hongxubin
 * @date 2019/10/11-下午9:53
 */
@Repository
public interface UserMapper {
    User Sel(int id);
}
