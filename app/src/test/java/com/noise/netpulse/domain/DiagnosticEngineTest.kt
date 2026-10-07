package com.noise.netpulse.domain

import com.noise.netpulse.domain.engine.DiagnosticEngine
import com.noise.netpulse.domain.engine.LatencyAnalyzer
import com.noise.netpulse.domain.engine.ReliabilityAnalyzer
import com.noise.netpulse.domain.model.ConnectionState
import com.noise.netpulse.domain.model.DiagnosticStep
import com.noise.netpulse.domain.model.DnsResolverResult
import com.noise.netpulse.domain.model.DnsResult
import com.noise.netpulse.domain.model.DnsSystemResult
import com.noise.netpulse.domain.model.EndpointProbe
import com.noise.netpulse.domain.model.GatewayResult
import com.noise.netpulse.domain.model.HttpErrorType
import com.noise.netpulse.domain.model.HttpsResult
import com.noise.netpulse.domain.model.NetworkSnapshot
import com.noise.netpulse.domain.model.NetworkType
import com.noise.netpulse.domain.model.Severity
import com.noise.netpulse.domain.model.StepStatus
import com.noise.netpulse.domain.scoring.HealthScoreCalculator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LatencyAnalyzerTest {
    @Test
    fun `local classification uses local thresholds`() {
        assertEquals(LatencyAnalyzer.LatencyGrade.EXCELLENT, LatencyAnalyzer.classifyLocal(20))
        assertEquals(LatencyAnalyzer.LatencyGrade.GOOD, LatencyAnalyzer.classifyLocal(50))
        assertEquals(LatencyAnalyzer.LatencyGrade.FAIR, LatencyAnalyzer.classifyLocal(100))
        assertEquals(LatencyAnalyzer.LatencyGrade.HIGH, LatencyAnalyzer.classifyLocal(200))
        assertEquals(LatencyAnalyzer.LatencyGrade.VERY_HIGH, LatencyAnalyzer.classifyLocal(201))
    }

    @Test
    fun `internet classification uses internet thresholds`() {
        assertEquals(LatencyAnalyzer.LatencyGrade.EXCELLENT, LatencyAnalyzer.classifyInternet(50))
        assertEquals(LatencyAnalyzer.LatencyGrade.GOOD, LatencyAnalyzer.classifyInternet(100))
        assertEquals(LatencyAnalyzer.LatencyGrade.VERY_HIGH, LatencyAnalyzer.classifyInternet(401))
    }

    @Test
    fun `null latency is unavailable`() {
        assertNull(LatencyAnalyzer.classifyLocal(null))
        assertEquals("Unavailable", LatencyAnalyzer.label(null))
    }
}

class ReliabilityAnalyzerTest {
    @Test
    fun `counts successes failures and timeouts`() = runBlocking {
        val analyzer = ReliabilityAnalyzer()
        var call = 0
        val result = analyzer.run(requests = 4) {
            call++
            when (call) {
                1 -> 30L to false
                2 -> null to true   // timeout
                3 -> 40L to false
                else -> null to false
            }
        }
        assertEquals(4, result.requests)
        assertEquals(2, result.successful)
        assertEquals(2, result.failed)
        assertEquals(1, result.timeouts)
        assertEquals(50.0, result.successRatePercent!!, 0.01)
        assertFalse(result.allTimeouts)
    }

    @Test
    fun `all timeouts is detected`() = runBlocking {
        val analyzer = ReliabilityAnalyzer()
        val result = analyzer.run(requests = 3) { null to true }
        assertTrue(result.allTimeouts)
        assertNull(result.jitterMs)
        assertNull(result.medianLatencyMs)
    }
}

class DiagnosticEngineTest {

    private fun wifiConnected() = NetworkSnapshot(NetworkType.WIFI, ConnectionState.CONNECTED)

    private fun healthyHttps() = HttpsResult(
        probes = listOf(
            EndpointProbe("https://a", success = true, statusCode = 204, latencyMs = 40),
            EndpointProbe("https://b", success = true, statusCode = 200, latencyMs = 60),
            EndpointProbe("https://c", success = true, statusCode = 200, latencyMs = 50),
        ),
        successCount = 3,
        failureCount = 0,
        bestLatencyMs = 40,
        primaryFailure = null,
    )

    @Test
    fun `no network produces error and skips remaining steps`() {
        val report = DiagnosticEngine.diagnose(
            network = null, gateway = null, dns = null, internet = null,
            https = null, reliability = null, epochNowMs = 100, durationMs = 10,
        )
        assertEquals(StepStatus.FAILED, report.stepStatuses[DiagnosticStep.CONNECTION])
        assertEquals(StepStatus.SKIPPED, report.stepStatuses[DiagnosticStep.GATEWAY])
        assertTrue(report.findings.any { it.severity == Severity.ERROR })
        assertEquals(1, report.findings.size)
    }

    @Test
    fun `healthy evidence yields no warnings and healthy summary`() {
        val dns = DnsResult(
            hostname = "test",
            resolvers = listOf(DnsResolverResult("1.1.1.1", success = true, latencyMs = 15, resolvedIps = listOf("1.2.3.4"))),
            systemResolution = DnsSystemResult(true, latencyMs = 18, resolvedCount = 1),
        )
        val gateway = GatewayResult("192.168.1.1", reachable = true, latencyMs = 3, method = "ICMP echo")
        val report = DiagnosticEngine.diagnose(
            network = wifiConnected(), gateway = gateway, dns = dns,
            internet = healthyHttps(), https = healthyHttps(),
            reliability = null, epochNowMs = 100, durationMs = 10,
        )
        assertEquals(StepStatus.PASSED, report.stepStatuses[DiagnosticStep.CONNECTION])
        assertEquals(StepStatus.PASSED, report.stepStatuses[DiagnosticStep.GATEWAY])
        assertEquals(StepStatus.PASSED, report.stepStatuses[DiagnosticStep.DNS])
        assertTrue(report.findings.none { it.severity == Severity.WARNING || it.severity == Severity.ERROR })
        assertTrue(report.summary.contains("healthy"))
    }

    @Test
    fun `high dns latency produces hedged warning with evidence`() {
        val dns = DnsResult(
            hostname = "test",
            resolvers = emptyList(),
            systemResolution = DnsSystemResult(true, latencyMs = 412, resolvedCount = 1),
        )
        val report = DiagnosticEngine.diagnose(
            network = wifiConnected(), gateway = null, dns = dns,
            internet = healthyHttps(), https = healthyHttps(),
            reliability = null, epochNowMs = 100, durationMs = 10,
        )
        val finding = report.findings.first { it.step == DiagnosticStep.DNS }
        assertEquals(Severity.WARNING, finding.severity)
        assertEquals("High DNS latency", finding.title)
        assertTrue(finding.evidence.contains("412"))
        assertTrue(finding.possibleCause.contains("Possible"))
        assertEquals(StepStatus.WARNING, report.stepStatuses[DiagnosticStep.DNS])
    }

    @Test
    fun `gateway unreachable yields error and local problem cause`() {
        val gateway = GatewayResult("192.168.1.1", reachable = false, latencyMs = null, method = "TCP connect", error = "refused")
        val report = DiagnosticEngine.diagnose(
            network = wifiConnected(), gateway = gateway, dns = null,
            internet = healthyHttps(), https = healthyHttps(),
            reliability = null, epochNowMs = 100, durationMs = 10,
        )
        val finding = report.findings.first { it.step == DiagnosticStep.GATEWAY }
        assertEquals(Severity.ERROR, finding.severity)
        assertTrue(finding.possibleCause.contains("local network"))
        assertEquals(StepStatus.FAILED, report.stepStatuses[DiagnosticStep.GATEWAY])
    }

    @Test
    fun `https failure with local success differentiates the failure layer`() {
        val https = HttpsResult(
            probes = listOf(EndpointProbe("https://a", false, null, null, HttpErrorType.TLS_FAILURE, "TLS failed")),
            successCount = 0, failureCount = 1, bestLatencyMs = null,
            primaryFailure = HttpErrorType.TLS_FAILURE,
        )
        val dns = DnsResult(
            hostname = "test", resolvers = emptyList(),
            systemResolution = DnsSystemResult(true, latencyMs = 20, resolvedCount = 1),
        )
        val report = DiagnosticEngine.diagnose(
            network = wifiConnected(), gateway = null, dns = dns,
            internet = https, https = https,
            reliability = null, epochNowMs = 100, durationMs = 10,
        )
        assertEquals(StepStatus.PASSED, report.stepStatuses[DiagnosticStep.DNS])
        assertEquals(StepStatus.FAILED, report.stepStatuses[DiagnosticStep.HTTPS])
        val finding = report.findings.first { it.step == DiagnosticStep.HTTPS }
        assertTrue(finding.title.contains("TLS"))
        assertTrue(!finding.possibleCause.contains("definitely"))
    }

    @Test
    fun `unreliable requests produce warning with accurate terminology`() {
        val result = com.noise.netpulse.domain.model.ReliabilityResult(
            requests = 10, successful = 8, failed = 2, timeouts = 1,
            successRatePercent = 80.0, jitterMs = 5.0, medianLatencyMs = 40, allTimeouts = false,
        )
        val report = DiagnosticEngine.diagnose(
            network = wifiConnected(), gateway = null, dns = null,
            internet = healthyHttps(), https = healthyHttps(),
            reliability = result, epochNowMs = 100, durationMs = 10,
        )
        val finding = report.findings.first { it.step == DiagnosticStep.RELIABILITY }
        assertEquals(Severity.WARNING, finding.severity)
        assertTrue(finding.evidence.contains("8 of 10"))
    }
}

class HealthScoreCalculatorTest {

    private fun wifiConnected() = NetworkSnapshot(NetworkType.WIFI, ConnectionState.CONNECTED)

    @Test
    fun `perfect evidence scores maximum`() {
        val dns = DnsResult(
            hostname = "test", resolvers = emptyList(),
            systemResolution = DnsSystemResult(true, latencyMs = 10, resolvedCount = 1),
        )
        val https = HttpsResult(
            probes = listOf(EndpointProbe("https://a", true, 204, 30)),
            successCount = 1, failureCount = 0, bestLatencyMs = 30, primaryFailure = null,
        )
        val gateway = GatewayResult("192.168.1.1", true, 3, "ICMP echo")
        val reliability = com.noise.netpulse.domain.model.ReliabilityResult(
            10, 10, 0, 0, 100.0, 2.0, 30, false,
        )
        val score = HealthScoreCalculator.calculate(
            network = wifiConnected(), gateway = gateway, dns = dns,
            https = https, reliability = reliability,
        )
        assertEquals(100, score.total)
        assertEquals(100, score.maxTotal)
    }

    @Test
    fun `total failure scores zero`() {
        val dns = DnsResult(
            hostname = "test", resolvers = emptyList(),
            systemResolution = DnsSystemResult(false, latencyMs = null, resolvedCount = 0, error = "fail"),
        )
        val https = HttpsResult(
            probes = listOf(EndpointProbe("https://a", false, null, null, HttpErrorType.TIMEOUT, "t/o")),
            successCount = 0, failureCount = 1, bestLatencyMs = null,
            primaryFailure = HttpErrorType.TIMEOUT,
        )
        val gateway = GatewayResult("192.168.1.1", false, null, "TCP connect", "refused")
        val reliability = com.noise.netpulse.domain.model.ReliabilityResult(
            10, 0, 10, 10, 0.0, null, null, true,
        )
        val score = HealthScoreCalculator.calculate(
            network = NetworkSnapshot(NetworkType.NONE, ConnectionState.DISCONNECTED),
            gateway = gateway, dns = dns, https = https, reliability = reliability,
        )
        assertEquals(0, score.total)
    }

    @Test
    fun `unverifiable components get neutral credit`() {
        val score = HealthScoreCalculator.calculate(
            network = wifiConnected(), gateway = null, dns = null,
            https = null, reliability = null,
        )
        val gatewayComponent = score.components.first { it.name == "Gateway" }
        assertEquals(10, gatewayComponent.score)
        val latencyComponent = score.components.first { it.name == "Latency" }
        assertEquals(10, latencyComponent.score)
        assertTrue(score.total > 0 && score.total < 100)
    }
}
