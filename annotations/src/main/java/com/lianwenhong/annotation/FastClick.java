package com.lianwenhong.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * value属性是标记方法的唯一标识，同一类中的value属性不可相同
 * 否则可能出现2个方法直接互相过滤点击
 * <p>
 * 注解保留时机必须持续到RUNTIME，因为javassist处理的就是class文件
 * 如果是SOURCE或CLASS时本注解已被去除所以会导致在解析类时找不到该注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FastClick {
    int value();
}
