package com.bin.app.controller;

import com.alibaba.fastjson.JSONObject;
import com.bin.app.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author hongxubin
 * @date 2019/8/31-上午10:00
 */
@Controller
public class HelloController {

    @RequestMapping("/hellos")
    public String hello(Map<String,Object> map)
    {
        map.put("hello","你好");
        map.put("users", Arrays.asList("zhangsang","lisi","longwu"));
        return "test";
    }

//    @Autowired
    //private UserService userService;
//    @RequestMapping(value="/update")
//    @ResponseBody  //加上该注解表明该方法返回值均自动转为json格式传给前端

}
