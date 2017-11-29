package com.vltgroup.ccTalk.bus;

import com.vltgroup.ccTalk.comport.ComPort;
import com.vltgroup.ccTalk.comport.ReceiveCallback;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ComPortPacketer implements ReceiveCallback{
  private static final Logger log = LoggerFactory.getLogger(ComPortPacketer.class.getName());
  public static enum EchoMode{
    echoOff, hardEchoOn, intelligentEcho
  }
  
  private final ComPort port;
  private final BlockingQueue <byte[]> receivedData;
  private final int betweenBytesTimeout;
  private final int beforeFirstByteTimeout;
  private EchoMode echoMode;
  
  public ComPortPacketer(ComPort port, int betweenBytesTimeout, int beforeFirstByteTimeout, EchoMode echoMode){
    this.port=port;
    this.betweenBytesTimeout = betweenBytesTimeout;
    this.beforeFirstByteTimeout=beforeFirstByteTimeout;
    this.echoMode=echoMode;
    receivedData =  new LinkedBlockingQueue<>();
    port.setReceiveCallback(this);
  }
  
  public boolean detectEcho(byte[] packetForDetectEcho){
    port.sendBytes(packetForDetectEcho);
    ByteArrayOutputStream  echo = new ByteArrayOutputStream();
    
    try{
      byte[] lastData=receivedData.poll(betweenBytesTimeout, TimeUnit.MILLISECONDS);
      while(lastData != null){
        echo.write(lastData);
        lastData = receivedData.poll(betweenBytesTimeout, TimeUnit.MILLISECONDS);
      }
    }catch(InterruptedException | IOException ignored){}
   
    if(echo.size() != 0 && echo.size() != packetForDetectEcho.length) throw new RuntimeException("invalid echo detection"); 
    
    boolean result = (echo.size() != 0);
    log.info("echo detected:{}",result);
    return result;
  }
  
  public void setEchoMode(EchoMode mode){
    this.echoMode=mode;
  }

  synchronized public byte[] sendPacket(byte[] data, int sleepBeforeFirstByte){
    receivedData.clear();     //clear from possible trash
    port.sendBytes(data);
    ByteArrayOutputStream  buf = new ByteArrayOutputStream();
    
    try{
      if(sleepBeforeFirstByte > 0) Thread.sleep(sleepBeforeFirstByte);
      
      if(echoMode == EchoMode.hardEchoOn){
        while(buf.size() <= data.length){                                                //recive echo  
          byte[] tmp =receivedData.poll(betweenBytesTimeout, TimeUnit.MILLISECONDS);
          if(tmp != null) buf.write(tmp);
        }
      }
      byte[] lastData = receivedData.poll(beforeFirstByteTimeout, TimeUnit.MILLISECONDS);
      
      while(lastData != null){            //wait until received all bytes of reply
        buf.write(lastData);
        lastData = receivedData.poll(betweenBytesTimeout, TimeUnit.MILLISECONDS);
      }
    }catch(InterruptedException | IOException ignored){}
    
    byte[] result = buf.toByteArray();
    
    if(echoMode == EchoMode.hardEchoOn){
      if(result.length < data.length) result = new byte[0];
      else result = java.util.Arrays.copyOfRange(result, data.length, result.length);
    }else if(echoMode == EchoMode.intelligentEcho){
      if(result.length >= data.length){
        byte[] possibleEcho = java.util.Arrays.copyOfRange(result, 0, data.length);
        if(java.util.Arrays.equals(data, possibleEcho)){
          result = java.util.Arrays.copyOfRange(result, data.length, result.length);
        }
      }
    }
    
    return result;
  }

  @Override
  public void onReceivedData(byte[] data) {
    try{
      receivedData.put(data);
    }catch(InterruptedException ignored){
    }
  }
}
