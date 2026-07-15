package com.rbc.fogwall.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * Binds a single entry in the {@code groups:} list in fogwall.yml.
 *
 * <p>Example:
 *
 * <pre>
 * groups:
 *   - name: team-alpha
 *     description: "Alpha team push access"
 *     members:
 *       - alice
 *       - bob
 *     grants:
 *       - provider: github
 *         match:
 *           target: SLUG
 *           value: /myorg/**
 *           type: GLOB
 *         grant: PUSH
 * </pre>
 */
@Data
public class GroupConfig {

    private String name = "";
    private String description = "";
    private List<String> members = new ArrayList<>();
    private List<GroupGrantConfig> grants = new ArrayList<>();

    @Data
    public static class GroupGrantConfig {
        private String provider = "";
        private MatchConfig match = new MatchConfig();
        private String grant = "PUSH";
    }
}
