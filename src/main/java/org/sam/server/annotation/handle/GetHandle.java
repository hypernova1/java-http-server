package org.sam.server.annotation.handle;

import org.sam.server.constant.ContentType;
import org.sam.server.constant.HttpMethod;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by melchor
 * Date: 2020/07/17
 * Time: 1:44 PM
 */

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface GetHandle {
    String value() default "/";
    ContentType contentType() default ContentType.JSON;
    HttpMethod method = HttpMethod.GET;
}