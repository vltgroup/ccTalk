package com.vltgroup.ccTalk.comport;

public interface ReceiveCallback {
  void onReceivedData(byte[] data);
}
