# Fabric MMO ports overlay M2.0

MythicMobs skill component registry and skill-line parser parity slice, reconstructed against MythicMobs 5.6.2 bytecode and validated against the server's real MythicMobs Skills corpus.

Local Java 21 gates:

```text
MYTHICMOBS_SKILL_COMPONENT_REGISTRY_RUNTIME=PASS
mechanicKeys=642
conditionKeys=294
targeterKeys=154

MYTHICMOBS_FULL_SKILL_CORPUS_PARSER=PASS
skills=20520 coreMechanics=19035 externalOrLegacyMechanics=1485
conditions=4002 coreConditions=3752 externalOrLegacyConditions=250
targeters=13067 coreTargeters=12288 externalOrLegacyTargeters=779
unresolvedMechanicIds=27
unresolvedConditionIds=6
unresolvedTargeterIds=11
```

Locked behavior:

- core component names and aliases are generated from the original `@MythicMechanic`, `@MythicCondition`, and `@MythicTargeter` annotations;
- registry keys use the same uppercase lookup contract as `SkillExecutor`;
- aliases and primary IDs share one registry and replacement is last-registration-wins;
- registry remains extensible for external/custom mechanics, conditions, and targeters;
- skill-line lexer preserves mechanic config, targeter config, placeholders, nested list/config syntax, and inline `?condition` fragments;
- legacy/truncated config blocks are preserved rather than rejected. The real corpus contains one such projectile line, and MythicMobs' own line/config path is lenient enough for it to exist in production config;
- no server configuration content is committed. The corpus is only used as a local regression input.

This is not a claim that all 280 mechanics have Fabric-side execution semantics yet. It locks the registry/parser surface needed before mechanic-by-mechanic execution is wired, and prevents silent loss of custom/external component IDs.

Source archive SHA-256 after base64 decode: `25387d389ddc3ee0da2663071859a6199b5ceddc428070b4383b0b6ff3ed334f`.

This overlay remains outside the current GitHub Actions path filter.
