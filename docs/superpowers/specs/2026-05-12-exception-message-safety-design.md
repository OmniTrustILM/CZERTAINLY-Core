# Exception Message Safety — Design Spec

**Date:** 2026-05-12
**Branch:** fix/exception-messages
**Scope:** `interfaces-fixes` (PlatformException utility), `core-fixes` (ArchUnit rules + call-site fixes)

---

## Problem

Raw `Exception.getMessage()` calls are forwarded to external-facing surfaces (REST responses,
BulkAction DTOs, CMP PKIFreeText, SCEP responses) without gating on whether the exception was
shaped by the platform. Runtime exceptions from third-party libraries can carry SQL fragments,
internal table names, stack-frame class identifiers, or upstream error detail that must not reach
external callers.

---

## Solution Overview

Three interlocking layers:

1. **Gate utility** — `PlatformException.safeMessage(Throwable, String)` makes the intended contract
   explicit at every call site.
2. **Structural enforcement** — Two ArchUnit rules in `core-fixes` prevent regressions at the REST
   handler layer and enforce exception hierarchy completeness for core-specific exceptions.
3. **Call-site fixes** — All existing violations replaced with `safeMessage()` or hardcoded strings,
   grouped by category below.

---

## Layer 1 — `PlatformException.safeMessage()`

Add a static method to the existing `PlatformException` marker interface in `interfaces-fixes`:

```java
static String safeMessage(Throwable t, String fallback) {
    return (t instanceof PlatformException && t.getMessage() != null)
            ? t.getMessage()
            : fallback;
}
```

**Why on the interface:** The gate and the marker are the same concept. Placing the method on
`PlatformException` makes call sites self-documenting — `PlatformException.safeMessage(ex, "…")`
tells the reader exactly why this method exists. Static interface methods are not inherited, so
callers must always name `PlatformException` explicitly, which keeps the gateway role visible.

**Semantics:** Returns the exception's own message only when it is a platform-shaped exception
(implements `PlatformException`) with a non-null message. For all other exceptions the caller
supplies a specific, safe fallback string. The fallback is per-call (not a global constant) so each
handler can provide a meaningful generic phrase.

**Test:** Add a unit test to `PlatformExceptionTest` in `interfaces-fixes` covering: PlatformException
subtype with message → returns message; PlatformException subtype with null message → returns
fallback; non-PlatformException → returns fallback.

---

## Layer 2 — ArchUnit Rules in `core-fixes`

### Dependency

Add to `core-fixes/pom.xml` test scope:

```xml
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit-junit5</artifactId>
    <version>1.4.0</version>
    <scope>test</scope>
</dependency>
```

### Test class

`src/test/java/com/czertainly/core/ExceptionSafetyTest.java`
(top-level under `com.czertainly.core`, consistent with `ApplicationTests`, `SpringBootConfigurationTests`, etc.)

### Rule 1 — Core exception hierarchy completeness

```java
@ArchTest
ArchRule coreExceptionsMustImplementPlatformException =
    classes()
        .that().resideInAPackage("com.czertainly.core..")
        .and().areAssignableTo(Throwable.class)
        .and().areNotInterfaces()
        .and().areNotAbstract()
        .should().implement(PlatformException.class)
        .because("all platform exceptions must implement PlatformException " +
                 "so safeMessage() can gate message exposure at wire boundaries");
```

Covers the gap in `PlatformExceptionTest` (interfaces-fixes): that test scans `com.czertainly.core`
but core-fixes is not on the interfaces classpath, so the scan returns empty silently. Rule 1 closes
that gap by running against the actual core-fixes compiled classes.

Affected core exceptions that need the marker added:
- `CzertainlyAuthenticationException`
- `ProvisioningException`
- `ScheduledJobSkippedException`
- `IntuneClientException` (and its subclasses `IntuneScepServiceException`,
  `IntuneServiceNotFoundException`, `IntuneClientHttpErrorException`)

### Rule 2 — No direct `getMessage()` in REST advice layer

```java
@ArchTest
ArchRule noRawGetMessageInRestAdvice =
    noMethods()
        .that().areDeclaredInClassesThat()
            .areAnnotatedWith(RestControllerAdvice.class)
        .should().callMethod(Throwable.class, "getMessage")
        .because("exception messages must be gated through " +
                 "PlatformException.safeMessage() before reaching HTTP responses");
```

Forbids every method in every `@RestControllerAdvice` class from calling `Throwable.getMessage()`
directly. After the call-site fixes in `ExceptionHandlingAdvice`, all message extraction goes
through `PlatformException.safeMessage(ex, "…")`. Any future handler that calls `ex.getMessage()`
directly fails the build.

**Limitation:** ArchUnit cannot perform data-flow analysis within method bodies, so it cannot
enforce the `safeMessage()` pattern in service-layer classes (BulkActionMessageDto sites, etc.).
Those are fixed manually; the hierarchy completeness rule (Rule 1) provides a backstop by ensuring
every exception that reaches a catch block implements `PlatformException`, making the
`safeMessage()` gate meaningful.

---

## Layer 3 — Call-site Fixes

### Category A — `ExceptionHandlingAdvice` (REST handlers, ~30 sites)

All `@ExceptionHandler` methods in `ExceptionHandlingAdvice` that currently call `ex.getMessage()`
directly are updated to use `PlatformException.safeMessage(ex, "<specific fallback>")`.

For handlers whose parameter type implements `PlatformException` (NotFoundException,
AlreadyExistException, AttributeException, LocationException, etc.) `safeMessage()` returns
`ex.getMessage()` unchanged — no functional difference, just uniform pattern.

For handlers whose parameter type does NOT implement `PlatformException`, `safeMessage()` returns
the fallback string. These handlers need meaningful fallbacks:

| Exception type | Current (unsafe) | After fix |
|---|---|---|
| `NoHandlerFoundException` | `ex.getMessage()` | `"Requested endpoint not found"` |
| `HttpRequestMethodNotSupportedException` | `ex.getMessage()` | `"HTTP method not supported"` |
| `IllegalArgumentException` | `ex.getMessage()` | `"Invalid argument"` |
| `MethodArgumentTypeMismatchException` | `ex.getMessage()` | `"Invalid argument type"` |
| `MissingRequestValueException` | `ex.getMessage()` | `"Required request value is missing"` |
| `ConnectException` | `ex.getMessage()` | `"Connection to external service failed"` |
| `AccessDeniedException` (else branch) | `ex.getMessage()` | `"Access denied"` |
| `WebClientRequestException` | `ex.getMessage()` | `"External service request failed"` |
| `CertificateException` (j.s.cert) | `ex.getMessage()` + `cause.getMessage()` | `"Certificate error"` (no cause message) |

For `ScepException` and `CertificateRequestException` handlers that additionally append
`ex.getCause().getMessage()`: drop the cause message. The cause is recorded in the server log
(the handler already logs at INFO/ERROR before returning); the wire response needs only the
top-level `safeMessage()` result.

### Category B — `BulkActionMessageDto` in service implementations (13+ sites)

Pattern: `catch (Exception e) { messages.add(new BulkActionMessageDto(uuid, name, e.getMessage())); }`

Files: `AuthorityInstanceServiceImpl`, `CbomServiceImpl`, `ScepProfileServiceImpl`,
`AcmeProfileServiceImpl`, `CmpProfileServiceImpl`, `TokenInstanceServiceImpl`,
`ConnectorServiceImpl`, `ComplianceProfileServiceImpl`.

Fix: replace `e.getMessage()` with `PlatformException.safeMessage(e, "Operation failed")` (or a
more specific string where context is clear, e.g. `"Delete failed"`, `"Force-delete failed"`).

### Category C — `CertificateValidationCheckDto` and `ConnectInfo` (5 sites)

Files: `X509CertificateValidator` (3 sites), `ConnectorServiceImpl` (2 sites).

Fix: replace `e.getMessage()` with `PlatformException.safeMessage(e, "<context-specific fallback>")`.
Examples: `"OCSP URL retrieval failed"`, `"CRL retrieval failed"`, `"Connector connection failed"`.

### Category D — CMP protocol: `CmpProcessingException` construction with embedded raw messages (3 sites)

Files: `PollReqMessageHandler`, `CertConfirmMessageHandler`.

These are a **second-order problem**: `CmpProcessingException` implements `PlatformException` (via
`CmpBaseException`), so `safeMessage()` would expose its message at the wire boundary. But the
message was constructed by interpolating a raw third-party exception message:

```java
// Before (unsafe — raw CertificateException/Exception message baked in)
throw new CmpProcessingException(tid, PKIFailureInfo.systemFailure,
        "failed to parse stored certificate: " + e.getMessage(), e);

// After (safe — static diagnostic, original exception preserved as cause)
throw new CmpProcessingException(tid, PKIFailureInfo.systemFailure,
        "failed to parse stored certificate", e);
```

The original exception is already passed as `cause`, so full detail is available in the server log.

Sites:
- `PollReqMessageHandler:167` — `"failed to parse stored certificate: " + e.getMessage()`
- `PollReqMessageHandler:189` — `"failed to build cert-ready response: " + e.getMessage()`
- `CertConfirmMessageHandler:177` — `"problem to compute certificate fingerprint (cert hash): " + e.getMessage()`

### Category E — SCEP: `ScepException` construction with embedded raw messages (2 sites)

File: `ScepServiceImpl`.

Same second-order problem as Category D.

```java
// Before
return buildResponse(scepRequest, buildFailedResponse(
        new ScepException("Unable to decrypt the data. " + e.getMessage(), FailInfo.BAD_REQUEST), ...));

// After
return buildResponse(scepRequest, buildFailedResponse(
        new ScepException("Unable to decrypt the data", FailInfo.BAD_REQUEST, e), ...));

// Before
throw new ScepException("Exception when verifying signature." + e.getMessage());

// After
throw new ScepException("Exception when verifying signature", e);
```

Verify that `ScepException` has a constructor accepting a `Throwable` cause; add one if not.

---

## Testing

| Layer | Test | Location |
|---|---|---|
| `safeMessage()` unit tests | `PlatformExceptionTest` | `interfaces-fixes` (extend existing) |
| Rule 1 — hierarchy completeness | `ExceptionSafetyTest` | `core-fixes` |
| Rule 2 — no `getMessage()` in advice | `ExceptionSafetyTest` | `core-fixes` |
| `ExceptionHandlingAdvice` handler behaviour | Existing handler tests | `core-fixes` (verify fallback strings) |

The ArchUnit rules are plain JUnit 5 tests — no Spring context needed, fast to run.

---

## Out of scope

- CMP `RevocationMessageHandler` — already uses the correct `instanceof CmpProcessingException` guard.
- ACME service layer — already logs raw messages without forwarding them to the ACME response.
- Logging calls (`LOG.error("...", e.getMessage())`) in service classes — these are not wire-facing
  and are not changed by this work.
- Connector or other downstream service modules — they consume `PlatformException.safeMessage()`
  from interfaces-fixes if needed; enforced by their own tests/reviews.

---

## Change summary by module

| Module | Changes |
|---|---|
| `interfaces-fixes` | Add `safeMessage()` to `PlatformException`; extend `PlatformExceptionTest` |
| `core-fixes` | Add `archunit-junit5` test dep; add `ExceptionSafetyTest` with 2 rules; add `PlatformException` to 5 core exception classes; fix ~45 call sites: ~30 in `ExceptionHandlingAdvice`, 13 in 8 service impls (BulkActionMessageDto), 3 in `X509CertificateValidator`/`ConnectorServiceImpl`, 3 in CMP handlers, 2 in `ScepServiceImpl` |
