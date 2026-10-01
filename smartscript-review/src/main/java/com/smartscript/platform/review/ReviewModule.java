package com.smartscript.platform.review;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 审核风控与AI模块启动类
 *
 * @author smartscript
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.smartscript.platform.review"})
@MapperScan("com.smartscript.platform.review.mapper")
public class ReviewModule {

}
