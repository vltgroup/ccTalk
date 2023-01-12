package com.vltgroup.ccTalk.examplePC;

import com.vltgroup.ccTalk.bus.Bus;
import com.vltgroup.ccTalk.bus.DeviceInfo;
import jssc.SerialPortException;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class Main {
  
  public static void main(String[] args) throws SerialPortException {
    ComPort comPort = new ComPort("/dev/ttyUSB0");
    
    //ComPortPacketer port = new ComPortPacketer(comPort, 40, 100,ComPortPacketer.EchoMode.intelligentEcho);
    
    
    Bus bus = new Bus(comPort , new byte[]{1,2,3,4,5,6});
    List<DeviceInfo> billAcceptors = bus.getBillAcceptors();
    List<DeviceInfo> coinAcceptors = bus.getCoinAcceptors();
    AcceptorController controller = new AcceptorController();
  
    for(DeviceInfo acc: billAcceptors){
      bus.createBillAcceptor(acc, controller);
    }
    for(DeviceInfo acc: coinAcceptors){
      bus.createCoinAcceptor(acc, controller);
    }
    bus.setMasterInhibitStatusAllDevicesSync(false);

    while(true) {
      try {
        Thread.sleep(1000);
      } catch(InterruptedException ex) {
        return;
      }
    }
  }
}
