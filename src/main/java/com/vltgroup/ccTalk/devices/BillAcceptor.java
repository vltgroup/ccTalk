package com.vltgroup.ccTalk.devices;

import com.vltgroup.ccTalk.bus.Bus;
import com.vltgroup.ccTalk.bus.DeviceInfo;
import com.vltgroup.ccTalk.bus.DeviceType;

import com.vltgroup.ccTalk.commands.CommandHeader;
import com.vltgroup.ccTalk.commands.Responce;
import static com.vltgroup.ccTalk.devices.BillAcceptorEventCodes.*;
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
      status("dummy after init", 0, StackerOK);
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
          
          log.info(info.shortString()+" channel index{}={} scaling={} decimal={}", channel, channelCostString[channel], scaling,decimal);
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

      if(events.events[i][0] != 0){
        final int channelIndex=events.events[i][0];
        switch(events.events[i][1]){
          case 0:
            loggingEvent("Bill validated correctly and sent to stacker",m_LastEventCounter,channelIndex);
            setMasterInhibitStatusSync(true);
            eventExecutor.submit(() -> {
              controller.onBillStacked(BillAcceptor.this, channelCostInCents[channelIndex]);
            });
            break;
          case 1:
            loggingEvent("Bill validated correctly and held in escrow",m_LastEventCounter,channelIndex);
            m_StartEscrow=System.currentTimeMillis(); 
            eventExecutor.submit(() -> {
              boolean toStack = controller.onBillEscrow(BillAcceptor.this, channelCostInCents[channelIndex]);
              log.info(info.shortString()+" escrow request result: "+toStack);
              
              if(toStack){
                m_StartEscrow=-1;
                executeCommandSync(CommandHeader.RouteBill,  new byte[]{(byte)1},-1);
              }else{
                m_StartEscrow=-1;
                executeCommandSync(CommandHeader.RouteBill, new byte[]{(byte)0},-1);
              }
            });
            break; 
          default: unknownEvent(m_LastEventCounter, events.events[i][0], events.events[i][1]); break; 
        }
      }else{
        final int eventCode=events.events[i][1];
        switch(eventCode){
          case MasterInhibit:           status("Master inhibit active",m_LastEventCounter,eventCode);                   break;
            
          case BillReturned:            status("Bill returned from escrow",m_LastEventCounter,eventCode);               break;
          case BillReject_ByValidation: reject("Invalid bill (due to validation fail)",m_LastEventCounter,eventCode);   break;  
          case BillReject_Transport:    reject("Invalid bill (due to transport problem)",m_LastEventCounter,eventCode); break; 
          case BillReject_Inhibited1:   status("Inhibited bill (on serial)",m_LastEventCounter,eventCode);              break; 
          case BillReject_Inhibited2:   status("Inhibited bill (on DIP switches)",m_LastEventCounter,eventCode);        break; 

          case StackerOK:               status("Stacker OK",m_LastEventCounter,eventCode);                              break;
          case StackerRremoved:         status("Stacker removed",m_LastEventCounter,eventCode);                         break;
          case StackerInserted:         status("Stacker inserted",m_LastEventCounter,eventCode);                        break;
          case StackerFull:             status("Stacker full",m_LastEventCounter,eventCode);                            break;
            

          case BillJammedInsafe:        hardwareFatal("Bill jammed in transport (unsafe mode)",m_LastEventCounter,eventCode); break; 
          case BillJammedInStacker:     hardwareFatal("Bill jammed in stacker",m_LastEventCounter,eventCode);                 break;             
          case StackerFaulty:           hardwareFatal("Stacker faulty",m_LastEventCounter,eventCode);                         break;
          case StackerJammed:           hardwareFatal("Stacker jammed",m_LastEventCounter,eventCode);                         break; 
          case BillJammedSafe:          hardwareFatal("Bill jammed in transport (safe mode)",m_LastEventCounter,eventCode);   break; 
          case AntiStringFaulty:        hardwareFatal("Anti-string mechanism faulty",m_LastEventCounter,eventCode);           break; 

          case BillPulledBackwards:     fraudAttemt("Bill pulled backwards",m_LastEventCounter,eventCode);                    break; 
          case BillTamper:              fraudAttemt("Bill tamper",m_LastEventCounter,eventCode);                              break;    
          case OptoFraud:               fraudAttemt("Opto fraud detected",m_LastEventCounter,eventCode);                      break; 
          case StringFraud:             fraudAttemt("String fraud detected",m_LastEventCounter,eventCode);                    break; 
          default:unknownEvent(m_LastEventCounter,events.events[i][0], eventCode);                                            break;
        }
      }
    }
    
    if(m_StartEscrow > 0 && System.currentTimeMillis() - m_StartEscrow > 4000){ //for infinite hold
      m_StartEscrow=System.currentTimeMillis() ;
      executeCommandSync(CommandHeader.RouteBill, new byte[]{(byte)0xFF},-1);
    }
  }
  
 
  private void fraudAttemt(final String message, final int eventCounter, final int code){
    loggingEvent(message, eventCounter, code);
    setMasterInhibitStatusSync(true);
    eventExecutor.submit(() -> {
      controller.onFraudAttemt(BillAcceptor.this, message, eventCounter, code);
    });
  }
  
  private void hardwareFatal(final String message, final int eventCounter, final int code){
    loggingEvent(message, eventCounter, code);
    setMasterInhibitStatusSync(true);
    eventExecutor.submit(() -> {
      controller.onHardwareFatal(BillAcceptor.this,message, eventCounter, code);
    });  
  }
  
  private void status(final String message, final int eventCounter, final int code){
    loggingEvent(message, eventCounter, code);
    eventExecutor.submit(() -> {
      controller.onStatus(BillAcceptor.this,message, eventCounter, code);
    });  
  }
  
  private void reject(final String message, final int eventCounter, final int code){
    loggingEvent(message, eventCounter, code);
    eventExecutor.submit(() -> {
      controller.onReject(BillAcceptor.this,message, eventCounter, code);
    });  
  }
  
  private void unknownEvent(final int eventCounter, final int code1, final int code2){
    loggingEvent("billacc unknown event", eventCounter, code1,code2);
    eventExecutor.submit(() -> {
      controller.onUnknownEvent(BillAcceptor.this, eventCounter, code1,code2);
    });  
  }
}
