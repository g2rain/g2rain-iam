package com.g2rain.iam.config;

import com.g2rain.common.model.Result;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.customizers.PropertyCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Objects;

/**
 * OpenAPI 文档配置。
 *
 * <p>使用 {@link PropertyCustomizer}（SpringDoc 推荐扩展点）在属性写入 Schema 前移除
 * {@code @Schema(hidden = true)} 标记的字段；swagger-core 对字段级 hidden 支持不完整时仍可生效。</p>
 *
 * @author alpha
 * @since 2026/4/9
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI(@Value("${spring.application.name}") String appName) {
        return new OpenAPI().info(new Info()
            .title(appName)
            .version("1.0")
            .description("IAM 接口文档")
        );
    }

    /**
     * 隐藏带 {@code @Schema(hidden = true)} 的模型属性（返回 {@code null} 表示不出现在文档中）。
     */
    @Bean
    public PropertyCustomizer hiddenSchemaPropertyCustomizer() {
        return (schema, type) -> shouldHideProperty(type) ? null : schema;
    }

    private static boolean shouldHideProperty(AnnotatedType type) {
        if (Objects.isNull(type)) {
            return false;
        }

        Annotation[] ctx = type.getCtxAnnotations();
        if (Objects.nonNull(ctx)) {
            for (Annotation a : ctx) {
                if (a instanceof Schema s && s.hidden()) {
                    return true;
                }
            }
        }

        String propertyName = type.getPropertyName();
        if (Objects.isNull(propertyName)) {
            return false;
        }

        try {
            Field field = Result.class.getDeclaredField(propertyName);
            Schema ann = field.getAnnotation(Schema.class);
            return Objects.nonNull(ann) && ann.hidden();
        } catch (NoSuchFieldException | SecurityException ignored) {
            return false;
        }
    }
}
