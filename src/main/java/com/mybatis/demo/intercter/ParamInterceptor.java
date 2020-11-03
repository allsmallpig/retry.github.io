package com.mybatis.demo.intercter;

/**
 * @author ltz
 * @version V1.0
 * @date 2020/11/3 14:32
 * @Description
 * @email goodmanalibaba@foxmail.com
 */

import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.RowBounds;
import org.jooq.tools.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * User : liulu
 * Date : 2017/12/1 11:30
 * Des https://retry.blog.csdn.net/article/details/109462687 参考
 * version $Id: ParamInterceptor.java, v 0.1 Exp $
 *
 * MyBatis 允许你在已映射语句执行过程中的某一点进行拦截调用。默认情况下，MyBatis 允许使用插件来拦截的方法调用包括：
 *
 * Executor (update, query, flushStatements, commit, rollback, getTransaction, close, isClosed)
 * ParameterHandler (getParameterObject, setParameters)
 * ResultSetHandler (handleResultSets, handleOutputParameters)
 * StatementHandler (prepare, parameterize, batch, update, query)
 * 我们看到了可以拦截Executor接口的部分方法，比如update，query，commit，rollback等方法，还有其他接口的一些方法等。
 *
 * 总体概括为：
 *
 * 拦截执行器的方法
 * 拦截参数的处理
 * 拦截结果集的处理
 * 拦截Sql语法构建的处理
 */
@Component
@Intercepts({
        @Signature(type = ParameterHandler.class, method = "setParameters", args = PreparedStatement.class),
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "createCacheKey", args = {MappedStatement.class, Object.class, RowBounds.class, BoundSql.class})
}
)
public class ParamInterceptor implements Interceptor {

    private static final String PARAM_KEY = "tenantId";

    @Override
    public Object intercept(Invocation invocation) throws Throwable {

        // 拦截 Executor 的 update 方法 生成sql前将 tenantId 设置到实体中
        if (invocation.getTarget() instanceof Executor && invocation.getArgs().length == 2) {
            return invokeUpdate(invocation);
        }
        // 拦截 Executor 的 createCacheKey 方法，pageHelper插件会拦截 query 方法，调用此方法，提前将参数设置到参数集合中
        if (invocation.getTarget() instanceof Executor && invocation.getArgs().length == 4) {
            return invokeCacheKey(invocation);
        }
        //拦截 ParameterHandler 的 setParameters 方法 动态设置参数
        if (invocation.getTarget() instanceof ParameterHandler) {
            return invokeSetParameter(invocation);
        }
        return null;
    }

    private Object invokeCacheKey(Invocation invocation) throws Exception {
        Executor executor = (Executor) invocation.getTarget();
        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        if (ms.getSqlCommandType() != SqlCommandType.SELECT) {
            return invocation.proceed();
        }

        Object parameterObject = invocation.getArgs()[1];
        RowBounds rowBounds = (RowBounds) invocation.getArgs()[2];
        BoundSql boundSql = (BoundSql) invocation.getArgs()[3];

        List<String> paramNames = new ArrayList<>();
        boolean hasKey = hasParamKey(paramNames, boundSql.getParameterMappings());

        if (!hasKey) {
            return invocation.proceed();
        }
        // 改写参数
        parameterObject = processSingle(parameterObject, paramNames);

        return executor.createCacheKey(ms, parameterObject, rowBounds, boundSql);
    }

    private Object invokeUpdate(Invocation invocation) throws Exception {
        Executor executor = (Executor) invocation.getTarget();
        // 获取第一个参数
        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        // 非 insert 语句 不处理
        if (ms.getSqlCommandType() != SqlCommandType.INSERT) {
            return invocation.proceed();
        }
        // mybatis的参数对象
        Object paramObj = invocation.getArgs()[1];
        if (paramObj == null) {
            return invocation.proceed();
        }

        // 插入语句只传一个基本类型参数, 不做处理
        if (ClassUtils.isPrimitiveOrWrapper(paramObj.getClass())
                || String.class.isAssignableFrom(paramObj.getClass())
                || Number.class.isAssignableFrom(paramObj.getClass())) {
            return invocation.proceed();
        }

        processParam(paramObj);
        return executor.update(ms, paramObj);
    }


    private Object invokeSetParameter(Invocation invocation) throws Exception {

        ParameterHandler parameterHandler = (ParameterHandler) invocation.getTarget();
        PreparedStatement ps = (PreparedStatement) invocation.getArgs()[0];

        // 反射获取 BoundSql 对象，此对象包含生成的sql和sql的参数map映射
        Field boundSqlField = parameterHandler.getClass().getDeclaredField("boundSql");
        boundSqlField.setAccessible(true);
        /** 获取包装好的sql*/
        BoundSql boundSql = (BoundSql) boundSqlField.get(parameterHandler);

        List<String> paramNames = new ArrayList<>();
        // 若参数映射没有包含的key直接返回
        boolean hasKey = hasParamKey(paramNames, boundSql.getParameterMappings());
        if (!hasKey) {
            return invocation.proceed();
        }

        // 反射获取 参数对像
        Field parameterField = parameterHandler.getClass().getDeclaredField("parameterObject");
        parameterField.setAccessible(true);
        Object parameterObject = parameterField.get(parameterHandler);

        // 改写参数
        parameterObject = processSingle(parameterObject, paramNames);

        // 改写的参数设置到原parameterHandler对象
        parameterField.set(parameterHandler, parameterObject);
        parameterHandler.setParameters(ps);
        return null;
    }

    // 判断已生成sql参数映射中是否包含tenantId
    private boolean hasParamKey(List<String> paramNames, List<ParameterMapping> parameterMappings) {
        boolean hasKey = false;
        for (ParameterMapping parameterMapping : parameterMappings) {
            if (StringUtils.equals(parameterMapping.getProperty(), PARAM_KEY)) {
                hasKey = true;
            } else {
                paramNames.add(parameterMapping.getProperty());
            }
        }
        return hasKey;
    }

    private Object processSingle(Object paramObj, List<String> paramNames) throws Exception {

        Map<String, Object> paramMap = new MapperMethod.ParamMap<>();
        if (paramObj == null) {
            paramMap.put(PARAM_KEY, 1L);
            paramObj = paramMap;
            // 单参数 将 参数转为 map
        } else if (ClassUtils.isPrimitiveOrWrapper(paramObj.getClass())
                || String.class.isAssignableFrom(paramObj.getClass())
                || Number.class.isAssignableFrom(paramObj.getClass())) {
            if (paramNames.size() == 1) {
                paramMap.put(paramNames.iterator().next(), paramObj);
                paramMap.put(PARAM_KEY, 1L);
                paramObj = paramMap;
            }
        } else {
            processParam(paramObj);
        }

        return paramObj;
    }

    private void processParam(Object parameterObject) throws IllegalAccessException, InvocationTargetException {
        // 处理参数对象  如果是 map 且map的key 中没有 tenantId，添加到参数map中
        // 如果参数是bean，反射设置值
        if (parameterObject instanceof Map) {
            ((Map) parameterObject).putIfAbsent(PARAM_KEY, 1L);
        } else {
            PropertyDescriptor ps = BeanUtils.getPropertyDescriptor(parameterObject.getClass(), PARAM_KEY);
            if (ps != null && ps.getReadMethod() != null && ps.getWriteMethod() != null) {
                Object value = ps.getReadMethod().invoke(parameterObject);
                if (value == null) {
                    ps.getWriteMethod().invoke(parameterObject, 1L);
                }
            }
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {

    }
}
