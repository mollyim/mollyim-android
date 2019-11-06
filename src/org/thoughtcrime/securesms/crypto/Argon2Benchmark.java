package org.thoughtcrime.securesms.crypto;

import org.thoughtcrime.securesms.logging.Log;

/**
 * Based on LUKS cryptsetup performance check.
 */
public class Argon2Benchmark {

  private static final String TAG = Log.tag(Argon2Benchmark.class);

  private static final long BENCH_MIN_MS          = 250;
  private static final long BENCH_MIN_MS_FAST     = 10;
  private static final int  BENCH_PERCENT_ATLEAST = 95;
  private static final int  BENCH_PERCENT_ATMOST  = 110;
  private static final int  BENCH_SAMPLES_FAST    = 3;
  private static final int  BENCH_SAMPLES_SLOW    = 1;

  private final int  minIterations;
  private final long minMemory;
  private final long maxMemory;

  private int  iterations;
  private long memory;
  private long ms;

  public Argon2Benchmark(int minIterations, long minMemory, long maxMemory) {
    this.minIterations = minIterations;
    this.minMemory     = minMemory;
    this.maxMemory     = maxMemory;
  }

  public Argon2Params getParameters() {
    return new Argon2Params(iterations, memory);
  }

  public Argon2Params findParameters(long targetTimeMillis) {
    iterations = minIterations;
    memory     = minMemory;

    Log.d(TAG, "Running benchmark: target time = " + targetTimeMillis + " ms");

    // 1. Find some small parameters, s. t. ms >= BENCH_MIN_MS:
    while (true) {
      ms = measure(getParameters(), BENCH_SAMPLES_FAST, BENCH_MIN_MS);
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
    while (nextParameters(targetTimeMillis)) {
      ms = measure(getParameters(), BENCH_SAMPLES_SLOW, atLeast);
      if (ms >= atLeast && ms <= atMost) {
        break;
      }
    }

    Log.d(TAG, "Memory cost = " + memory + " bytes, iterations = " + iterations + " (took " + ms + " ms)");

    return getParameters();
  }

  private boolean nextParameters(long targetTime) {
    int  prevIterations = iterations;
    long prevMemory     = memory;

    if (ms > targetTime) {
      // Decreasing, first try to lower t_cost, then m_cost
      int val = (int) (iterations * targetTime / ms);
      if (val < minIterations) {
        memory = (iterations * memory * targetTime) / (minIterations * ms);
        iterations = minIterations;
        if (memory < minMemory) {
          memory = minMemory;
          return false;
        }
      } else {
        iterations = val;
      }
    } else {
      // Increasing, first try to increase m_cost, then t_cost
      long val = memory * targetTime / ms;
      if (val > maxMemory) {
        iterations = (int) ((iterations * memory * targetTime) / (maxMemory * ms));
        memory = maxMemory;
        if (iterations <= minIterations) {
          iterations = minIterations;
          return false;
        }
      } else if (val < minMemory){
        memory = minMemory;
        return false;
      } else {
        memory = val;
      }
    }

    // Do not continue if it is the same as in the previous run
    return !(prevIterations == iterations && prevMemory == memory);
  }

  static private long measure(Argon2Params params, int samples, long atLeastMillis) {
    char[] password = "password".toCharArray();
    byte[] salt     = "somesalt".getBytes();

    long minTime = Long.MAX_VALUE;

    for (int i = 0; i < samples; i++) {
      long startTime = System.currentTimeMillis();

      Argon2.generateHash(params, password, salt, 32);

      long elapsedTime = System.currentTimeMillis() - startTime;

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
