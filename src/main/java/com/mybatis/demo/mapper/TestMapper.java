package com.mybatis.demo.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author ltz
 * @version V1.0
 * @date 2020/11/3 10:28
 * @Description
 * @email goodmanalibaba@foxmail.com
 */
@Mapper
public interface TestMapper {
    @Insert("INSERT test(name)  VALUES(\"dfg\")")
    void insert();
}
