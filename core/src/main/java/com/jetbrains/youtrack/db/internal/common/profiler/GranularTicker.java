package com.jetbrains.youtrack.db.internal.common.profiler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang.Validate;

/**
 * Default implementation of {@link Ticker} that updates its internal time at a certain granularity
 * in a separate thread.
 */
public class GranularTicker implements Ticker, AutoCloseable {

  private final long granularity;
  private volatile long time;

  private final ScheduledExecutorService executor;

  public GranularTicker(long granularity) {
    this.executor = Executors.newSingleThreadScheduledExecutor();
    this.granularity = granularity;
  }

  @Override
  public void start() {
    Validate.isTrue(time == 0, "Ticker is already started");
    this.time = System.nanoTime();
    executor.scheduleAtFixedRate(
        () -> time = System.nanoTime(),
        0, granularity, TimeUnit.NANOSECONDS
    );
  }

  @Override
  public long lastNanoTime() {
    return time;
  }

  @Override
  public long currentNanoTime() {
    return System.nanoTime();
  }

  @Override
  public long getTick() {
    return time / granularity;
  }

  @Override
  public long getGranularity() {
    return granularity;
  }

  @Override
  public void stop() {
    executor.shutdown();
  }

  @Override
  public void close() throws Exception {
    stop();
  }
}
