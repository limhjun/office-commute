package com.company.officecommute.config;

import com.company.officecommute.auth.AuthInterceptor;
import com.company.officecommute.auth.OriginCheckInterceptor;
import com.company.officecommute.repository.employee.EmployeeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final EmployeeRepository employeeRepository;
    private final List<String> allowedOrigins;

    public WebConfig(
            EmployeeRepository employeeRepository,
            @Value("${app.security.allowed-origins:}") List<String> allowedOrigins
    ) {
        this.employeeRepository = employeeRepository;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public AuthInterceptor authInterceptor() {
        return new AuthInterceptor(employeeRepository);
    }

    @Bean
    public OriginCheckInterceptor originCheckInterceptor() {
        return new OriginCheckInterceptor(allowedOrigins);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 로그인 CSRF 도 막도록 인증 예외 경로까지 포함한다. 인증보다 먼저 검사한다.
        registry.addInterceptor(originCheckInterceptor())
                .addPathPatterns("/api/**");
        registry.addInterceptor(authInterceptor())
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**");
    }
}
