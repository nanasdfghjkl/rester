package com.rester;

import io.swagger.handler.ValidatorController;
import io.swagger.oas.inflector.models.RequestContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.io.IOException;
import java.util.Map;

@SpringBootApplication
public class ResterApplication {

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        corsConfiguration.addAllowedOrigin("*");
        corsConfiguration.addAllowedHeader("*");
        corsConfiguration.addAllowedMethod("*");
        source.registerCorsConfiguration("/**", corsConfiguration);
        return new CorsFilter(source);
    }

    public static void main(String[] args) {
        SpringApplication.run(ResterApplication.class, args);

        ValidatorController validator = new ValidatorController();
        //String content=validator.readFile("D:\\test\\data-all-clear\\github.com-v3-swagger.yaml");
        String content= null;
        try {
            content = validator.readFile("D:\\test\\data-all-clear\\github.com-v3-swagger.yaml");
        } catch (IOException e) {
            e.printStackTrace();
        }

        //静态检测
        validator.validateByString(new RequestContext(), content);
        Map<String, Map<String, String>> map = validator.getPathParameterMap();
        System.out.println("mapsize:"+map.size());
        //动态检测
        try {
            validator.dynamicValidateByContent(content);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Map<String,Object> pathdetaildynamic=validator.getPathDetailDynamic();
        System.out.println(validator.getValidResponseNum());
        System.out.println(validator.getResponseNum());
    }

}
