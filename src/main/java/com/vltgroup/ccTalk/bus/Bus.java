package com.vltgroup.ccTalk.bus;

import com.vltgroup.ccTalk.comport.ComPort;
import static com.vltgroup.ccTalk.commands.BNVEncode.IsValidBNVCode;
import com.vltgroup.ccTalk.commands.Command;
import static com.vltgroup.ccTalk.commands.Command.bytesToHex;
import com.vltgroup.ccTalk.commands.Responce;
import com.vltgroup.ccTalk.commands.CommandHeader;
import com.vltgroup.ccTalk.devices.BaseDevice;
import com.vltgroup.ccTalk.devices.BillAcceptor;
import com.vltgroup.ccTalk.devices.BillAcceptorController;
import com.vltgroup.ccTalk.devices.CoinAcceptor;
import com.vltgroup.ccTalk.devices.CoinAcceptorController;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Bus implements Closeable{
  private static final Logger log = LoggerFactory.getLogger(Bus.class.getName());
  
  private final ComPortPacketer port;
  private final ReentrantLock fairLock;
  private final Map<Address, DeviceMode> busMode = new HashMap<>();
  protected final ExecutorService devicesExecutor;
  protected final Map<Address,BaseDevice> activeDevices = new HashMap<>();
  
  private final DeviceInfo[] billAcc;
  private final DeviceInfo[] coinAcc;
  private final DeviceInfo[] hopper;

  private final byte[] BNVCode;
  
  private static final int betweenBytesTimeout=50;       //ccTalk part 1 v4.6   11.1
  private static final int beforeFirstByteTimeout=100;   //ccTalk standart says that it can be even 2 seconds - but it unusable, because ccTalk devices don't have any anticollision logic
    
  public Bus(ComPort port, byte[] BNVCode) {
    fairLock=new ReentrantLock(true);
    this.port = new ComPortPacketer(port, betweenBytesTimeout, beforeFirstByteTimeout,ComPortPacketer.EchoMode.intelligentEcho);
    //Command packetForDetectEcho = new Command(Address.ccHostDevId, CommandHeader.SIMPLE_POLL);
    //this.port.detectEcho(packetForDetectEcho.getBytesCRC8());
    this.BNVCode = BNVCode;
    
    log.info("scan CRC8:");
    for(byte deviceId : scanBus(DeviceMode.CRC8)) {
      busMode.put(new Address(deviceId & 0xFF), DeviceMode.CRC8);
      log.info("find:"+(0xFF & deviceId));
    }
    
    log.info("scan CRC16:");
    for(byte deviceId : scanBus(DeviceMode.CRC16)) {
      busMode.put(new Address(deviceId & 0xFF), DeviceMode.CRC16);
      log.info("find:"+(0xFF & deviceId));
    }
    
    log.info("scan ENCRYPTED:");
    if(IsValidBNVCode(BNVCode)){
      for(byte deviceId : scanBus(DeviceMode.ENCRYPTED)) {
        busMode.put(new Address(deviceId & 0xFF), DeviceMode.ENCRYPTED);
        log.info("find:"+(0xFF & deviceId));
      }
    }
    log.info("scan FINISHED");
    
    
    ArrayList<DeviceInfo> billAcc = new ArrayList<>();
    ArrayList<DeviceInfo> coinAcc = new ArrayList<>();
    ArrayList<DeviceInfo> hopper = new ArrayList<>();
    
    for(Address device: busMode.keySet()){
      Responce resp = executeCommand( new Command(device, CommandHeader.REQ_DEV_CATEGORY_ID), -1);
      if(resp != null && resp.data.length != 0){
        String data = new String(resp.data);
        DeviceMode mode = busMode.get(device);
        
        if(data.equals("Payout") ){
          DeviceInfo info = queryDeviceInfo(device,mode, DeviceType.HOPPER);
          log.info("add Hopper:"+info.toString());
          hopper.add(info);
        }else if(data.equals("Coin Acceptor")){
          DeviceInfo info = queryDeviceInfo(device,mode, DeviceType.COIN_ACC);
          log.info("add Coin Acceptor:"+info.toString());
          coinAcc.add(info);
        }else if(data.equals("Bill Validator")){
          DeviceInfo info = queryDeviceInfo(device,mode, DeviceType.BILL_ACC);
          log.info("add Bill Validator:"+info.toString());
          billAcc.add(info);
        }
      }
    }
    
    this.billAcc=billAcc.toArray(new DeviceInfo[billAcc.size()]);
    this.coinAcc=coinAcc.toArray(new DeviceInfo[coinAcc.size()]);
    this.hopper=hopper.toArray(new DeviceInfo[hopper.size()]);
    
    devicesExecutor = Executors.newCachedThreadPool();
  }

  private byte[] scanBus(DeviceMode mode) {
    byte[] raw = getRawBytes(Command.AddessPoll, mode);
    return port.sendPacket(raw, 1500);
  }
  
  private DeviceInfo queryDeviceInfo(Address device, DeviceMode mode, DeviceType type) {
    Responce ManufacturerId   = executeCommand(new Command(device, CommandHeader.REQ_ManufacturerId), -1);
    Responce ProductCode      = executeCommand(new Command(device, CommandHeader.REQ_ProductCode),    -1);
    Responce SerialNumber     = executeCommand(new Command(device, CommandHeader.REQ_SerialNumber),   -1);
    Responce PollingPriority  = executeCommand(new Command(device, CommandHeader.REQ_PollingPriority),2);
    Responce SoftwareVer      = executeCommand(new Command(device, CommandHeader.REQ_SoftwareVer),    -1);
    
    
    
    byte[] sn = SerialNumber.data;
    long serial=0;
    String manufacturerId = new String(ManufacturerId.data);
    if(manufacturerId.equals("ITL")){
      for(int i=0; i<sn.length; ++i){
        serial = (serial << 8) | (0xFF & sn[i]);
      }
    }else{
      for(int i=sn.length-1; i>=0; --i){
        serial = (serial << 8) | (0xFF & sn[i]);
      }
    }
    
    long[] multiplier = {
      0,
      1,
      10,
      1000,
      60000,
      60 * 60000,
      60 * 60000 * 24,
      60 * 60000 * 24 * 7,
      60 * 60000 * 24 * 30,
      60 * 60000 * 24 * 365
    };
    
    long pollingInterval = -1; //means invalid
    if(PollingPriority.data.length >= 2 && PollingPriority.data[0] >= 1 && PollingPriority.data[0] < multiplier.length){
      pollingInterval = multiplier[ PollingPriority.data[0] ] * (PollingPriority.data[1] & 0xff); 
    }
    
    
    return new DeviceInfo(device, mode, type, manufacturerId, new String(ProductCode.data), new String(SoftwareVer.data),
            serial, pollingInterval);
  }

  public DeviceInfo[] getBillAcceptors() {
    return billAcc;
  }
  public DeviceInfo[] getCoinAcceptors() {
    return coinAcc;
  }
  public DeviceInfo[] getHoppers() {
    return hopper;
  }
  public synchronized BillAcceptor createBillAcceptor(DeviceInfo info, BillAcceptorController controller){
    if(activeDevices.containsKey(info.address)) throw new RuntimeException("try to create device on addres already in use");
    
    log.info("Start creating BillAcceptor");
    BillAcceptor result = new BillAcceptor(this, info, controller);
    devicesExecutor.submit(result);
    activeDevices.put(info.address, result);
    log.info("End creating BillAcceptor, bill acc thread started");
    return result;
  }
  
  public synchronized CoinAcceptor createCoinAcceptor(DeviceInfo info, CoinAcceptorController controller){
    if(activeDevices.containsKey(info.address)) throw new RuntimeException("try to create device on addres already in use");
    
    log.info("Start creating CoinAcceptor");
    CoinAcceptor result = new CoinAcceptor(this, info, controller); 
    devicesExecutor.submit(result);
    activeDevices.put(info.address, result);
    log.info("End creating CoinAcceptor, coin acc thread started");
    return result;
  }
  
  @Override
  public synchronized void close(){
    for(Map.Entry<Address, BaseDevice> dev: activeDevices.entrySet()){
      dev.getValue().stop();
    }
    activeDevices.clear();
  }
  
  public void setMasterInhibitStatusAllDevicesSync(boolean inhibit){
    for(Map.Entry<Address, BaseDevice> dev: activeDevices.entrySet()){
      dev.getValue().setMasterInhibitStatusSync(inhibit);
    }
  }
  
  public void setMasterInhibitStatusAllDevicesAsync(boolean inhibit){
    for(Map.Entry<Address, BaseDevice> dev: activeDevices.entrySet()){
      dev.getValue().setMasterInhibitStatusAsync(inhibit);
    }
  }
  
  
  /**
   * @param expectedDataLength - if negative - means does'n matter
   */
  public Responce executeCommand(Command command, int expectedDataLength) {
    return executeCommand(command,expectedDataLength, 3);
  }
  
  private Responce executeCommand(Command command, int expectedDataLength, int retry) {
    Responce response=null;
    
    do {
      byte[] raw = sendCommand(command);
      DeviceMode mode = busMode.get(command.destination);
      response = Command.decodeCommand(command.destination, raw, mode, BNVCode, expectedDataLength);
    } while(retry-- > 1 && (response==null || !response.isValid)); 
    if(response == null){
      log.info("no respond on command addr={} cmd={} dat={}",command.destination.address, command.command,  bytesToHex(command.data));
    }else if(!response.isValid){
      log.info("respond on command  addr={} cmd={} dat={} is invaid: {} {}",command.destination.address, command.command,  bytesToHex(command.data), response.responceHeader, bytesToHex(response.data));
    }
    return response;
  }
  
  private byte[] sendCommand(Command command) {
    return sendCommand(command, busMode.get(command.destination));
  }

  private byte[] sendCommand(Command command, DeviceMode mode) {
    fairLock.lock();   //fair sync here
    try{
      byte[] raw=getRawBytes(command, mode);
      return port.sendPacket(raw, 0);
    }finally{
      fairLock.unlock();
    }
    
  }
  
  private byte[] getRawBytes(Command command, DeviceMode mode){
    byte[] raw=null;
    switch(mode){
      case CRC8:      raw = command.getBytesCRC8(); break;
      case CRC16:     raw = command.getBytesCRC16(); break;
      case ENCRYPTED: raw = command.getBytesEncrypted(BNVCode); break;
    }
    return raw;
  }
}
