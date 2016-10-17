package com.vltgroup.ccTalk.comport;

public interface ComPort {
  void sendBytes(byte[] bytes);
  void setReceiveCallback(ReceiveCallback callback);
}
