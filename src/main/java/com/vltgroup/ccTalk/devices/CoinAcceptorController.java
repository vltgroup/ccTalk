package com.vltgroup.ccTalk.devices;

public interface CoinAcceptorController extends BaseController {
  void onCoinAccepted(CoinAcceptor device, long cents);
  void onHardwareFatal(CoinAcceptor device, String message, int eventCounter, int code);
  void onFraudAttempt(CoinAcceptor device, String message, int eventCounter, int code);
  void onStatus(CoinAcceptor device, String message, int eventCounter, int code);
  void onCoinInsertedTooQuickly(CoinAcceptor device, String message, int eventCounter, int code);
}
