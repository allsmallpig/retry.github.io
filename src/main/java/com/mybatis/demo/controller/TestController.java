package com.mybatis.demo.controller;

import com.mybatis.demo.mapper.TestMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * @author ltz
 * @version V1.0
 * @date 2020/11/3 14:21
 * @Description
 * @email goodmanalibaba@foxmail.com
 */

@RestController
@RequestMapping("/a")
public class TestController {

    @Resource
    private TestMapper testMapper;

    @GetMapping("/ass")
    public void test() {
        System.out.println("testMapper = ");
        testMapper.insert();
    }
}
