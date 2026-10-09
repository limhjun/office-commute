package com.company.officecommute.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 쿠키 세션 인증의 상태 변경 요청에 대한 CSRF 방어. 역할 검사만으로는 다른 사이트가 로그인한 관리자의
 * 브라우저로 승인·마감을 보내는 것을 막지 못한다. 운영 쿠키의 {@code SameSite=Lax}도 같은 사이트의
 * 다른 출처(서브도메인)는 막지 않으므로 출처를 직접 확인한다.
 * <p>
 * 브라우저는 교차 출처 POST/PUT/DELETE 에 {@code Origin}을 항상 붙인다. Origin(없으면 Referer)이
 * 요청 Host 와 같거나 허용 목록에 있어야 통과한다. 두 헤더가 모두 없으면 브라우저 요청이 아니므로 통과시킨다.
 */
public class OriginCheckInterceptor implements HandlerInterceptor {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final List<String> allowedOrigins;

    public OriginCheckInterceptor(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins.stream()
                .filter(origin -> origin != null && !origin.isBlank())
                .map(OriginCheckInterceptor::normalizeOrigin)
                .toList();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (SAFE_METHODS.contains(request.getMethod().toUpperCase(Locale.ROOT))) {
            return true;
        }
        String source = request.getHeader("Origin");
        if (source == null || source.isBlank() || "null".equals(source)) {
            source = request.getHeader("Referer");
        }
        if (source == null || source.isBlank()) {
            return true;
        }
        if (!isAllowed(source, request)) {
            throw new CsrfOriginRejectedException();
        }
        return true;
    }

    private boolean isAllowed(String source, HttpServletRequest request) {
        URI uri;
        try {
            uri = URI.create(source.trim());
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (uri.getScheme() == null || uri.getHost() == null) {
            return false;
        }
        String sourceOrigin = originOf(uri.getScheme(), uri.getHost(), uri.getPort());
        if (allowedOrigins.contains(sourceOrigin)) {
            return true;
        }
        return hostAndPort(uri.getScheme(), uri.getHost(), uri.getPort()).equals(requestHost(request));
    }

    /**
     * Nginx 가 {@code Host}를 그대로 넘기므로 Host 헤더가 브라우저가 본 출처와 같다.
     * 스킴은 비교하지 않는다 — 프록시 뒤에서 앱은 http 로 받는다.
     */
    private static String requestHost(HttpServletRequest request) {
        String host = request.getHeader("Host");
        if (host == null || host.isBlank()) {
            return hostAndPort(request.getScheme(), request.getServerName(), request.getServerPort());
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith(":80") || normalized.endsWith(":443")) {
            return normalized.substring(0, normalized.lastIndexOf(':'));
        }
        return normalized;
    }

    private static String hostAndPort(String scheme, String host, int port) {
        String lowerHost = host.toLowerCase(Locale.ROOT);
        if (port < 0 || isDefaultPort(scheme, port)) {
            return lowerHost;
        }
        return lowerHost + ":" + port;
    }

    private static boolean isDefaultPort(String scheme, int port) {
        return ("http".equalsIgnoreCase(scheme) && port == 80) || ("https".equalsIgnoreCase(scheme) && port == 443);
    }

    private static String originOf(String scheme, String host, int port) {
        return scheme.toLowerCase(Locale.ROOT) + "://" + hostAndPort(scheme, host, port);
    }

    private static String normalizeOrigin(String origin) {
        URI uri = URI.create(origin.trim());
        return originOf(uri.getScheme(), uri.getHost(), uri.getPort());
    }
}
