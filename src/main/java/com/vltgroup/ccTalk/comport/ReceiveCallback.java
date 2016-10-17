package com.vltgroup.ccTalk.comport;

public interface ReceiveCallback {
  public void onReceivedData(byte[] data);
}
