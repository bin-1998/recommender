package com.bin.apps.controller;

import com.alibaba.fastjson.JSONObject;
import com.bin.apps.entity.User;
import com.bin.apps.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * @author hongxubin
 * @date 2019/10/12-上午11:29
 */
@Controller
public class LoginController {
    @Autowired
    UserMapper userMapper;

    @RequestMapping("/ha")
    public String osd()
    {
        return "test";
    }
    @RequestMapping("/he")
    @ResponseBody
    public Object update(@RequestBody JSONObject params){
        //@RequestBody只能给方法的参数注解，表明一次接收到请求参数
        //JSONObject为alibaba的fastjson包下类，推荐使用。另外也可以使用String来接收前端的json格式传参。
        Integer id = params.getInteger("userId");
        String name = params.getString("username");
        //以上就是获取到参数体内id和name。后面再加上自己的业务逻辑即可。如果是dubbo方式的consumer，请将service注入进来，直接调用即可
        Map<String,Object> map = new HashMap<>();
        map.put("success",true);
        map.put("message",userMapper.Sel(1));
        return map;
    }
}
