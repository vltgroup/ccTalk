package com.vltgroup.ccTalk.devices;

import com.vltgroup.ccTalk.bus.Bus;
import com.vltgroup.ccTalk.bus.DeviceInfo;
import com.vltgroup.ccTalk.bus.DeviceType;

import com.vltgroup.ccTalk.commands.CommandHeader;
import com.vltgroup.ccTalk.commands.Responce;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class BillAcceptor extends BaseDevice{
  private static final Logger log = LoggerFactory.getLogger(BillAcceptor.class.getName());

  private int               m_LastEventCounter;
  private volatile long     m_StartEscrow;
  private BillAcceptorController controller;
  
  public BillAcceptor(Bus bus, DeviceInfo info, BillAcceptorController controller){
    super(bus, info,controller);
    if(info.type != DeviceType.BILL_ACC) throw new RuntimeException("invalid type");
    this.controller=controller;
    
    Responce response = executeCommandSync(CommandHeader.Read_Buffered_BillEvents, BillEvents.length);
    if(response != null && response.isValid){
      BillEvents events = new BillEvents(response.data,0);  
      init(events.event_counter != 0);
    }else{
      init(true);   //but it very strange
    }
  }

  
  @Override
  protected final void init(boolean makeReset){
    if(makeReset){
      reset(5, CommandHeader.Read_Buffered_BillEvents, BillEvents.length);
    }

    m_LastEventCounter = 0;
    m_StartEscrow = -1;

    queryChannelInfo();
    
    executeCommandSync(CommandHeader.ModifyBillOperatingMode, new byte[]{0x02},0);   // escrow mode
    executeCommandSync(CommandHeader.ModInhibitStat,new byte[]{(byte)0xFF,(byte)0xFF},0);//TODO: inhibit channel with zero cost
    executeCommandSync(CommandHeader.ModMasterInhibit, new byte[]{m_lastInhibit ? 0 : (byte)1},0);
    if(!getNotRespondStatus()){
      status("dummy", 0, STATUS_OK);
    }
  }
  
  private void queryChannelInfo() {
    int maxChannel = channelCostInCents.length;
    if(info.productCode.equals("LB BLN 1")){  //Alberici
      maxChannel=9;
    }
    
    for(int channel = 1; channel < maxChannel; ++channel){
      Responce billId = executeCommandSync(CommandHeader.REQ_BillId, new byte[]{(byte)channel},7);
            
      if (billId != null && billId.isValid){
        try{
          channelCostInCents[channel]=Integer.parseInt(new String(billId.data, 2, 4));//*100;
          channelCostString[channel]=new String(billId.data);
          
          byte[] country =java.util.Arrays.copyOfRange(billId.data, 0, 2);
          Responce scalingFactor = executeCommandSync(CommandHeader.REQ_ScalingFactor, country,3);
          byte[] temp = scalingFactor.data;
          int scaling = ( (temp[1]&0xFF) <<8 )+(temp[0] & 0xFF);
          int decimal = temp[2];
          
          log.info("channel index"+channel+"="+channelCostString[channel] + " scaling="+scaling+" decimal="+decimal);
          channelCostInCents[channel]*=scaling;
          if(decimal == 0){
            channelCostInCents[channel]*=100;   //currency without cents, but we emulate them
          }          
          
        }catch(Exception ignored){
          channelCostInCents[channel]=0;
          channelCostString[channel]=null;
        }
      }else{
        channelCostInCents[channel]=0;
        channelCostString[channel]=null;
      }
    }
  }
  
  @Override
  protected void deviceTick(){
    Responce response = executeCommandSync(CommandHeader.Read_Buffered_BillEvents, BillEvents.length);
    if(response == null || !response.isValid)  return;
        
    final BillEvents events = new BillEvents(response.data,m_LastEventCounter);  
    
    for(int i=events.start_index;m_LastEventCounter != events.event_counter;--i){
      m_LastEventCounter=events.counter[i];
      final int index=i;
      
      if(events.events[index][0] != 0){
        switch(events.events[index][1]){
          case 0:
            loggingEvent("Bill validated correctly and sent to stacker",m_LastEventCounter,events.events[index][0]);
            setMasterInhibitStatusSync(true);
            eventExecutor.submit(new Runnable() {
              @Override
              public void run() {
                controller.onBillStacked(BillAcceptor.this, channelCostInCents[events.events[index][0]]);
              }
            });
            break;
          case 1:
            loggingEvent("Bill validated correctly and held in escrow",m_LastEventCounter,events.events[index][0]);
            m_StartEscrow=System.currentTimeMillis(); 
            eventExecutor.submit(new Runnable() {
              @Override
              public void run() {
                boolean toStack = controller.onBillEscrow(BillAcceptor.this, channelCostInCents[events.events[index][0]]);
                log.info("escrow request result: "+toStack);
                
                if(toStack){
                  m_StartEscrow=-1;
                  executeCommandSync(CommandHeader.RouteBill,  new byte[]{(byte)1},-1);
                }else{
                  m_StartEscrow=-1;
                  executeCommandSync(CommandHeader.RouteBill, new byte[]{(byte)0},-1);
                }
              }
            });
            break; 
          default: hardwareFatal("billacc unknown event "+events.events[index][0],m_LastEventCounter, events.events[index][1]); break; 
        }
      }else{
        switch(events.events[index][1]){
          case 0: status("Master inhibit active",m_LastEventCounter,events.events[index][1]);                           break;
            
          case 1: status("Bill returned from escrow",m_LastEventCounter,events.events[index][1]);                       break;
          case 2: status("Invalid bill (due to validation fail)",m_LastEventCounter,events.events[index][1]);           break;  
          case 3: status("Invalid bill (due to transport problem)",m_LastEventCounter,events.events[index][1]);         break; 
          case 4: status("Inhibited bill (on serial)",m_LastEventCounter,events.events[index][1]);                      break; 
          case 5: status("Inhibited bill (on DIP switches)",m_LastEventCounter,events.events[index][1]);                break; 

          case 10:status("Stacker OK",m_LastEventCounter,events.events[index][1]);                                      break;
          case 11:status("Stacker removed",m_LastEventCounter,events.events[index][1]);                                 break;
          case 12:status("Stacker inserted",m_LastEventCounter,events.events[index][1]);                                break;
          case 14:status("Stacker full",m_LastEventCounter,events.events[index][1]);                                    break;
            

          case 6: hardwareFatal("Bill jammed in transport (unsafe mode)",m_LastEventCounter,events.events[index][1]);   break; 
          case 7: hardwareFatal("Bill jammed in stacker",m_LastEventCounter,events.events[index][1]);                   break;             
          case 13:hardwareFatal("Stacker faulty",m_LastEventCounter,events.events[index][1]);                           break;
          case 15:hardwareFatal("Stacker jammed",m_LastEventCounter,events.events[index][1]);                           break; 
          case 16:hardwareFatal("Bill jammed in transport (safe mode)",m_LastEventCounter,events.events[index][1]);     break; 

          case 8: fraudAttemt("Bill pulled backwards",m_LastEventCounter,events.events[index][1]);                      break; 
          case 9: fraudAttemt("Bill tamper",m_LastEventCounter,events.events[index][1]);                                break;    
          case 17:fraudAttemt("Opto fraud detected",m_LastEventCounter,events.events[index][1]);                        break; 
          case 18:fraudAttemt("String fraud detected",m_LastEventCounter,events.events[index][1]);                      break; 
          default:hardwareFatal("billacc unknown event",m_LastEventCounter, events.events[index][1]);                   break;
        }
      }
    }
    
    if(m_StartEscrow > 0 && System.currentTimeMillis() - m_StartEscrow > 4000){ //for infinite hold
      m_StartEscrow=System.currentTimeMillis() ;
      executeCommandSync(CommandHeader.RouteBill, new byte[]{(byte)0xFF},-1);
    }
  }
  
  private void loggingEvent(String message, int eventCounter, int code){
    log.info(message+" eventCounter:{} code:{}",eventCounter, code);
  }
  
  private void fraudAttemt(final String message, final int eventCounter, final int code){
    loggingEvent(message, eventCounter, code);
    setMasterInhibitStatusSync(true);
    eventExecutor.submit(new Runnable() {
      @Override
      public void run() {
        controller.onFraudAttemt(BillAcceptor.this, message, eventCounter, code);
      }
    });
  }
  
  private void hardwareFatal(final String message, final int eventCounter, final int code){
    loggingEvent(message, eventCounter, code);
    setMasterInhibitStatusSync(true);
    eventExecutor.submit(new Runnable() {
      @Override
      public void run() {
        controller.onHardwareFatal(BillAcceptor.this,message, eventCounter, code);
      }
    });  
  }
  
  public static final int STATUS_OK=10;
  private void status(final String message, final int eventCounter, final int code){
    loggingEvent(message, eventCounter, code);
    eventExecutor.submit(new Runnable() {
      @Override
      public void run() {
        controller.onStatus(BillAcceptor.this,message, eventCounter, code);
      }
    });  
  }
    
}
