package com.vltgroup.ccTalk.devices;

public interface CoinAcceptorController extends BaseController{
  void onCoinAccepted(CoinAcceptor device, long cents);
  void onHardwareFatal(CoinAcceptor device, String message, int eventCounter, int code);
  void onFraudAttemt(CoinAcceptor device, String message, int eventCounter, int code);  
  void onStatus(CoinAcceptor device, String message, int eventCounter, int code);
  void onCoinInsertedTooQuikly(CoinAcceptor device, String message, int eventCounter, int code);
}
