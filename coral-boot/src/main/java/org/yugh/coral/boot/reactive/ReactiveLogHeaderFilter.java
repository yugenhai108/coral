/*
 *
 * Copyright (c) 2014-2020, yugenhai108@gmail.com.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 */


package org.yugh.coral.boot.reactive;

import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.yugh.coral.core.config.RequestHeaderProvider;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.Map;

import static java.util.stream.Collectors.toMap;

/**
 * Log Header Reactive application.
 *
 * @author yugenhai
 */
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class ReactiveLogHeaderFilter implements WebFilter, Ordered {

    // filter should be set
    private int order = Ordered.HIGHEST_PRECEDENCE + 2;

    @Override
    public int getOrder() {
        return this.order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    @Nullable
    @Override
    public Mono<Void> filter(@Nullable ServerWebExchange ex, @Nullable WebFilterChain chain) {
        ex.getResponse().beforeCommit(
                () -> addContextToHttpResponseHeaders(ex.getResponse())
        );
        return chain.filter(ex)
                .subscriberContext(
                        ctx -> addRequestHeadersToContext(ex.getRequest(), ctx)
                );
    }


    private Context addRequestHeadersToContext(final ServerHttpRequest request, final Context context) {
        MDC.put(RequestHeaderProvider.REQUEST_ID_KEY, request.getHeaders().toSingleValueMap().get(RequestHeaderProvider.REQUEST_ID_KEY));
        final Map<String, String> contextMap = request.getHeaders().toSingleValueMap().entrySet()
                .stream()
                .filter(req -> req.getKey().startsWith(RequestHeaderProvider.REQUEST_ID_KEY))
                .collect(toMap(
                        v -> v.getKey().substring(RequestHeaderProvider.REQUEST_ID_KEY.length()), Map.Entry::getValue));
        return context.put(RequestHeaderProvider.CONTEXT_MAP, contextMap);
    }

    private Mono<Void> addContextToHttpResponseHeaders(final ServerHttpResponse res) {
        return Mono.subscriberContext().doOnNext(ctx -> {
            if (!ctx.hasKey(RequestHeaderProvider.CONTEXT_MAP)) {
                return;
            }
            final HttpHeaders headers = res.getHeaders();
            ctx.<Map<String, String>>get(RequestHeaderProvider.CONTEXT_MAP).forEach(
                    (key, value) -> headers.add(RequestHeaderProvider.REQUEST_ID_KEY + key, value)
            );
            headers.keySet().forEach(MDC::remove);
        }).then();
    }
}