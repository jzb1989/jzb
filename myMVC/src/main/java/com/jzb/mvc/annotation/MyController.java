package com.jzb.mvc.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})  //  TYPE表示作用在类上   FIELD表示是作用在成员变量上的   METHOD表示是作用在方法上的  PARAMETER表示是作用在参数上的
@Retention(RetentionPolicy.RUNTIME)   //运行时产生作用,进行动态的解析;
@Documented	
public @interface MyController {
	String value() default "";
}
