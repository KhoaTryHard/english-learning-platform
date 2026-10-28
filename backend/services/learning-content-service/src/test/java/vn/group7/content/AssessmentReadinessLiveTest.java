package vn.group7.content;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.group7.core.Json;

/** External acceptance: never enabled by regular verify, never supplies an Assessment stub. */
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_READINESS", matches = "1")
class AssessmentReadinessLiveTest {
  @Test void acceptReadinessPhase() throws Exception {
    String phase = System.getenv("READINESS_PHASE");
    ReadinessAcceptance.require(phase != null && Set.of("prepare", "online", "service-down", "database-down", "recovery").contains(phase),
        "Select a readiness phase");
    var input = Path.of(System.getenv("READINESS_MANIFEST"));
    var output = Path.of(System.getenv("READINESS_REPORT"));
    ReadinessAcceptance.require(!Files.exists(output), "Use a fresh report path; preserve previous evidence");
    var manifest = Json.map(Files.readString(input));
    ReadinessAcceptance.validate(manifest, !phase.equals("prepare"));
    var driver = new ReadinessAcceptance(manifest, Path.of(System.getenv("READINESS_PUBLIC_KEYS")),
        Path.of(System.getenv("READINESS_PRIVATE_KEYS")), System.getenv("FRONTEND_ORIGIN"));
    var report = new LinkedHashMap<String, Object>();
    report.put("phase", phase); report.put("checkedAt", Instant.now().toString());
    report.put("backendSha", manifest.get("backendSha")); report.put("assessment", manifest.get("assessment"));
    report.put("fixtureIds", manifest.get("fixtures"));
    report.put("manifestSha256", java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(input))));
    report.put("scope", "External HTTP acceptance; supplied LIVE runtime/DB provenance requires Huy/Khoa review");
    report.put("notCovered", List.of("attempt retry/retake", "Progress/Kafka", "team contract approval"));
    report.put("checks", driver.checks);
    try {
      driver.login(System.getenv().getOrDefault("ADMIN_EMAIL", "admin@example.com"),
          Files.readString(Path.of(System.getenv("READINESS_ADMIN_PASSWORD_FILE"))).strip());
      switch (phase) {
        case "prepare" -> {
          driver.prepare();
          Path prepared = input.resolveSibling(input.getFileName() + ".prepared.json");
          Files.writeString(prepared, Json.text(manifest), StandardOpenOption.CREATE_NEW);
          report.put("preparedManifest", prepared.toString());
        }
        case "online" -> driver.online();
        case "service-down" -> driver.outage(false);
        case "database-down" -> driver.outage(true);
        case "recovery" -> driver.recovery();
      }
      report.put("status", phase.equals("prepare") ? "FIXTURES_CREATED_LIVE_NOT_RUN" : "PHASE_PASS_REVIEW_REQUIRED");
    } catch (Exception failure) {
      report.put("status", "FAIL");
      // Keep raw exceptions/request bodies out of reports. The named case/status is in checks.
      report.put("failureType", failure.getClass().getSimpleName());
      throw failure;
    } finally {
      Files.createDirectories(output.toAbsolutePath().getParent());
      Files.writeString(output, Json.text(report), StandardOpenOption.CREATE_NEW);
    }
  }
}
