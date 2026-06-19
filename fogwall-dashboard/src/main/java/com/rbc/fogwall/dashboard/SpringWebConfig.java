package com.rbc.fogwall.dashboard;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableWebMvc
@ComponentScan("com.rbc.fogwall.dashboard.controller")
public class SpringWebConfig implements WebMvcConfigurer {

    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.withJsonConverter(new JacksonJsonHttpMessageConverter(JsonMapper.builder()
                .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
                .changeDefaultPropertyInclusion(incl -> incl.withContentInclusion(JsonInclude.Include.NON_NULL))
                .build()));
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // WebJar assets (Swagger UI) served at /webjars/** with versioned paths.
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/")
                .resourceChain(true);
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/dashboard/");
    }
}
