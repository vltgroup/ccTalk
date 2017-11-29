package com.vltgroup.ccTalk.examplePC;

import com.vltgroup.ccTalk.bus.Bus;
import com.vltgroup.ccTalk.bus.DeviceInfo;
import jssc.SerialPortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
  private static final Logger log = LoggerFactory.getLogger(ComPort.class.getName());
  
  public static void main(String[] args) throws SerialPortException {
    ComPort comPort = new ComPort("/dev/ttyUSB0");
    
    //ComPortPacketer port = new ComPortPacketer(comPort, 40, 100,ComPortPacketer.EchoMode.intelligentEcho);
    
    
    Bus bus = new Bus(comPort , new byte[]{1,2,3,4,5,6});
    DeviceInfo[] billAcceptors = bus.getBillAcceptors();
    DeviceInfo[] coinAcceptors = bus.getCoinAcceptors();
    AcceptorController controler = new AcceptorController();
  
    for(DeviceInfo acc: billAcceptors){
      bus.createBillAcceptor(acc, controler);
    }
    for(DeviceInfo acc: coinAcceptors){
      bus.createCoinAcceptor(acc, controler);
    }
    bus.setMasterInhibitStatusAllDevicesSync(false);
    
    
    while(true){
      try{
      Thread.sleep(1000);
      }catch(InterruptedException ex){
        return;
      }
    }
  }
}
