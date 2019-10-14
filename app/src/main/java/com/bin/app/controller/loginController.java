package com.bin.app.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.thymeleaf.util.StringUtils;

import javax.servlet.http.HttpSession;
import java.util.Map;

/**
 * @author hongxubin
 * @date 2019/9/3-下午10:00
 */
@Controller
public class loginController {
    @PostMapping("/user/login")  //表示接收　ｐｏｓｔ方法的数据
    public String login(@RequestParam("username") String username,
                        @RequestParam("password") String password,
                        Map<String,Object> map,
                        HttpSession session)
    {
        if(!StringUtils.isEmpty(username)&&"123456".equals(password))
        {  //登录成功,防止表单重复提交,可以重定向到主页
            session.setAttribute("loginUser",username);
        return "redirect:/main.html";}
        else {
            String message="账号为空或密码输入错误";
            map.put("msg",message);
            return "index";
        }
    }
}
