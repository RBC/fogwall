package com.rbc.fogwall.dashboard.controller;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Serves the OpenAPI 3 specification for the fogwall REST API. The spec is generated at startup by walking Spring's
 * {@link RequestMappingHandlerMapping}, so it always reflects the actual registered routes.
 *
 * <ul>
 *   <li>{@code GET /api/openapi.yaml} — YAML format (machine-readable)
 *   <li>{@code GET /api/openapi.json} — JSON format
 * </ul>
 *
 * Both endpoints are public (no authentication required). Swagger UI is served at {@code /swagger-ui/}.
 */
@RestController
@RequestMapping("/api")
public class OpenApiController {

    /** Controllers whose endpoints should not appear in the public spec. */
    private static final Set<String> EXCLUDED = Set.of("OpenApiController", "SpaController");

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    private String specYaml;
    private String specJson;

    private static String readVersion() {
        var props = new Properties();
        try (var in = OpenApiController.class.getClassLoader().getResourceAsStream("version.properties")) {
            if (in != null) props.load(in);
        } catch (IOException ignored) {
        }
        return props.getProperty("version", "unknown");
    }

    public record VersionResponse(String version, String apiDocs) {}

    @GetMapping
    public ResponseEntity<VersionResponse> getSpec() {
        return ResponseEntity.ok(new VersionResponse(readVersion(), "/api/openapi.json"));
    }

    @PostConstruct
    public void buildSpec() {
        OpenAPI spec = new OpenAPI()
                .info(
                        new Info()
                                .title("fogwall API")
                                .version(readVersion())
                                .description(
                                        "REST API for fogwall. Covers push approval workflow, user management, access control rules, and provider configuration."))
                .components(new Components()
                        .addSecuritySchemes(
                                "apiKey",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-Api-Key")
                                        .description(
                                                "API key for programmatic access. Configured via the fogwall_API_KEY environment variable."))
                        .addSecuritySchemes(
                                "session",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.COOKIE)
                                        .name("JSESSIONID")
                                        .description("Session cookie obtained via form login at /login.")))
                .addSecurityItem(new SecurityRequirement().addList("apiKey"))
                .addSecurityItem(new SecurityRequirement().addList("session"));

        // Group handler methods by path for stable, sorted output
        var byPath = new TreeMap<String, List<Map.Entry<RequestMappingInfo, HandlerMethod>>>();
        for (var entry : handlerMapping.getHandlerMethods().entrySet()) {
            if (EXCLUDED.contains(entry.getValue().getBeanType().getSimpleName())) continue;
            for (String path : resolvePatterns(entry.getKey())) {
                byPath.computeIfAbsent(path, p -> new ArrayList<>()).add(entry);
            }
        }

        Paths paths = new Paths();
        for (var entry : byPath.entrySet()) {
            PathItem pathItem = new PathItem();
            for (var mapping : entry.getValue()) {
                Operation op = buildOperation(mapping.getValue(), mapping.getKey());
                for (RequestMethod method : effectiveMethods(mapping.getKey())) {
                    assignOperation(pathItem, method, op);
                }
            }
            paths.addPathItem(entry.getKey(), pathItem);
        }
        spec.setPaths(paths);

        this.specYaml = Yaml.pretty(spec);
        this.specJson = Json.pretty(spec);
    }

    private Set<String> resolvePatterns(RequestMappingInfo info) {
        var pc = info.getPathPatternsCondition();
        if (pc == null) return Set.of();
        var result = new LinkedHashSet<String>();
        pc.getPatterns().forEach(p -> result.add(p.getPatternString()));
        return result;
    }

    private Set<RequestMethod> effectiveMethods(RequestMappingInfo info) {
        Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
        return methods.isEmpty() ? Set.of(RequestMethod.GET) : methods;
    }

    private Operation buildOperation(HandlerMethod handler, RequestMappingInfo info) {
        Operation op = new Operation();

        // Prefer @Operation annotation for operationId and summary; fall back to method name.
        // Using FQN for the annotation class to avoid a name collision with io.swagger.v3.oas.models.Operation.
        var opAnnotation = handler.getMethodAnnotation(io.swagger.v3.oas.annotations.Operation.class);
        op.setOperationId(
                opAnnotation != null && !opAnnotation.operationId().isEmpty()
                        ? opAnnotation.operationId()
                        : handler.getMethod().getName());
        if (opAnnotation != null && !opAnnotation.summary().isEmpty()) {
            op.setSummary(opAnnotation.summary());
        }

        // Prefer @Tag on the controller class; fall back to trimmed class name.
        Tag tagAnnotation = handler.getBeanType().getAnnotation(Tag.class);
        op.addTagsItem(
                tagAnnotation != null
                        ? tagAnnotation.name()
                        : handler.getBeanType().getSimpleName().replace("Controller", ""));

        List<Parameter> params = new ArrayList<>();
        for (var mp : handler.getMethodParameters()) {
            PathVariable pv = mp.getParameterAnnotation(PathVariable.class);
            if (pv != null) {
                String name = pv.value().isEmpty() ? mp.getParameterName() : pv.value();
                params.add(new Parameter().in("path").name(name).required(true).schema(new StringSchema()));
            }
            RequestParam rp = mp.getParameterAnnotation(RequestParam.class);
            if (rp != null) {
                String name = rp.value().isEmpty() ? mp.getParameterName() : rp.value();
                // A defaultValue makes the param optional regardless of the required() flag.
                boolean hasDefault = !ValueConstants.DEFAULT_NONE.equals(rp.defaultValue());
                params.add(new Parameter()
                        .in("query")
                        .name(name)
                        .required(rp.required() && !hasDefault)
                        .schema(new StringSchema()));
            }
        }
        if (!params.isEmpty()) op.setParameters(params);

        return op;
    }

    private static void assignOperation(PathItem item, RequestMethod method, Operation op) {
        switch (method) {
            case GET -> item.setGet(op);
            case POST -> item.setPost(op);
            case PUT -> item.setPut(op);
            case DELETE -> item.setDelete(op);
            case PATCH -> item.setPatch(op);
            case HEAD -> item.setHead(op);
            case OPTIONS -> item.setOptions(op);
            default -> {
                /* TRACE etc. — skip */
            }
        }
    }

    @RequestMapping(value = "/openapi.yaml", method = RequestMethod.GET, produces = "application/yaml")
    public String getYaml() {
        return specYaml;
    }

    @RequestMapping(value = "/openapi.json", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public String getJson() {
        return specJson;
    }
}
