package com.vltgroup.ccTalk.devices;

public interface BillAcceptorController extends BaseController{
  boolean onBillEscrow(BillAcceptor device, long cents);
  void onBillStacked(BillAcceptor device,long cents);
  void onHardwareFatal(BillAcceptor device,String message, int eventCounter, int code);
  void onFraudAttemt(BillAcceptor device,String message, int eventCounter, int code);  
  void onStatus(BillAcceptor device,String message, int eventCounter, int code);
  void onReject(BillAcceptor device,String message, int eventCounter, int code);
}
