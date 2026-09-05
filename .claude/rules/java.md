---
paths:
  - "**/*.java"
---

# Java conventions

## Dependency injection

- **Constructor injection only, everywhere** — `private final` fields via Lombok `@RequiredArgsConstructor`, or an
  explicit constructor when a param needs `@Qualifier` or `Optional<T>`. Field `@Autowired` and `@Resource` are banned;
  `@Autowired(required = false)` especially so.
- **A genuinely optional collaborator is an explicit `Optional<T>` constructor param** (deployment-conditional beans
  like the storage-backend split) **or a no-op implementation** — never a nullable field with use-site `!= null` guards.
  A required control dependency is `Objects.requireNonNull`'d with a message naming the consequence.
- **Conditionality lives in the composition roots only** — `JettyConfigurationBuilder` (server) and
  `FogwallDashboardApplication`'s `registerSingleton` block (dashboard). Everything they hand out is non-null; anything
  conditional gets resolved inside them. No `BeanFactoryPostProcessor`, no conditional bean registration for beans that
  always exist.

## Comments and javadoc

Comments describe the code, not its history. No issue references for fogwall's own features or bugs
(`/** MyCoolFeature (#123) … */`) and no version numbers ("deferred to 1.x"). Both go stale the moment a follow-up
lands; that context belongs in release notes and issues. The one exception is a workaround for an external project, e.g.
`// workaround for https://github.com/foo/bar/issues/123`.
