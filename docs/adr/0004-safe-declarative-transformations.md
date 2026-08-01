# ADR 0004: Safe Declarative Transformations

- Status: Accepted
- Date: 2026-07-31

## Context

StreamForge needs configurable transformations while processing untrusted or mistaken user configuration against exact financial data. Executing user-provided JavaScript or another general-purpose language would introduce code-execution, resource-exhaustion, nondeterminism, and precision risks.

## Decision

Transformations will use a versioned JSON or YAML document representing a typed operation abstract syntax tree. A transformation document will declare its schema version and an ordered composition of allowlisted operations.

The initial operation families will be:

- `filter`: retain or reject an event using typed comparisons and boolean composition.
- `map`: replace supported field values through an explicit, typed mapping.
- `derive`: calculate a supported field using allowlisted typed operators.
- `project`: select an approved subset of payload fields for downstream serialization without removing the canonical envelope.

Configuration will be validated against its schema before activation. Validation will resolve field references, verify input and result types, enforce fixed-point scale rules, and reject unsupported operators. Fixed-point arithmetic will be checked for overflow and incompatible scales.

Source identity, source sequence, event timestamp, schema metadata, and Raw Event reference will remain immutable. No operation may remove or rewrite these canonical envelope fields.

The validated tree may be compiled into an internal execution plan, but it will not be evaluated as source code. Results must be deterministic for the same Canonical Event and transformation version. JavaScript, bytecode loading, reflection, shell execution, and unrestricted or general-purpose expression evaluation are prohibited.

A transformation failure will produce a structured diagnostic retaining the Canonical Event's Raw Event reference. It must not silently change precision or substitute an approximate value.

## Alternatives Considered

### Arbitrary JavaScript or another scripting language

Scripts offer broad expressiveness, but safe isolation, deterministic resource limits, and exact numeric behavior would become core security responsibilities.

### A restricted general-purpose expression language

An engine such as CEL could provide mature parsing and typing, but it would expose a broader language surface and external semantics before StreamForge's required operations are understood.

### Fixed, non-composable transformation stages

Parameter-only stages are easy to secure but make common combinations verbose and can drive users toward application changes for routine transformations.

### A custom expression string grammar

A textual DSL can be concise, but an explicit JSON/YAML tree is easier to schema-validate, version, inspect, and generate from a future dashboard.

## Consequences

- Transformations will be auditable, serializable, deterministic, and safe to validate before data processing.
- The operation set will initially be less expressive than general-purpose code.
- Every new operator will need a versioned contract, type rules, security review, documentation, and tests.
- JSON/YAML documents may be verbose, but the future dashboard can generate them without parsing source text.
- Checked fixed-point operations preserve financial correctness while making overflow and scale failures explicit.
- The transform engine can remain independent of input formats and output transports because it operates only on Canonical Events.
