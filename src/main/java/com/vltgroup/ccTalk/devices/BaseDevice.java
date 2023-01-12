package com.vltgroup.ccTalk.devices;

import com.vltgroup.ccTalk.bus.Bus;
import com.vltgroup.ccTalk.bus.DeviceInfo;
import com.vltgroup.ccTalk.commands.Command;
import com.vltgroup.ccTalk.commands.CommandHeader;
import com.vltgroup.ccTalk.commands.Response;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import com.vltgroup.ccTalk.utils.Timer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class BaseDevice implements Runnable {
  protected final long[] channelCostInCents = new long[16];  // command 231 - Modify inhibit status support only 16 channels
  protected final ChannelCost[] channelCost = new ChannelCost[16];
  
  public final Bus bus;
  public final DeviceInfo info;
  private volatile boolean run;
  protected final ExecutorService eventExecutor;
  
  private volatile boolean notRespond;
  protected volatile boolean lastInhibit;
  private final BaseController controller;
  private final Timer timer = new Timer(true);
  private final Semaphore finishSemaphore;
  private int restoreCounter;
  
  public BaseDevice(Bus bus, DeviceInfo info, BaseController controller){
    this.bus = bus;
    this.info = info;
    run = true;
    eventExecutor = Executors.newCachedThreadPool();
    notRespond = false;
    lastInhibit = true;
    this.controller = controller;
    finishSemaphore = new Semaphore(0);
    restoreCounter = 0;
  }
  
  public ChannelCost[] getChannelCost(){
    return channelCost;
  }
  
  public boolean getNotRespondStatus(){
    return notRespond;
  }
  
  @Override
  public void run() {
    log.info(info.shortString() + " thread started");
    while(run) {
      try {
        Thread.sleep(info.pollingInterval/2);

        if (notRespond) {
          init(true);
          if (notRespond) {
            ++restoreCounter;
            if (restoreCounter >= 3) {
              log.info("failed to restore device at: {}", info.shortString());
              run = false;
              eventExecutor.submit(() -> controller.onDeviceDead(BaseDevice.this));
            }
          } else {
            restoreCounter = 0;
            eventExecutor.submit(() -> controller.onDeviceRestored(BaseDevice.this));
          }
        }
        
        if (!notRespond) {
          deviceTick(); // Do device job!
        }

        log.debug(info.shortString()+" delay between ticks={}ms", timer.restart());

      } catch (Exception ex){
        log.warn("", ex);
      }
    }
    
    setMasterInhibitStatusSync(true, !notRespond);  //call callNotRespond use here for not call onDeviceNotRespond one more time if device dead
    finishSemaphore.release();
  }
  
  public void stop(){
    run = false;
    try {
      finishSemaphore.acquire();
    } catch(InterruptedException ignored){
    }
  }

  public Response executeCommandSync(CommandHeader command, int expectedDataLength, boolean callNotRespond){
    return executeCommandSync(command, new byte[0],  expectedDataLength, callNotRespond);
  }
  
  public Response executeCommandSync(CommandHeader command, int expectedDataLength){
    return executeCommandSync(command, new byte[0],  expectedDataLength, true);
  }
  
  public Response executeCommandSync(CommandHeader command, byte[] data, int expectedDataLength){
    return executeCommandSync(command, data, expectedDataLength, true);
  }

  /**
   * @param expectedDataLength if negative - means doesn't matter
   * @param callNotRespond  call or not onDeviceNotRespond in case if device not respond on command
   */
  public Response executeCommandSync(CommandHeader command, byte[] data, int expectedDataLength, boolean callNotRespond){
    final Response response = bus.executeCommand(new Command(info.address, command, data), expectedDataLength);
    if (response == null || !response.isValid) {
      notRespond = true;
      if (callNotRespond) {
        eventExecutor.submit(() -> controller.onDeviceNotRespond(BaseDevice.this));
      }
    }
    return response;
  }
  
  private void setMasterInhibitStatusSync(boolean inhibit, boolean callNotRespond){
    log.info(info.shortString()+" set inhibit:{}", inhibit);
    lastInhibit = inhibit;
    executeCommandSync(CommandHeader.ModMasterInhibit, new byte[]{inhibit ? 0 : (byte)1},0, callNotRespond);
  }
  
  
  public void setMasterInhibitStatusSync(boolean inhibit){
    setMasterInhibitStatusSync(inhibit,true);
  }

  public void setMasterInhibitStatusAsync(final boolean inhibit){
    log.info(info.shortString() + " set inhibit:{}", inhibit);
    lastInhibit = inhibit;
    eventExecutor.submit(() -> {
      executeCommandSync(CommandHeader.ModMasterInhibit, new byte[]{inhibit ? 0 : (byte)1},0);
    });
  }
  /**
   * set notRespond status - was device successfully response after reset or not
  */
  public void reset(int maxAttemptToWakeUp, CommandHeader wakeUpCommand, int expectedDataLength){
    log.info(info.shortString() + " reset");
    executeCommandSync(CommandHeader.RESET_DEVICE, 0, false);  //not expect for response on RESET command
    log.info(info.shortString() + " start waiting for response after reset");
    
    for(int i = 0; i < maxAttemptToWakeUp; ++i) {
      try{ Thread.sleep(1000); }catch(InterruptedException ignored){  }
      final Response response = executeCommandSync(wakeUpCommand, expectedDataLength, false);  // don't wait mandatory response after RESET command

      // device doesn't answer this mean that it is broken
      if (response != null && response.isValid) {
        log.info(info.shortString()+" device answered after reset");
        notRespond = false;
        return;
      }
      log.info(info.shortString() + " continue waiting for response after reset");
    }
    
    notRespond = true;
    log.info(info.shortString()+ " device not answer after reset");
  }
  
  /**
   * set notRespond status - was device successfully response after reset or not
  */
  protected abstract void init(boolean makeReset);
  protected abstract void deviceTick();

  protected void loggingEvent(String message, int eventCounter, int code){
    log.info(info.shortString()+ " "+message+" eventCounter:{} code:{}", eventCounter, code);
  }
  protected void loggingEvent(String message, int eventCounter, int code1, int code2){
    log.info(info.shortString()+ " "+message+" eventCounter:{} code1:{} code2:{}", eventCounter, code1, code2);
  }
}
