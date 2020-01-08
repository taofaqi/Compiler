package com.ttp.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author faqi.tao
 * @time 2019/12/30
 * <p>
 * 注解接口的类
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface Aspect {
}
