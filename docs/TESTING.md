# Testing

Venomous uses JUnit 5 (Jupiter) for example tests and [jqwik](https://jqwik.net/) for property
tests. Coverage is measured with JaCoCo and uploaded to Codecov by CI.

## Run everything

```bash
./gradlew test                    # Windows: .\gradlew.bat test
./gradlew build                   # compile + test + jar
```

`test` is configured with `useJUnitPlatform()` and logs every passed, skipped, and failed test to
the console.

Reports:

| Report | Location |
| --- | --- |
| HTML test report | `build/reports/tests/test/index.html` |
| JUnit XML | `build/test-results/test/*.xml` |
| JaCoCo HTML | `build/reports/jacoco/test/html/index.html` |
| JaCoCo XML | `build/reports/jacoco/test/jacocoTestReport.xml` |

Run one class or method:

```bash
./gradlew test --tests "net.venomous.meteoraddon.MovementMathTest"
./gradlew test --tests "net.venomous.meteoraddon.MovementMathTest.clampIsIdempotent"
```

## Test layout

Tests live in `src/test/java/net/venomous/meteoraddon/` and are named `*Test`. They run on the
normal JVM without starting Minecraft, so they cover pure logic: math helpers, rotation/aim
decisions, packet gates, rule tables, source/layout guards, and metadata checks.

The current baseline is recorded in [BASELINE_FAILURES.md](BASELINE_FAILURES.md). Always compare a
new run against it before assuming a failure is new.

## Writing a JUnit test

```java
package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExampleTest {
    @Test
    void addsTwoNumbers() {
        assertEquals(4, 2 + 2);
    }
}
```

## Writing a jqwik property test

jqwik is on the test classpath (`net.jqwik:jqwik:1.8.5`). Property tests use `@Property` instead
of `@Test` and generated parameters:

```java
package net.venomous.meteoraddon;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.venomous.meteoraddon.utils.MovementMath;

import static org.junit.jupiter.api.Assertions.*;

class MovementMathPropertyTest {
    @Property
    void clampKeepsValueInRange(
        @ForAll @IntRange(min = -1000, max = 1000) int value,
        @ForAll @IntRange(min = -1000, max = 1000) int a,
        @ForAll @IntRange(min = -1000, max = 1000) int b
    ) {
        double min = Math.min(a, b);
        double max = Math.max(a, b);

        double clamped = MovementMath.clamp(value, min, max);

        assertTrue(clamped >= min);
        assertTrue(clamped <= max);
    }
}
```

Use properties for invariants that should hold for every input (clamping, symmetry, ordering,
idempotence, round-trips). Keep example-based JUnit tests for precise expected values and
error cases.

## Coverage

```bash
./gradlew test jacocoTestReport
```

The build applies the JaCoCo plugin with tool version **0.8.14** (the first release that
officially supports Java 25 class files; 0.8.12 fails with "Unsupported class file major
version 69"). `jacocoTestReport` depends on `test`, produces XML and HTML reports, and is
wired to the `build/reports/jacoco/test/` paths listed above.

CI uploads the XML report with `codecov/codecov-action@v4`. Coverage is not currently a hard
percentage gate; the report is informational until a threshold is agreed.

## CI gates

`.github/workflows/build.yml` runs, in order:

1. **Reject broken module files** — fails if any `src/**/*.broken` exists.
2. `./gradlew build` — compiles, tests, and assembles the jar.
3. `./gradlew test jacocoTestReport` — re-runs tests and writes coverage.
4. `./gradlew generateModulesDoc` — regenerates `docs/MODULES.md`.
5. `git diff --exit-code -- docs/MODULES.md` — fails if the committed module index is stale.
6. Uploads the jar and `build/reports/jacoco/test/jacocoTestReport.xml`.

## Ownership

`src/test/**` is Track B's area. Build/test configuration (`build.gradle.kts`,
`gradle/libs.versions.toml`, CI) is Track A's. If a test fails because it parses source text that
Track A changed (for example the registry migration), report it to Track B instead of editing the
test from a Track A change.
