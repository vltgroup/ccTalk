package com.vltgroup.ccTalk.devices;

import com.vltgroup.ccTalk.bus.Bus;
import com.vltgroup.ccTalk.bus.DeviceInfo;
import com.vltgroup.ccTalk.bus.DeviceType;
import com.vltgroup.ccTalk.commands.CommandHeader;
import com.vltgroup.ccTalk.commands.Response;
import com.vltgroup.ccTalk.utils.Timer;
import lombok.extern.slf4j.Slf4j;

import static com.vltgroup.ccTalk.devices.BillAcceptorEventCodes.AntiStringFaulty;
import static com.vltgroup.ccTalk.devices.BillAcceptorEventCodes.BillJammedInStacker;
import static com.vltgroup.ccTalk.devices.BillAcceptorEventCodes.BillJammedInsafe;
import static com.vltgroup.ccTalk.devices.BillAcceptorEventCodes.BillJammedSafe;
import static com.vltgroup.ccTalk.devices.BillAcceptorEventCodes.BillPulledBackwards;
import static com.vltgroup.ccTalk.devices.BillAcceptorEventCodes.BillReject_ByValidation;
import static com.vltgroup.ccTalk.devices.BillAcceptorEventCodes.BillReject_Inhibited1;
import static com.vltgroup.ccTalk.devices.BillAcceptorEventCodes.BillReject_Inhibited2;
import static com.vltgroup.ccTalk.devices.BillAcceptorEventCodes.BillReject_Transport;
import static com.vltgroup.ccTalk.devices.BillAcceptorEventCodes.BillReturned;
import static com.vltgroup.ccTalk.devices.BillAcceptorEventCodes.BillTamper;
import static com.vltgroup.ccTalk.devices.BillAcceptorEventCodes.MasterInhibit;
import static com.vltgroup.ccTalk.devices.BillAcceptorEventCodes.OptoFraud;
import static com.vltgroup.ccTalk.devices.BillAcceptorEventCodes.StackerFaulty;
import static com.vltgroup.ccTalk.devices.BillAcceptorEventCodes.StackerFull;
import static com.vltgroup.ccTalk.devices.BillAcceptorEventCodes.StackerInserted;
import static com.vltgroup.ccTalk.devices.BillAcceptorEventCodes.StackerJammed;
import static com.vltgroup.ccTalk.devices.BillAcceptorEventCodes.StackerOK;
import static com.vltgroup.ccTalk.devices.BillAcceptorEventCodes.StackerRemoved;
import static com.vltgroup.ccTalk.devices.BillAcceptorEventCodes.StringFraud;



@Slf4j
public class BillAcceptor extends BaseDevice{

  private int lastEventCounter;
  private final Timer escrowTimer = new Timer(); // Used to issue 'hold' command each 4s - otherwise note can be returned
  private final BillAcceptorController controller;
  
  public BillAcceptor(Bus bus, DeviceInfo info, BillAcceptorController controller){
    super(bus, info,controller);
    if(info.type != DeviceType.BILL_ACC) throw new RuntimeException("invalid type");
    this.controller=controller;
    
    Response response = executeCommandSync(CommandHeader.Read_Buffered_BillEvents, BillEvents.length,false);
    if (response != null && response.isValid) {
      BillEvents events = new BillEvents(response.data,0);  
      init(events.event_counter != 0);
    } else {
      init(true); // but it very strange
    }
  }

  
  @Override
  protected final void init(boolean makeReset){
    if (makeReset){
      reset(5, CommandHeader.Read_Buffered_BillEvents, BillEvents.length);
      if (getNotRespondStatus()) return;
    }

    lastEventCounter = 0;
    escrowTimer.stop();

    queryChannelInfo();
    if(getNotRespondStatus()) return; 
    
    executeCommandSync(CommandHeader.ModifyBillOperatingMode, new byte[]{0x02},0, false);   // escrow mode
    if(getNotRespondStatus()) return; 
    executeCommandSync(CommandHeader.ModInhibitStat,new byte[]{(byte)0xFF,(byte)0xFF},0, false);//TODO: inhibit channel with zero cost
    if(getNotRespondStatus()) return; 
    executeCommandSync(CommandHeader.ModMasterInhibit, new byte[]{lastInhibit ? 0 : (byte)1},0, false);
    if(getNotRespondStatus()) return; 
    
    status("dummy after init", 0, StackerOK);
  }
  
  private void queryChannelInfo() {
    int maxChannel = channelCostInCents.length;
    if (info.productCode.equals("LB BLN 1")) {  //Alberici
      maxChannel = 9;
    }
    
    for(int channel = 1; channel < maxChannel; ++channel){
      Response billId = executeCommandSync(CommandHeader.REQ_BillId, new byte[]{(byte)channel},7, false);
            
      if (billId != null && billId.isValid){
        try{
          channelCostInCents[channel]=Integer.parseInt(new String(billId.data, 2, 4));//*100;
          String channelCostString = new String(billId.data);
          
          byte[] country =java.util.Arrays.copyOfRange(billId.data, 0, 2);
          Response scalingFactor = executeCommandSync(CommandHeader.REQ_ScalingFactor, country,3, false);
          byte[] temp = scalingFactor.data;
          int scaling = ( (temp[1]&0xFF) <<8 )+(temp[0] & 0xFF);
          int decimal = temp[2];
          
          log.info(info.shortString()+" channel index{}={} scaling={} decimal={}", channel, channelCostString, scaling,decimal);
          channelCostInCents[channel]*=scaling;
          if (decimal == 0) {
            channelCostInCents[channel]*=100;   //currency without cents, but we emulate them
            decimal = 2;
          }          
          
          channelCost[channel] = new ChannelCost(channelCostInCents[channel],scaling,decimal,new String(country),channelCostString);
        } catch(Exception ignored) {
          channelCostInCents[channel]=0;
          channelCost[channel]=null;
        }
      } else {
        channelCostInCents[channel]=0;
        channelCost[channel]=null;
      }
    }
  }
  
  @Override
  protected void deviceTick(){
    Response response = executeCommandSync(CommandHeader.Read_Buffered_BillEvents, BillEvents.length);
    if(response == null || !response.isValid)  return;
        
    final BillEvents events = new BillEvents(response.data, lastEventCounter);
    
    for(int i = events.start_index; lastEventCounter != events.event_counter; --i){
      lastEventCounter =events.counter[i];

      if(events.events[i][0] != 0){
        final int channelIndex=events.events[i][0];
        switch(events.events[i][1]){
          case 0:
            loggingEvent("Bill validated correctly and sent to stacker", lastEventCounter,channelIndex);
            setMasterInhibitStatusSync(true);
            eventExecutor.submit(() -> controller.onBillStacked(BillAcceptor.this, channelCostInCents[channelIndex]));
            break;
          case 1:
            loggingEvent("Bill validated correctly and held in escrow", lastEventCounter,channelIndex);
            escrowTimer.restart();

            eventExecutor.submit(() -> {
              boolean toStack = controller.onBillEscrow(BillAcceptor.this, channelCostInCents[channelIndex]);
              log.info(info.shortString()+" escrow request result: "+toStack);
              escrowTimer.stop();
              if (toStack) {
                executeCommandSync(CommandHeader.RouteBill, new byte[]{(byte)1},-1);
              } else {
                executeCommandSync(CommandHeader.RouteBill, new byte[]{(byte)0},-1);
              }
            });
            break; 
          default: unknownEvent(lastEventCounter, events.events[i][0], events.events[i][1]); break;
        }
      } else {
        final int eventCode=events.events[i][1];
        switch(eventCode){
          case MasterInhibit:           status("Master inhibit active", lastEventCounter,eventCode);                   break;
            
          case BillReturned:            status("Bill returned from escrow", lastEventCounter,eventCode);               break;
          case BillReject_ByValidation: reject("Invalid bill (due to validation fail)", lastEventCounter,eventCode);   break;
          case BillReject_Transport:    reject("Invalid bill (due to transport problem)", lastEventCounter,eventCode); break;
          case BillReject_Inhibited1:   status("Inhibited bill (on serial)", lastEventCounter,eventCode);              break;
          case BillReject_Inhibited2:   status("Inhibited bill (on DIP switches)", lastEventCounter,eventCode);        break;

          case StackerOK:               status("Stacker OK", lastEventCounter,eventCode);                              break;
          case StackerRemoved:         status("Stacker removed", lastEventCounter,eventCode);                         break;
          case StackerInserted:         status("Stacker inserted", lastEventCounter,eventCode);                        break;
          case StackerFull:             status("Stacker full", lastEventCounter,eventCode);                            break;
            

          case BillJammedInsafe:        hardwareFatal("Bill jammed in transport (unsafe mode)", lastEventCounter,eventCode); break;
          case BillJammedInStacker:     hardwareFatal("Bill jammed in stacker", lastEventCounter,eventCode);                 break;
          case StackerFaulty:           hardwareFatal("Stacker faulty", lastEventCounter,eventCode);                         break;
          case StackerJammed:           hardwareFatal("Stacker jammed", lastEventCounter,eventCode);                         break;
          case BillJammedSafe:          hardwareFatal("Bill jammed in transport (safe mode)", lastEventCounter,eventCode);   break;
          case AntiStringFaulty:        hardwareFatal("Anti-string mechanism faulty", lastEventCounter,eventCode);           break;

          case BillPulledBackwards:     fraudAttempt("Bill pulled backwards", lastEventCounter,eventCode);                    break;
          case BillTamper:              fraudAttempt("Bill tamper", lastEventCounter,eventCode);                              break;
          case OptoFraud:               fraudAttempt("Opto fraud detected", lastEventCounter,eventCode);                      break;
          case StringFraud:             fraudAttempt("String fraud detected", lastEventCounter,eventCode);                    break;
          default:unknownEvent(lastEventCounter,events.events[i][0], eventCode);                                            break;
        }
      }
    }

    if (escrowTimer.isRunning() && escrowTimer.elapsed() > 4000) {
      escrowTimer.restart(); // restart timer
      executeCommandSync(CommandHeader.RouteBill, new byte[]{(byte)0xFF},-1);
    }
  }
  
 
  private void fraudAttempt(final String message, final int eventCounter, final int code){
    loggingEvent(message, eventCounter, code);
    setMasterInhibitStatusSync(true);
    eventExecutor.submit(() -> controller.onFraudAttempt(BillAcceptor.this, message, eventCounter, code));
  }
  
  private void hardwareFatal(final String message, final int eventCounter, final int code){
    loggingEvent(message, eventCounter, code);
    setMasterInhibitStatusSync(true);
    eventExecutor.submit(() -> controller.onHardwareFatal(BillAcceptor.this,message, eventCounter, code));
  }
  
  private void status(final String message, final int eventCounter, final int code){
    loggingEvent(message, eventCounter, code);
    eventExecutor.submit(() -> controller.onStatus(BillAcceptor.this,message, eventCounter, code));
  }
  
  private void reject(final String message, final int eventCounter, final int code){
    loggingEvent(message, eventCounter, code);
    eventExecutor.submit(() -> controller.onReject(BillAcceptor.this,message, eventCounter, code));
  }
  
  private void unknownEvent(final int eventCounter, final int code1, final int code2){
    loggingEvent("billacc unknown event", eventCounter, code1,code2);
    eventExecutor.submit(() -> controller.onUnknownEvent(BillAcceptor.this, eventCounter, code1,code2));
  }
}
