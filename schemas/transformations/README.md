# Transformation Configuration v1.0

[`transformation-v1.schema.json`](transformation-v1.schema.json) defines the implemented JSON
configuration shape for StreamForge's safe declarative transformation DSL. The backend can parse
the raw configuration and compile it against a known field schema. Event transformation execution
is not implemented yet.

## Processing Boundary

Configuration activation has two explicit stages:

1. `TransformationConfigParser` parses JSON into a closed raw operation and condition hierarchy.
2. `TransformationCompiler` resolves field paths and types into immutable compiled rules.

Compilation applies operations in document order. A source field must exist at the point it is
referenced. A destination parent must already exist as an object; `create_object` can create that
parent explicitly. Unknown operations, properties, conditions, value types, field paths, and
unsupported casts are rejected before an event can be processed.

The canonical v1 field catalog protects the complete metadata envelope and the computed
`payload.type` discriminator. `select` retains protected fields. `remove`, `rename`, `cast`, and
other mutations cannot target a protected field, an ancestor containing one, or a new child under
one.

## Supported Operations

| Operation | Configuration | Compile-time behavior |
| --- | --- | --- |
| `select` | `fields` | Retains selected paths, their object hierarchy, and protected fields. |
| `rename` | `from`, `to` | Moves one field or object subtree to an unused path. |
| `remove` | `path` | Removes one field or object subtree. |
| `add_constant` | `path`, typed `value` | Adds a scalar field below an existing object. |
| `cast` | `path`, `toType` | Allows only the explicit cast matrix below. |
| `scale_fixed_decimal` | `path`, `targetScale` | Requires `FIXED_DECIMAL` and scale `0..18`. |
| `enum_map` | `path`, `mapping` | Requires an `ENUM` or `STRING` field. |
| `filter` | `condition` | Compiles a restricted condition tree. |
| `create_object` | `path` | Adds an empty object path for later nested fields. |
| `conditional_field` | `path`, `condition`, `whenTrue`, `whenFalse` | Requires both typed branch values to have the same type. |

## Types and Casts

The scalar types are `STRING`, `BOOLEAN`, `INT64`, `FIXED_DECIMAL`, `ENUM`, and
`TIMESTAMP_NANOS`. Fixed decimals contain an exact signed `long` mantissa and scale `0..18`.
Timestamps are nonnegative nanoseconds represented by a Java `long`. There is no floating-point
type.

The compiler accepts identity casts plus these conversions:

- `STRING` to any scalar type.
- `BOOLEAN` to `STRING`.
- `INT64` to `STRING` or `FIXED_DECIMAL`.
- `FIXED_DECIMAL` to `STRING`.
- `ENUM` to `STRING`.
- `TIMESTAMP_NANOS` to `STRING` or `INT64`.

Execution-time parsing, overflow, and exactness rules will be implemented before casts execute. A
compiled rule does not imply that event execution currently exists.

## Conditions

A condition is one of:

- A field-to-literal comparison using `EQ`, `NE`, `LT`, `LTE`, `GT`, or `GTE`.
- `all` or `any` with one or more child conditions.
- `not` with one child condition.

The literal type must exactly match the resolved field type. Ordered comparisons are supported for
`STRING`, `INT64`, `FIXED_DECIMAL`, and `TIMESTAMP_NANOS`; `BOOLEAN` and `ENUM` support equality
and inequality only. Conditions are limited to 16 levels and 256 total nodes per configuration.

## Security Boundary

The schema contains no expression or function-call field. The parser rejects unknown properties,
duplicate keys, dangerous reflective path segments, excessive nesting, unsupported discriminators,
and non-integral values for integer types. Compiled rules contain field definitions and typed
literals, never source text to evaluate.

The following are deliberately unsupported:

- JavaScript, Java source, SpEL, MVEL, scripts, bytecode, shell commands, or template evaluation.
- Reflection, method invocation, arbitrary class names, constructors, or class-loader access.
- Dynamic field paths, computed property names, field-to-field comparisons, regex, or callbacks.
- General arithmetic, joins, aggregation, loops, recursion, network access, or file access.
- Floating-point constants, implicit coercion, null literals, arrays, and arbitrary JSON objects.

Adding an operation or widening a type rule requires a versioned schema change, explicit Java AST
types, compiler validation, security review, and tests.
