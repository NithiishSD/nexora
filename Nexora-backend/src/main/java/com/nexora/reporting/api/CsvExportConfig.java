package com.nexora.reporting.api;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import tools.jackson.databind.json.JsonMapper;

// format=csv on any list or report (FR-RPT-16): the CSV converter, and paging switched off for a CSV request (all
// matching rows, at most 50000 + 1 so the exporter can tell the client to narrow the period). Ordered first so this
// Pageable resolver is asked before Spring Data's own (which it delegates to for page, size defaults and sort).
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
class CsvExportConfig implements WebMvcConfigurer {

    private final JsonMapper jsonMapper;
    private final ObjectProvider<PageableHandlerMethodArgumentResolver> pageableResolver;

    CsvExportConfig(JsonMapper jsonMapper, ObjectProvider<PageableHandlerMethodArgumentResolver> pageableResolver) {
        this.jsonMapper = jsonMapper;
        this.pageableResolver = pageableResolver;
    }

    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.addCustomConverter(new CsvMessageConverter(jsonMapper));
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return Pageable.class.equals(parameter.getParameterType());
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav, NativeWebRequest request,
                                          WebDataBinderFactory binderFactory) {
                Pageable pageable = pageableResolver.getObject().resolveArgument(parameter, mav, request, binderFactory);
                return CsvMessageConverter.csvRequested(request.getNativeRequest(HttpServletRequest.class))
                        ? PageRequest.of(0, CsvExporter.MAX_ROWS + 1, pageable.getSort()) : pageable;
            }
        });
    }
}
