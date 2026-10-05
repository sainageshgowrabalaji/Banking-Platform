package com.sainagesh.bank.gateway.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request an id.
 *
 * <p>If the client sent an {@code X-Request-Id}, it is kept. If not, one is made. The id is passed on
 * to the service behind, written into every log line of this request, and returned in the response.
 * When a customer reports a problem, this one id finds the request in every log.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    /** Only plain ids are accepted from outside, so a caller cannot write anything else into the logs. */
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String given = request.getHeader(HEADER);
        String id = given != null && SAFE.matcher(given).matches() ? given : UUID.randomUUID().toString();
        response.setHeader(HEADER, id);
        MDC.put("requestId", id);
        try {
            chain.doFilter(new WithRequestId(request, id), response);
        } finally {
            MDC.remove("requestId");
        }
    }

    /** The same request, with the id header set to the chosen value. */
    private static final class WithRequestId extends HttpServletRequestWrapper {

        private final String id;

        WithRequestId(HttpServletRequest request, String id) {
            super(request);
            this.id = id;
        }

        @Override
        public String getHeader(String name) {
            return HEADER.equalsIgnoreCase(name) ? id : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return HEADER.equalsIgnoreCase(name) ? Collections.enumeration(List.of(id)) : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = new ArrayList<>();
            boolean present = false;
            Enumeration<String> original = super.getHeaderNames();
            while (original.hasMoreElements()) {
                String name = original.nextElement();
                present |= HEADER.equalsIgnoreCase(name);
                names.add(name);
            }
            if (!present) {
                names.add(HEADER);
            }
            return Collections.enumeration(names);
        }
    }
}
