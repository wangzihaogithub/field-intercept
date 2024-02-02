package com.github.fieldintercept.springboot;

import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 自动配置
 *
 * @author wangzihao
 */
@AutoConfigureOrder(Integer.MAX_VALUE - 10)
@EnableConfigurationProperties(FieldinterceptProperties.class)
@Import(FieldInterceptImportSelector.class)
@Configuration
public class FieldInterceptAutoConfiguration {

}
