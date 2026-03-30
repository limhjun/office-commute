package com.company.officecommute.config;

import com.company.officecommute.auth.AuthInterceptor;
import com.company.officecommute.service.employee.EmployeeService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final EmployeeService employeeService;

    public WebConfig(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @Bean
    public AuthInterceptor authInterceptor() {
        return new AuthInterceptor(employeeService);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns("/h2-console/**", "/", "/error");
    }
}
