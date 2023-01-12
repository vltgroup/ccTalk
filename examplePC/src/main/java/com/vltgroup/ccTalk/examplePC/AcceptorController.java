package com.vltgroup.ccTalk.examplePC;

import com.vltgroup.ccTalk.devices.BaseDevice;
import com.vltgroup.ccTalk.devices.BillAcceptor;
import com.vltgroup.ccTalk.devices.BillAcceptorController;
import com.vltgroup.ccTalk.devices.CoinAcceptor;
import com.vltgroup.ccTalk.devices.CoinAcceptorController;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class AcceptorController implements BillAcceptorController, CoinAcceptorController{

  @Override
  public boolean onBillEscrow(BillAcceptor device, long cents) {
    //log.info("{}:{} bill escrow {}",device.info.type, device.info.address.address, cents);
    return true;
  }

  @Override
  public void onBillStacked(BillAcceptor device, long cents) {
    //log.info("{}:{} bill stacked {}",device.info.type, device.info.address.address,cents);
    device.bus.setMasterInhibitStatusAllDevicesSync(false);
  }

  @Override
  public void onHardwareFatal(BillAcceptor device, String message, int eventCounter, int code) {
    //log.info("{}:{} {}",device.info.type,device.info.address.address,message);
  }

  @Override
  public void onFraudAttempt(BillAcceptor device, String message, int eventCounter, int code) {
    //log.info("{}:{} {}",device.info.type,device.info.address.address,message);
  }

  @Override
  public void onStatus(BillAcceptor device, String message, int eventCounter, int code) {
    //log.info("{}:{} {}",device.info.type,device.info.address.address,message);
  }

  @Override
  public void onCoinAccepted(CoinAcceptor device, long cents) {
    //log.info("{}:{} coin accepted {}",device.info.type,device.info.address.address, cents);
    device.bus.setMasterInhibitStatusAllDevicesSync(false);
  }

  @Override
  public void onHardwareFatal(CoinAcceptor device, String message, int eventCounter, int code) {
    //log.info("{}:{} {}",device.info.type,device.info.address.address,message);
  }

  @Override
  public void onFraudAttempt(CoinAcceptor device, String message, int eventCounter, int code) {
    //log.info("{}:{} {}",device.info.type,device.info.address.address,message);
  }

  @Override
  public void onStatus(CoinAcceptor device, String message, int eventCounter, int code) {
    //log.info("{}:{} {}",device.info.type,device.info.address.address,message);
  }

  @Override
  public void onCoinInsertedTooQuickly(CoinAcceptor device, String message, int eventCounter, int code) {
    //log.info("{}:{} {}",device.info.type,device.info.address.address,message);
  }

  @Override
  public void onDeviceNotRespond(BaseDevice device) {
    //log.info("{}:{}",device.info.type,device.info.address.address);
  }

  @Override
  public void onDeviceRestored(BaseDevice device) {
    //log.info("{}:{}",device.info.type,device.info.address.address);
  }

  @Override
  public void onReject(BillAcceptor device, String message, int eventCounter, int code) {
    //log.info("{}:{} {}",device.info.type,device.info.address.address,message);
  }

  @Override
  public void onUnknownEvent(BaseDevice device, int eventCounter, int code1, int code2) {
    //log.info("{}:{} counter:{} code1:{} code2{}",device.info.type,device.info.address.address,eventCounter, code1, code2);
  }
  @Override
  public void onDeviceDead(BaseDevice device) {
    //log.info("{}:{}",device.info.type,device.info.address.address);
  }
}
