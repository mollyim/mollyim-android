package im.molly.app.benchmarks

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import im.molly.security.MLKEM1024
import im.molly.security.MLDSA87
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureNanoTime

/**
 * Performance benchmark suite for EMMA cryptographic operations
 *
 * Targets (Pixel 8A with Tensor G3):
 * - ML-KEM-1024 KeyGen: < 1.5 ms
 * - ML-KEM-1024 Encaps: < 2.0 ms
 * - ML-KEM-1024 Decaps: < 2.0 ms
 * - ML-DSA-87 KeyGen: < 4.0 ms
 * - ML-DSA-87 Sign: < 6.0 ms
 * - ML-DSA-87 Verify: < 3.0 ms
 */
@RunWith(AndroidJUnit4::class)
class CryptoBenchmarks {

    companion object {
        private const val TAG = "CryptoBenchmark"
        private const val ITERATIONS = 100
        private const val WARMUP_ITERATIONS = 10
    }

    @Test
    fun benchmark_MLKEM1024_KeyGeneration() {
        Log.i(TAG, "=== ML-KEM-1024 Key Generation Benchmark ===")

        // Warmup
        repeat(WARMUP_ITERATIONS) {
            MLKEM1024.generateKeypair()
        }

        // Benchmark
        val times = mutableListOf<Long>()
        repeat(ITERATIONS) {
            val nanos = measureNanoTime {
                MLKEM1024.generateKeypair()
            }
            times.add(nanos)
        }

        val stats = calculateStats(times)
        logStats("ML-KEM-1024 KeyGen", stats)

        // Performance assertion (Pixel 8A target: < 1.5ms)
        assertTrue(
            "ML-KEM-1024 KeyGen too slow: ${stats.avgMs}ms (target: <1.5ms)",
            stats.avgMs < 1.5
        )
    }

    @Test
    fun benchmark_MLKEM1024_Encapsulation() {
        Log.i(TAG, "=== ML-KEM-1024 Encapsulation Benchmark ===")

        val keypair = MLKEM1024.generateKeypair()!!

        // Warmup
        repeat(WARMUP_ITERATIONS) {
            MLKEM1024.encapsulate(keypair.publicKey)
        }

        // Benchmark
        val times = mutableListOf<Long>()
        repeat(ITERATIONS) {
            val nanos = measureNanoTime {
                MLKEM1024.encapsulate(keypair.publicKey)
            }
            times.add(nanos)
        }

        val stats = calculateStats(times)
        logStats("ML-KEM-1024 Encaps", stats)

        assertTrue(
            "ML-KEM-1024 Encaps too slow: ${stats.avgMs}ms (target: <2.0ms)",
            stats.avgMs < 2.0
        )
    }

    @Test
    fun benchmark_MLKEM1024_Decapsulation() {
        Log.i(TAG, "=== ML-KEM-1024 Decapsulation Benchmark ===")

        val keypair = MLKEM1024.generateKeypair()!!
        val encapResult = MLKEM1024.encapsulate(keypair.publicKey)!!

        // Warmup
        repeat(WARMUP_ITERATIONS) {
            MLKEM1024.decapsulate(encapResult.ciphertext, keypair.secretKey)
        }

        // Benchmark
        val times = mutableListOf<Long>()
        repeat(ITERATIONS) {
            val nanos = measureNanoTime {
                MLKEM1024.decapsulate(encapResult.ciphertext, keypair.secretKey)
            }
            times.add(nanos)
        }

        val stats = calculateStats(times)
        logStats("ML-KEM-1024 Decaps", stats)

        assertTrue(
            "ML-KEM-1024 Decaps too slow: ${stats.avgMs}ms (target: <2.0ms)",
            stats.avgMs < 2.0
        )
    }

    @Test
    fun benchmark_MLKEM1024_FullKeyExchange() {
        Log.i(TAG, "=== ML-KEM-1024 Full Key Exchange Benchmark ===")

        // Warmup
        repeat(WARMUP_ITERATIONS) {
            val kp = MLKEM1024.generateKeypair()!!
            val er = MLKEM1024.encapsulate(kp.publicKey)!!
            MLKEM1024.decapsulate(er.ciphertext, kp.secretKey)
        }

        // Benchmark full key exchange (keygen + encaps + decaps)
        val times = mutableListOf<Long>()
        repeat(ITERATIONS) {
            val nanos = measureNanoTime {
                val keypair = MLKEM1024.generateKeypair()!!
                val encapResult = MLKEM1024.encapsulate(keypair.publicKey)!!
                MLKEM1024.decapsulate(encapResult.ciphertext, keypair.secretKey)
            }
            times.add(nanos)
        }

        val stats = calculateStats(times)
        logStats("ML-KEM-1024 Full Exchange", stats)

        assertTrue(
            "ML-KEM-1024 Full Exchange too slow: ${stats.avgMs}ms (target: <5.0ms)",
            stats.avgMs < 5.0
        )
    }

    @Test
    fun benchmark_MLDSA87_KeyGeneration() {
        Log.i(TAG, "=== ML-DSA-87 Key Generation Benchmark ===")

        // Warmup
        repeat(WARMUP_ITERATIONS) {
            MLDSA87.generateKeypair()
        }

        // Benchmark
        val times = mutableListOf<Long>()
        repeat(ITERATIONS) {
            val nanos = measureNanoTime {
                MLDSA87.generateKeypair()
            }
            times.add(nanos)
        }

        val stats = calculateStats(times)
        logStats("ML-DSA-87 KeyGen", stats)

        assertTrue(
            "ML-DSA-87 KeyGen too slow: ${stats.avgMs}ms (target: <4.0ms)",
            stats.avgMs < 4.0
        )
    }

    @Test
    fun benchmark_MLDSA87_Signing_SmallMessage() {
        Log.i(TAG, "=== ML-DSA-87 Signing (256 bytes) Benchmark ===")

        val keypair = MLDSA87.generateKeypair()!!
        val message = ByteArray(256) { it.toByte() }

        // Warmup
        repeat(WARMUP_ITERATIONS) {
            MLDSA87.sign(message, keypair.secretKey)
        }

        // Benchmark
        val times = mutableListOf<Long>()
        repeat(ITERATIONS) {
            val nanos = measureNanoTime {
                MLDSA87.sign(message, keypair.secretKey)
            }
            times.add(nanos)
        }

        val stats = calculateStats(times)
        logStats("ML-DSA-87 Sign (256B)", stats)

        assertTrue(
            "ML-DSA-87 Sign too slow: ${stats.avgMs}ms (target: <6.0ms)",
            stats.avgMs < 6.0
        )
    }

    @Test
    fun benchmark_MLDSA87_Signing_LargeMessage() {
        Log.i(TAG, "=== ML-DSA-87 Signing (8KB) Benchmark ===")

        val keypair = MLDSA87.generateKeypair()!!
        val message = ByteArray(8192) { it.toByte() }

        // Warmup
        repeat(WARMUP_ITERATIONS) {
            MLDSA87.sign(message, keypair.secretKey)
        }

        // Benchmark
        val times = mutableListOf<Long>()
        repeat(ITERATIONS) {
            val nanos = measureNanoTime {
                MLDSA87.sign(message, keypair.secretKey)
            }
            times.add(nanos)
        }

        val stats = calculateStats(times)
        logStats("ML-DSA-87 Sign (8KB)", stats)

        // Large message should still be fast (message is hashed first)
        assertTrue(
            "ML-DSA-87 Sign (large) too slow: ${stats.avgMs}ms (target: <7.0ms)",
            stats.avgMs < 7.0
        )
    }

    @Test
    fun benchmark_MLDSA87_Verification() {
        Log.i(TAG, "=== ML-DSA-87 Verification Benchmark ===")

        val keypair = MLDSA87.generateKeypair()!!
        val message = ByteArray(1024) { it.toByte() }
        val signature = MLDSA87.sign(message, keypair.secretKey)!!

        // Warmup
        repeat(WARMUP_ITERATIONS) {
            MLDSA87.verify(message, signature, keypair.publicKey)
        }

        // Benchmark
        val times = mutableListOf<Long>()
        repeat(ITERATIONS) {
            val nanos = measureNanoTime {
                val valid = MLDSA87.verify(message, signature, keypair.publicKey)
                assertTrue("Signature should be valid", valid)
            }
            times.add(nanos)
        }

        val stats = calculateStats(times)
        logStats("ML-DSA-87 Verify", stats)

        assertTrue(
            "ML-DSA-87 Verify too slow: ${stats.avgMs}ms (target: <3.0ms)",
            stats.avgMs < 3.0
        )
    }

    @Test
    fun benchmark_MLDSA87_FullSignAndVerify() {
        Log.i(TAG, "=== ML-DSA-87 Full Sign+Verify Benchmark ===")

        val keypair = MLDSA87.generateKeypair()!!
        val message = ByteArray(1024) { it.toByte() }

        // Warmup
        repeat(WARMUP_ITERATIONS) {
            val sig = MLDSA87.sign(message, keypair.secretKey)!!
            MLDSA87.verify(message, sig, keypair.publicKey)
        }

        // Benchmark
        val times = mutableListOf<Long>()
        repeat(ITERATIONS) {
            val nanos = measureNanoTime {
                val signature = MLDSA87.sign(message, keypair.secretKey)!!
                val valid = MLDSA87.verify(message, signature, keypair.publicKey)
                assertTrue("Signature should be valid", valid)
            }
            times.add(nanos)
        }

        val stats = calculateStats(times)
        logStats("ML-DSA-87 Sign+Verify", stats)

        assertTrue(
            "ML-DSA-87 Sign+Verify too slow: ${stats.avgMs}ms (target: <9.0ms)",
            stats.avgMs < 9.0
        )
    }

    @Test
    fun benchmark_MemoryUsage_MLKEM() {
        Log.i(TAG, "=== ML-KEM-1024 Memory Usage Benchmark ===")

        val runtime = Runtime.getRuntime()
        System.gc()
        Thread.sleep(100)

        val before = runtime.totalMemory() - runtime.freeMemory()

        // Generate 100 keypairs
        val keypairs = mutableListOf<MLKEM1024.KeyPair>()
        repeat(100) {
            keypairs.add(MLKEM1024.generateKeypair()!!)
        }

        System.gc()
        Thread.sleep(100)

        val after = runtime.totalMemory() - runtime.freeMemory()
        val usedMB = (after - before) / 1024.0 / 1024.0

        Log.i(TAG, "Memory for 100 ML-KEM-1024 keypairs: ${"%.2f".format(usedMB)}MB")
        Log.i(TAG, "Per keypair: ${"%.2f".format(usedMB * 10)}KB")

        // Should be ~0.5 MB (100 * 4.7KB per keypair)
        assertTrue(
            "ML-KEM-1024 memory usage too high: ${"%.2f".format(usedMB)}MB (target: <1.0MB)",
            usedMB < 1.0
        )
    }

    @Test
    fun benchmark_MemoryUsage_MLDSA() {
        Log.i(TAG, "=== ML-DSA-87 Memory Usage Benchmark ===")

        val runtime = Runtime.getRuntime()
        System.gc()
        Thread.sleep(100)

        val before = runtime.totalMemory() - runtime.freeMemory()

        // Generate 100 keypairs
        val keypairs = mutableListOf<MLDSA87.KeyPair>()
        repeat(100) {
            keypairs.add(MLDSA87.generateKeypair()!!)
        }

        System.gc()
        Thread.sleep(100)

        val after = runtime.totalMemory() - runtime.freeMemory()
        val usedMB = (after - before) / 1024.0 / 1024.0

        Log.i(TAG, "Memory for 100 ML-DSA-87 keypairs: ${"%.2f".format(usedMB)}MB")
        Log.i(TAG, "Per keypair: ${"%.2f".format(usedMB * 10)}KB")

        // Should be ~0.75 MB (100 * 7.5KB per keypair)
        assertTrue(
            "ML-DSA-87 memory usage too high: ${"%.2f".format(usedMB)}MB (target: <1.5MB)",
            usedMB < 1.5
        )
    }

    // Helper functions

    data class BenchmarkStats(
        val avgNs: Double,
        val avgMs: Double,
        val minMs: Double,
        val maxMs: Double,
        val medianMs: Double,
        val p95Ms: Double,
        val p99Ms: Double,
        val stdDevMs: Double
    )

    private fun calculateStats(times: List<Long>): BenchmarkStats {
        val sorted = times.sorted()
        val avgNs = times.average()
        val avgMs = avgNs / 1_000_000.0

        val n = sorted.size
        val medianMs = sorted[n / 2] / 1_000_000.0
        val p95Ms = sorted[(n * 0.95).toInt()] / 1_000_000.0
        val p99Ms = sorted[(n * 0.99).toInt()] / 1_000_000.0

        val variance = times.map { (it - avgNs) * (it - avgNs) }.average()
        val stdDevMs = kotlin.math.sqrt(variance) / 1_000_000.0

        return BenchmarkStats(
            avgNs = avgNs,
            avgMs = avgMs,
            minMs = sorted.first() / 1_000_000.0,
            maxMs = sorted.last() / 1_000_000.0,
            medianMs = medianMs,
            p95Ms = p95Ms,
            p99Ms = p99Ms,
            stdDevMs = stdDevMs
        )
    }

    private fun logStats(operation: String, stats: BenchmarkStats) {
        Log.i(TAG, "$operation Statistics:")
        Log.i(TAG, "  Average:   ${"%.3f".format(stats.avgMs)} ms")
        Log.i(TAG, "  Median:    ${"%.3f".format(stats.medianMs)} ms")
        Log.i(TAG, "  Min:       ${"%.3f".format(stats.minMs)} ms")
        Log.i(TAG, "  Max:       ${"%.3f".format(stats.maxMs)} ms")
        Log.i(TAG, "  P95:       ${"%.3f".format(stats.p95Ms)} ms")
        Log.i(TAG, "  P99:       ${"%.3f".format(stats.p99Ms)} ms")
        Log.i(TAG, "  Std Dev:   ${"%.3f".format(stats.stdDevMs)} ms")
    }
}
