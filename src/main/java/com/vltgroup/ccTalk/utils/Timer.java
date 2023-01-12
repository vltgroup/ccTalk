package com.vltgroup.ccTalk.utils;

/**
 * A simple timer class to count milliseconds elapsed between two events.
 */
public class Timer {

  private volatile long started;

  public Timer() {
    started = -1; // -1 = timer not started
  }

  public Timer(boolean start) {
    this();
    if (start) {
      restart();
    }
  }

  /**
   * Starts/restarts the timer
   * @return time elapsed since previous start or 0 if it wasn't running
   */
  public long restart() {
    long elapsed = elapsed();
    started = now();
    return elapsed;
  }

  /**
   * Stops the timer
   * @return number of milliseconds elapsed since start or 0 if not started
   */
  public long stop() {
    long elapsed = elapsed();
    started = -1;
    return elapsed;
  }

  public long elapsed() {
    if (!isRunning()) {
      return 0;
    }
    return now() - started;
  }

  public boolean isRunning() {
    return started != -1;
  }
  private long now() {
    return System.currentTimeMillis();
  }

}
