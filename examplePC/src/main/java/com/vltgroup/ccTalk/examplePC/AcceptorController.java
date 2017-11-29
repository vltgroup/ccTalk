package com.vltgroup.ccTalk.examplePC;

import com.vltgroup.ccTalk.devices.BaseDevice;
import com.vltgroup.ccTalk.devices.BillAcceptor;
import com.vltgroup.ccTalk.devices.BillAcceptorController;
import com.vltgroup.ccTalk.devices.CoinAcceptor;
import com.vltgroup.ccTalk.devices.CoinAcceptorController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AcceptorController implements BillAcceptorController, CoinAcceptorController{
  private static final Logger log = LoggerFactory.getLogger(AcceptorController.class.getName());

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
  public void onFraudAttemt(BillAcceptor device, String message, int eventCounter, int code) {
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
  public void onFraudAttemt(CoinAcceptor device, String message, int eventCounter, int code) {
    //log.info("{}:{} {}",device.info.type,device.info.address.address,message);
  }

  @Override
  public void onStatus(CoinAcceptor device, String message, int eventCounter, int code) {
    //log.info("{}:{} {}",device.info.type,device.info.address.address,message);
  }

  @Override
  public void onCoinInsertedTooQuikly(CoinAcceptor device, String message, int eventCounter, int code) {
    //log.info("{}:{} {}",device.info.type,device.info.address.address,message);
  }

  @Override
  public void onDeviceNotRespond(BaseDevice device) {
    //log.info("{}:{} {}",device.info.type,device.info.address.address);
  }

  @Override
  public void onDeviceRestored(BaseDevice device) {
    //log.info("{}:{} {}",device.info.type,device.info.address.address);
  }
  
}
