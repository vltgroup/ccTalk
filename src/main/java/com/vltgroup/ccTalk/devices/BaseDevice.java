package com.vltgroup.ccTalk.devices;

import com.vltgroup.ccTalk.bus.Bus;
import com.vltgroup.ccTalk.bus.DeviceInfo;
import com.vltgroup.ccTalk.commands.Command;
import com.vltgroup.ccTalk.commands.CommandHeader;
import com.vltgroup.ccTalk.commands.Responce;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseDevice implements Runnable{
  private static final Logger log = LoggerFactory.getLogger(BaseDevice.class.getName());
  //public static final int MAX_CAHNNEL_COST=5000; // Maximum absolute (NOT in cents) channel cost that will accepted
  protected final long[]   channelCostInCents = new long[17];
  protected final String[] channelCostString = new String[17];
  
  public final        Bus             bus;
  public final        DeviceInfo      info;
  private volatile    boolean         run;
  protected final     ExecutorService eventExecutor;
  
  private   volatile boolean  notRespond;
  protected volatile boolean  m_lastInhibit;
  private final BaseController controller;
  
  public BaseDevice(Bus bus, DeviceInfo info, BaseController controller){
    this.bus=bus;
    this.info=info;
    run=true;
    eventExecutor=Executors.newCachedThreadPool();
    notRespond=false;
    m_lastInhibit=true;
    this.controller=controller;
  }
  
  public String[] getChannelCost(){
    return channelCostString;
  }
  
  protected boolean getNotRespondStatus(){
    return notRespond;
  }
  
  @Override
  public void run() {
    while(run) {
      try {
        Thread.sleep(info.pollingInterval/2);
        if(notRespond){
          init(true);
          notRespond=false;
          eventExecutor.submit(new Runnable() {
            @Override
            public void run() {
              controller.onDeviceRestored(BaseDevice.this);
            }
          });
        }
        
        if(!notRespond) deviceTick();
      } catch (Exception ex){
        log.warn("",ex);
      }
    }
    setMasterInhibitStatusSync(true);
  }
  public void stop(){
    run=false;
  }

  
  public Responce executeCommandSync(CommandHeader command, int expectedDataLength){
    return executeCommandSync(command, new byte[0],  expectedDataLength);
  }
  
  /**
   * @param expectedDataLength if negative - means does'n matter
   */
  public Responce executeCommandSync(CommandHeader command, byte[] data,  int expectedDataLength){
    Responce responce =bus.executeCommand(new Command(info.address,command, data),-1);
    if(responce == null || !responce.isValid){
      notRespond=true;
      eventExecutor.submit(new Runnable() {
        @Override
        public void run() {
          controller.onDeviceNotRespond(BaseDevice.this);
        }
      });
    }
    return responce;
  }
  
  public void setMasterInhibitStatusSync(boolean inhibit){
    log.info("set inhibit:"+inhibit);
    m_lastInhibit=inhibit;
    executeCommandSync(CommandHeader.ModMasterInhibit, new byte[]{inhibit ? 0 : (byte)1},0);
  }

  public void setMasterInhibitStatusAsync(final boolean inhibit){
    log.info("set inhibit:"+inhibit);
    m_lastInhibit=inhibit;
    eventExecutor.submit(new Runnable() {
      @Override
      public void run() {
        executeCommandSync(CommandHeader.ModMasterInhibit, new byte[]{inhibit ? 0 : (byte)1},0);
      }
    });
  }

  public void reset(int maxAttempToWakeUp, CommandHeader wakeUpCommand, int expectedDataLength){
    log.info("reset "+info.shortString());
    executeCommandSync(CommandHeader.RESET_DEVICE, 0);
    
    log.info("start waiting for response "+info.shortString());
    Responce response;
    
    do {
      if(--maxAttempToWakeUp < 0) throw new RuntimeException("device not answer "+info.shortString());
      try{ Thread.sleep(1000); }catch(InterruptedException ignored){  }
      response = executeCommandSync(wakeUpCommand, expectedDataLength);
      log.info("waiting for response "+info.shortString());
    } while(response == null || !response.isValid);
    log.info("device - answered "+info.shortString());
  }
  
  
  protected abstract void init(boolean makeReset);
  protected abstract void deviceTick();
   
}
