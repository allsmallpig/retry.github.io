package com.mybatis.demo.intercter;

import com.alibaba.fastjson.JSON;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * @author ltz
 * @version V1.0
 * @date 2020/11/3 10:55
 * @Description 拦截参数的处理
 * @email goodmanalibaba@foxmail.com
 */
@Component
public class OsParameterHandler implements ParameterHandler {
    @Override
    public Object getParameterObject() {
        return null;
    }

    @Override
    public void setParameters(PreparedStatement ps) throws SQLException {
        String s = JSON.toJSONString(ps);
        System.out.println("s========= = " + s);


    }
}
