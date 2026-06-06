package org.finos.gitproxy.jetty.config;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * Validates YAML config files against the known {@link GitProxyConfig} structure before Gestalt loads them. Any key
 * that does not correspond to a field in the target POJO throws {@link IllegalStateException} at startup, so typos and
 * misplaced keys are caught immediately instead of being silently ignored.
 *
 * <p>Works by recursively walking the raw YAML tree produced by SnakeYAML and comparing each key against the kebab-case
 * equivalent of the corresponding POJO's declared fields. Nested POJOs, {@code Map<String, Pojo>} values, and
 * {@code List<Pojo>} elements are all validated.
 */
final class YamlStructureValidator {

    private YamlStructureValidator() {}

    static void validateClasspathResource(String resource) {
        InputStream is = YamlStructureValidator.class.getClassLoader().getResourceAsStream(resource);
        if (is == null) return;
        try (is) {
            validateStream(is, resource);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read config resource: " + resource, e);
        }
    }

    static void validateFile(Path file) throws IOException {
        try (InputStream is = Files.newInputStream(file)) {
            validateStream(is, file.toString());
        }
    }

    private static void validateStream(InputStream is, String source) {
        Object parsed = new Yaml().load(is);
        if (!(parsed instanceof Map<?, ?> root)) return;
        checkNode(castStringMap(root), GitProxyConfig.class, "", source);
    }

    private static void checkNode(Map<String, Object> node, Class<?> pojoClass, String path, String source) {
        Map<String, Field> knownFields = fieldsByKebabKey(pojoClass);
        for (Map.Entry<String, Object> entry : node.entrySet()) {
            String key = entry.getKey();
            String fullPath = path.isEmpty() ? key : path + "." + key;
            if (!knownFields.containsKey(key)) {
                throw new IllegalStateException("Unknown configuration key '" + fullPath + "' in " + source
                        + ". Check for a typo or misplaced key."
                        + " See docs/CONFIGURATION.md for valid keys.");
            }
            Object value = entry.getValue();
            if (value == null) continue;
            Field field = knownFields.get(key);
            if (value instanceof Map<?, ?> mapValue) {
                recurseMap(castStringMap(mapValue), field, fullPath, source);
            } else if (value instanceof List<?> listValue) {
                recurseList(listValue, field, fullPath, source);
            }
        }
    }

    private static void recurseMap(Map<String, Object> value, Field field, String path, String source) {
        Class<?> fieldType = field.getType();
        if (isProjectPojo(fieldType)) {
            checkNode(value, fieldType, path, source);
        } else if (Map.class.isAssignableFrom(fieldType)) {
            Class<?> valueClass = genericArg(field, 1);
            if (valueClass != null && isProjectPojo(valueClass)) {
                for (Map.Entry<String, Object> e : value.entrySet()) {
                    if (e.getValue() instanceof Map<?, ?> nested) {
                        checkNode(castStringMap(nested), valueClass, path + "." + e.getKey(), source);
                    }
                }
            }
        }
    }

    private static void recurseList(List<?> list, Field field, String path, String source) {
        Class<?> elementClass = genericArg(field, 0);
        if (elementClass == null || !isProjectPojo(elementClass)) return;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) instanceof Map<?, ?> elem) {
                checkNode(castStringMap(elem), elementClass, path + "[" + i + "]", source);
            }
        }
    }

    private static Map<String, Field> fieldsByKebabKey(Class<?> clazz) {
        Map<String, Field> result = new LinkedHashMap<>();
        for (Field f : clazz.getDeclaredFields()) {
            if (!f.isSynthetic() && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                result.put(toKebabCase(f.getName()), f);
            }
        }
        return result;
    }

    private static boolean isProjectPojo(Class<?> type) {
        return type.getPackageName().startsWith("org.finos.gitproxy");
    }

    private static Class<?> genericArg(Field field, int index) {
        Type generic = field.getGenericType();
        if (generic instanceof ParameterizedType pt && pt.getActualTypeArguments().length > index) {
            Type arg = pt.getActualTypeArguments()[index];
            if (arg instanceof Class<?> c) return c;
        }
        return null;
    }

    private static String toKebabCase(String camelCase) {
        return camelCase.replaceAll("([A-Z])", "-$1").toLowerCase();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castStringMap(Map<?, ?> raw) {
        return (Map<String, Object>) raw;
    }
}
