package com.vltgroup.ccTalk.bus;

import com.vltgroup.ccTalk.commands.Command;
import com.vltgroup.ccTalk.commands.CommandHeader;
import com.vltgroup.ccTalk.commands.Response;
import com.vltgroup.ccTalk.comport.ComPort;
import com.vltgroup.ccTalk.devices.BaseDevice;
import com.vltgroup.ccTalk.devices.BillAcceptor;
import com.vltgroup.ccTalk.devices.BillAcceptorController;
import com.vltgroup.ccTalk.devices.CoinAcceptor;
import com.vltgroup.ccTalk.devices.CoinAcceptorController;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import static com.vltgroup.ccTalk.commands.BNVEncode.isValidBNVCode;
import static com.vltgroup.ccTalk.commands.Command.bytesToHex;

@Slf4j
public class Bus implements Closeable {
  private final ComPortPacketSlicer port;
  private final ReentrantLock fairLock = new ReentrantLock(true);
  private final Map<Address, DeviceMode> busMode = new HashMap<>();
  protected final ExecutorService devicesExecutor;
  protected final Map<Address,BaseDevice> activeDevices = new ConcurrentHashMap<>();

  @Getter
  private final ArrayList<DeviceInfo> billAcceptors = new ArrayList<>();
  @Getter
  private final ArrayList<DeviceInfo> coinAcceptors = new ArrayList<>();
  @Getter
  private final ArrayList<DeviceInfo> hoppers = new ArrayList<>();

  private final byte[] BNVCode;
  
  private static final int betweenBytesTimeout = 50;       //ccTalk part 1 v4.6   11.1
  private static final int beforeFirstByteTimeout = 100;   //ccTalk standard says that it can be even 2 seconds - but it unusable, because ccTalk devices don't have any anticollision logic

  public Bus(ComPort port, byte[] BNVCode) {
    this.port = new ComPortPacketSlicer(port, betweenBytesTimeout, beforeFirstByteTimeout, ComPortPacketSlicer.EchoMode.intelligentEcho);
    this.BNVCode = BNVCode;

    searchDevices(DeviceMode.CRC8);
    searchDevices(DeviceMode.CRC16);

    if (isValidBNVCode(BNVCode)) {
      searchDevices(DeviceMode.ENCRYPTED);
    }

    log.info("Scan finished - device(s) found {}", busMode.values().size());

    for (Address device: busMode.keySet()){
      Response resp = executeCommand( new Command(device, CommandHeader.REQ_DEV_CATEGORY_ID), -1);
      if (resp != null && resp.data.length != 0) {
        String deviceType = new String(resp.data);
        DeviceMode mode = busMode.get(device);
        if (deviceType.equals(DeviceType.HOPPER.getSignature())) {
          final DeviceInfo payout = queryDeviceInfo(device, mode, DeviceType.HOPPER);
          log.info("Adding Hopper: {}", payout);
          hoppers.add(payout);
        } else if (deviceType.equals(DeviceType.COIN_ACC.getSignature())) {
          final DeviceInfo ca = queryDeviceInfo(device, mode, DeviceType.COIN_ACC);
          log.info("Adding Coin Acceptor: {}", ca);
          coinAcceptors.add(ca);
        } else if (deviceType.equals(DeviceType.BILL_ACC.getSignature())) {
          final DeviceInfo ba = queryDeviceInfo(device, mode, DeviceType.BILL_ACC);
          log.info("Adding Bill Validator: {}", ba);
          billAcceptors.add(ba);
        } else {
          log.warn("Found device of unsupported type: {}", deviceType);
        }
      }
    }

    devicesExecutor = Executors.newCachedThreadPool();
  }

  private void searchDevices(DeviceMode deviceMode) {
    log.info("Scanning {}:", deviceMode);
    for (byte deviceId : scanBus(deviceMode)) {
      busMode.put(new Address(deviceId & 0xFF), deviceMode);
      log.info("  + found device w/addr: {}", (0xFF & deviceId));
    }
  }

  private byte[] scanBus(DeviceMode mode) {
    final Command command = Command.AddressPoll;
    log.debug("addr={} cmd={} dat={}", command.destination.getAddress(), command.command, bytesToHex(command.data));
    byte[] raw = getRawBytes(command, mode);
    return port.sendPacket(raw, 1500);
  }
  
  private DeviceInfo queryDeviceInfo(Address device, DeviceMode mode, DeviceType type) {
    Response ManufacturerId   = executeCommand(new Command(device, CommandHeader.REQ_ManufacturerId), -1);
    Response productCode      = executeCommand(new Command(device, CommandHeader.REQ_ProductCode),    -1);
    Response serialNumber     = executeCommand(new Command(device, CommandHeader.REQ_SerialNumber),   -1);
    Response pollingPriority  = executeCommand(new Command(device, CommandHeader.REQ_PollingPriority),2);
    Response softwareVer      = executeCommand(new Command(device, CommandHeader.REQ_SoftwareVer),    -1);

    byte[] sn = serialNumber.data;
    long serial=0;
    String manufacturerId = new String(ManufacturerId.data);
    if (manufacturerId.equals("ITL")) {
      for(int i = 0; i < sn.length; ++i) {
        serial = (serial << 8) | (0xFF & sn[i]);
      }
    } else {
      for(int i = sn.length-1; i >= 0; --i) {
        serial = (serial << 8) | (0xFF & sn[i]);
      }
    }
    
    final long[] multiplier = {
      0,
      1,
      10,
      1000,
      60000,
      60 * 60000L,
      60 * 60000L * 24,
      60 * 60000L * 24 * 7,
      60 * 60000L * 24 * 30,
      60 * 60000L * 24 * 365
    };
    
    long pollingInterval = -1; // means invalid

    if (pollingPriority.data.length >= 2 && pollingPriority.data[0] >= 1 && pollingPriority.data[0] < multiplier.length){
      pollingInterval = multiplier[ pollingPriority.data[0] ] * (pollingPriority.data[1] & 0xff);
    }

    return new DeviceInfo(device,
                          mode,
                          type,
                          manufacturerId,
                          new String(productCode.data),
                          new String(softwareVer.data),
                          serial,
                          pollingInterval
                        );
  }

  private void assertNotRegistered(Address address) {
    if (activeDevices.containsKey(address)) {
      throw new RuntimeException("address already in use");
    }
  }

  /**
   * @return can return null - if device can't be successfully created
   */
  public synchronized BillAcceptor createBillAcceptor(DeviceInfo info, BillAcceptorController controller){
    assertNotRegistered(info.address);
    
    log.info("Creating bill acceptor at: {}", info.address.getAddress());

    final BillAcceptor result = new BillAcceptor(this, info, controller);
    if (result.getNotRespondStatus()) {
      log.info("Failed creating bill acceptor at: {}", info.address.getAddress());
      return null;
    }
    
    activeDevices.put(info.address, result);
    devicesExecutor.submit(()->{
      result.run();
      activeDevices.remove(info.address);
      log.info("Bill acceptor thread stopped, at: {}", info.address.getAddress());
    });
    
    log.info("Bill acceptor created and thread started, at: {}", info.address.getAddress());
    return result;
  }
  
  /**
   * @return can return null - if device can't be successfully created
   */
  public synchronized CoinAcceptor createCoinAcceptor(DeviceInfo info, CoinAcceptorController controller){
    assertNotRegistered(info.address);
    
    log.info("Creating coin acceptor at: {}", info.address.getAddress());

    final CoinAcceptor result = new CoinAcceptor(this, info, controller);
    if (result.getNotRespondStatus()) {
      log.info("Failed creating coin acceptor at: {}", info.address.getAddress());
      return null;
    }
    
    activeDevices.put(info.address, result);

    devicesExecutor.submit(()->{
      result.run();
      activeDevices.remove(info.address);
      log.info("Coin acceptor thread stopped, at: {}", info.address.getAddress());
    });
    
    log.info("Coin acceptor created and thread started, at: {}", info.address.getAddress());
    return result;
  }
  
  @Override
  public synchronized void close() {
    for (BaseDevice device : new ArrayList<>(activeDevices.values())) { // copy array to avoid concurrent modification
      device.stop();
    }
    activeDevices.clear();
  }
  
  public void setMasterInhibitStatusAllDevicesSync(boolean inhibit){
    for (BaseDevice device : activeDevices.values()) {
      device.setMasterInhibitStatusSync(inhibit);
    }
  }
  
  public void setMasterInhibitStatusAllDevicesAsync(boolean inhibit){
    for (BaseDevice device : activeDevices.values()) {
      device.setMasterInhibitStatusAsync(inhibit);
    }
  }
  
  /**
   * @param expectedDataLength - if negative - means doesn't matter
   */
  public Response executeCommand(Command command, int expectedDataLength) {
    return executeCommand(command,expectedDataLength, 3);
  }
  
  private Response executeCommand(Command command, int expectedDataLength, int retry) {
    Response response;
    do {
      byte[] raw = sendCommand(command);
      DeviceMode mode = busMode.get(command.destination);
      response = Command.decodeCommand(command.destination, raw, mode, BNVCode, expectedDataLength);
    } while(retry-- > 1 && (response==null || !response.isValid)); 
    if (response == null) {
      log.info("no response on command addr={} cmd={} dat={}", command.destination.getAddress(), command.command, bytesToHex(command.data));
    } else if (!response.isValid) {
      log.info("response on command addr={} cmd={} dat={} is invalid: {} {}",command.destination.getAddress(), command.command, bytesToHex(command.data), response.getResponseHeader(), bytesToHex(response.data));
    }
    return response;
  }
  
  private byte[] sendCommand(Command command) {
    return sendCommand(command, busMode.get(command.destination));
  }

  private byte[] sendCommand(Command command, DeviceMode mode) {
    log.debug("addr={} cmd={} dat={}", command.destination.getAddress(), command.command, bytesToHex(command.data));
    fairLock.lock();   // fair sync here
    try {
      byte[] raw = getRawBytes(command, mode);
      return port.sendPacket(raw, 0);
    } finally {
      fairLock.unlock();
    }
  }
  
  private byte[] getRawBytes(Command command, DeviceMode mode){
    byte[] raw = null;
    switch(mode){
      case CRC8:      raw = command.getBytesCRC8(); break;
      case CRC16:     raw = command.getBytesCRC16(); break;
      case ENCRYPTED: raw = command.getBytesEncrypted(BNVCode); break;
    }
    return raw;
  }
}
