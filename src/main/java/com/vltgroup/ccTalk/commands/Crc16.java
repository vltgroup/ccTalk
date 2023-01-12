package com.vltgroup.ccTalk.commands;

public class Crc16 {
  private int crc = 0;          // initial value
  private final int polynomial = 0x1021;   // 0001 0000 0010 0001  (0, 5, 12)

  public int getCRC() {
      return crc & 0xffff;
  }

  public void update(byte ... bytes) {
    for (byte b : bytes) {
      update(b);
    }
  }

  public void update(byte b) {
    for (int i = 0; i < 8; i++) {
      boolean bit = ((b   >> (7-i) & 1) == 1);
      boolean c15 = ((crc >> 15    & 1) == 1);
      crc <<= 1;
      if (c15 ^ bit) crc ^= polynomial;
    }
  }
}
