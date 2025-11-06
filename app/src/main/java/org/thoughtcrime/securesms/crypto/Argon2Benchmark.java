package org.thoughtcrime.securesms.crypto;

import android.os.SystemClock;

import org.signal.argon2.Argon2;
import org.signal.argon2.Argon2Exception;
import org.signal.argon2.MemoryCost;
import org.signal.argon2.Type;
import org.signal.argon2.Version;
import org.signal.core.util.logging.Log;

/**
 * Based on LUKS https://gitlab.com/cryptsetup/cryptsetup/blob/master/lib/crypto_backend/pbkdf_check.c
 */
public class Argon2Benchmark {

  private static final String TAG = Log.tag(Argon2Benchmark.class);

  private static final int NCPU = Runtime.getRuntime().availableProcessors();

  private static final long BENCH_MIN_MS          = 250;
  private static final long BENCH_MIN_MS_FAST     = 10;
  private static final int  BENCH_PERCENT_ATLEAST = 95;
  private static final int  BENCH_PERCENT_ATMOST  = 110;
  private static final int  BENCH_SAMPLES_FAST    = 3;
  private static final int  BENCH_SAMPLES_SLOW    = 1;

  private final int  minIterations;
  private final long minMemory;
  private final long maxMemory;

  private int  parallelism;
  private int  iterations;
  private long memory;
  private long ms;

  public Argon2Benchmark(int minIterations, long minMemory, long maxMemory) {
    this(minIterations, minMemory, maxMemory, (NCPU > 1) ? (NCPU - 1) : 1);
  }

  public Argon2Benchmark(int minIterations, long minMemory, long maxMemory, int parallelism) {
    this.minIterations = minIterations;
    this.minMemory     = minMemory * parallelism;
    this.maxMemory     = maxMemory;
    this.parallelism   = parallelism;
  }

  public int getParallelism() {
    return parallelism;
  }

  public int getIterations() {
    return iterations;
  }

  public long getMemory() {
    return memory;
  }

  public long getElapsedTime() {
    return ms;
  }

  public void run(long targetTimeMillis) throws Argon2Exception {
    iterations = minIterations;
    memory     = minMemory;

    Log.d(TAG, "Running benchmark: target = " + targetTimeMillis + " ms, maxMemory = " + maxMemory + " bytes");

    // 1. Find some small parameters, s. t. ms >= BENCH_MIN_MS:
    while (true) {
      ms = measure(BENCH_SAMPLES_FAST, BENCH_MIN_MS);
      if (ms >= BENCH_MIN_MS) {
        break;
      }
      if (memory == maxMemory) {
        if (ms < BENCH_MIN_MS_FAST) {
          iterations *= 16;
        } else {
          int val = (int) (iterations / ms * BENCH_MIN_MS);
          if (iterations == val) {
            break;
          }
          iterations = val;
        }
      } else {
        if (ms < BENCH_MIN_MS_FAST) {
          memory *= 16;
        } else {
          long val = memory / ms * BENCH_MIN_MS;
          if (val < 8192) {
            val = 8192;
          }
          if (memory == val) {
            break;
          }
          memory = val;
        }
        if (memory > maxMemory) {
          memory = maxMemory;
        }
      }
    }

    long atLeast = targetTimeMillis * BENCH_PERCENT_ATLEAST / 100;
    long atMost  = targetTimeMillis * BENCH_PERCENT_ATMOST  / 100;

    // 2. Use the params obtained in (1.) to estimate the target params.
    // 3. Then repeatedly measure the candidate params and if they fall out of
    // the acceptance range (+-5 %), try to improve the estimate:
    boolean stop;
    do {
      stop = nextParameters(targetTimeMillis);
      ms = measure(BENCH_SAMPLES_SLOW, atLeast);
      if (stop) {
        Log.d(TAG, "Optimal hash parameters exhausted");
        break;
      }
    } while (ms < atLeast || ms > atMost);

    Log.d(TAG, "Memory cost = " + memory + " bytes, iterations = " + iterations + " (took " + ms + " ms)");
  }

  private boolean nextParameters(long targetTime) {
    int  prevIterations = iterations;
    long prevMemory     = memory;

    if (ms > targetTime) {
      // Decreasing, first try to lower t_cost, then m_cost
      int t = (int) (iterations * targetTime / ms);
      if (t < minIterations) {
        memory = (iterations * memory * targetTime) / (minIterations * ms);
        iterations = minIterations;
        if (memory < minMemory) {
          memory = minMemory;
          return true;
        }
      } else {
        iterations = t;
      }
    } else {
      // Increasing, first try to increase m_cost, then t_cost
      long m = memory * targetTime / ms;
      if (m > maxMemory) {
        iterations = (int) ((iterations * memory * targetTime) / (maxMemory * ms));
        memory = maxMemory;
        if (iterations <= minIterations) {
          iterations = minIterations;
          return true;
        }
      } else if (m < minMemory){
        memory = minMemory;
        return true;
      } else {
        memory = m;
      }
    }

    // Do not continue if it is the same as in the previous run
    return prevIterations == iterations && prevMemory == memory;
  }

  private long measure(int samples, long atLeastMillis) throws Argon2Exception {
    byte[] password = "password".getBytes();
    byte[] salt     = "somesalt".getBytes();

    Argon2 argon2 = new Argon2.Builder(Version.V13)
                              .type(Type.Argon2id)
                              .parallelism(parallelism)
                              .iterations(iterations)
                              .memoryCost(MemoryCost.Bytes(memory))
                              .hashRaw(true)
                              .build();

    long minTime = Long.MAX_VALUE;

    for (int i = 0; i < samples; i++) {
      long startTime = SystemClock.elapsedRealtime();

      argon2.hash(password, salt);

      long elapsedTime = SystemClock.elapsedRealtime() - startTime;

      if (elapsedTime < atLeastMillis) {
        minTime = elapsedTime;
        break;
      }
      if (elapsedTime < minTime) {
        minTime = elapsedTime;
      }
    }

    return minTime;
  }
}
