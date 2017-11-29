package com.vltgroup.ccTalk.devices;

import com.vltgroup.ccTalk.bus.Bus;
import com.vltgroup.ccTalk.bus.DeviceInfo;
import com.vltgroup.ccTalk.bus.DeviceType;
import com.vltgroup.ccTalk.commands.CommandHeader;
import com.vltgroup.ccTalk.commands.Responce;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CoinAcceptor extends BaseDevice{
  private static final Logger log = LoggerFactory.getLogger(CoinAcceptor.class.getName());
  
  private int                           m_LastEventCounter;
  private final CoinAcceptorController  controller;
  
  public CoinAcceptor(Bus bus, DeviceInfo info, CoinAcceptorController controller){
    super(bus, info, controller);
    if(info.type != DeviceType.COIN_ACC) throw new RuntimeException("invalid type");
    this.controller=controller;
    
    Responce response = executeCommandSync(CommandHeader.Read_Buff_Credit, BillEvents.length);
    if(response != null && response.isValid){
      BillEvents events = new BillEvents(response.data,0);  
      init(events.event_counter != 0);
    }else{
      init(true);   //but it very strange
    }
  }
  

  @Override
  protected final void init(boolean makeReset) {
    if(makeReset){
      reset(5, CommandHeader.Read_Buff_Credit, BillEvents.length);
    }

    m_LastEventCounter=0;
    
    QueryChannelInfo();
    
    executeCommandSync(CommandHeader.ModInhibitStat,new byte[]{(byte)0xFF,(byte)0xFF},0);//TODO: inhibit channel with zero cost
    executeCommandSync(CommandHeader.ModMasterInhibit, new byte[]{m_lastInhibit ? 0 : (byte)1},0);
    
    if(!getNotRespondStatus()){
      status("dummy", 0, STATUS_OK);
    }
  } 
  
  private void QueryChannelInfo() {
    //Responce temp = executeCommandSync(CommandHeader.REQ_InhibitStat, -1); //just to know channel amount
    //int channelAmount = temp.data.length < channelCostInCents.length ? temp.data.length : channelCostInCents.length;
    
    
    for(int channel = 1; channel < channelCostInCents.length ; ++channel){
      Responce response = executeCommandSync(CommandHeader.REQ_CoinId, new byte[]{(byte)channel},6);
      
      if (response != null && response.isValid){
        try{
          channelCostInCents[channel]=Integer.parseInt(new String(response.data, 2, 3));
          channelCostString[channel]=new String(response.data);
          
          log.info("channel index"+channel+"="+channelCostString[channel]);
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
  protected void deviceTick() {
    Responce response = executeCommandSync(CommandHeader.Read_Buff_Credit, BillEvents.length);
    if(response == null || !response.isValid)  return;
        
    final BillEvents events = new BillEvents(response.data,m_LastEventCounter);  
    
    for(int i=events.start_index;m_LastEventCounter != events.event_counter;--i){
      m_LastEventCounter=events.counter[i];
      
      if(events.events[i][0] != 0){
        final int channelIndex=events.events[i][0];
        loggingEvent("Coin accepted",m_LastEventCounter, channelIndex);
        setMasterInhibitStatusSync(true);
        eventExecutor.submit(() -> {
          controller.onCoinAccepted(CoinAcceptor.this, channelCostInCents[channelIndex]);
        });
      }else{
        final int eventCode=events.events[i][1];
        if(eventCode < 128){
          setMasterInhibitStatusSync(true);
          switch(eventCode){
            case 0: status("Null event ( no error )",m_LastEventCounter,eventCode);                           break;
            case 1: status("Reject coin",m_LastEventCounter,eventCode);                                       break;
            case 2: status("Inhibited coin",m_LastEventCounter,eventCode);                                    break;
            
            case 3: status("Multiple window",m_LastEventCounter,eventCode);                                   break;
            case 4: hardwareFatal("Wake-up timeout",m_LastEventCounter,eventCode);                            break;
            case 5: hardwareFatal("Validation timeout",m_LastEventCounter,eventCode);                         break;
            case 6: hardwareFatal("Credit sensor timeout",m_LastEventCounter,eventCode);                      break;
            case 7: hardwareFatal("Sorter opto timeout",m_LastEventCounter,eventCode);                        break;
            case 8: coinInsertedTooQuikly("2nd close coin error",m_LastEventCounter,eventCode);               break; //quikly
            case 9: coinInsertedTooQuikly("Accept gate not ready",m_LastEventCounter,eventCode);              break; //quikly
            case 10:coinInsertedTooQuikly("Credit sensor not ready",m_LastEventCounter,eventCode);            break; //quikly
            case 11:coinInsertedTooQuikly("Sorter not ready",m_LastEventCounter,eventCode);                   break; //quikly
            case 12:coinInsertedTooQuikly("Reject coin not cleared",m_LastEventCounter,eventCode);            break; //quikly
            case 13:hardwareFatal("Validation sensor not ready",m_LastEventCounter,eventCode);                break;
            case 14:hardwareFatal("Credit sensor blocked",m_LastEventCounter,eventCode);                      break;
            case 15:hardwareFatal("Sorter opto blocked",m_LastEventCounter,eventCode);                        break;
            case 16:fraudAttemt("Credit sequence error",m_LastEventCounter,eventCode);                        break;
            case 17:fraudAttemt("Coin going backwards",m_LastEventCounter,eventCode);                         break;
            case 18:fraudAttemt("Coin too fast (over credit sensor)",m_LastEventCounter,eventCode);           break;
            case 19:fraudAttemt("Coin too slow (over credit sensor)",m_LastEventCounter,eventCode);           break;
            case 20:fraudAttemt("C.O.S. mechanism activated (coin-on-string)",m_LastEventCounter,eventCode);  break;
            case 21:hardwareFatal("DCE opto timeout",m_LastEventCounter,eventCode);                           break;
            case 22:fraudAttemt("DCE opto not seen",m_LastEventCounter,eventCode);                            break;
            case 23:fraudAttemt("Credit sensor reached too early",m_LastEventCounter,eventCode);              break;
            case 24:fraudAttemt("Reject coin (repeated sequential trip)",m_LastEventCounter,eventCode);       break;
            case 25:fraudAttemt("Reject slug",m_LastEventCounter,eventCode);                                  break;
            case 26:hardwareFatal("Reject sensor blocked",m_LastEventCounter,eventCode);                      break;
            case 27:hardwareFatal("Games overload",m_LastEventCounter,eventCode);                             break;
            case 28:hardwareFatal("Max. coin meter pulses exceeded",m_LastEventCounter,eventCode);            break;
            case 29:hardwareFatal("Accept gate open not closed",m_LastEventCounter,eventCode);                break;  
            case 30:hardwareFatal("Accept gate closed not open",m_LastEventCounter,eventCode);                break;  
            case 31:hardwareFatal("Manifold opto timeout",m_LastEventCounter,eventCode);                      break;
            case 32:hardwareFatal("Manifold opto blocked",m_LastEventCounter,eventCode);                      break;
            case 33:coinInsertedTooQuikly("Manifold not ready",m_LastEventCounter,eventCode);                 break;    //quikly
            case 34:fraudAttemt("Security status changed",m_LastEventCounter,eventCode);                      break;
            case 35:hardwareFatal("Motor exception",m_LastEventCounter,eventCode);                            break;
            default:hardwareFatal("coinacc unknown event {}",m_LastEventCounter,eventCode);                   break;
          }
        }else if(128<= eventCode &&  eventCode <= 159){
          status("inhibited coin", m_LastEventCounter, eventCode);
        }else if(eventCode > 159){
          hardwareFatal("coinacc unknown event", m_LastEventCounter,eventCode); 
        }
      }
    }
  }
  
  
  
  private void fraudAttemt(final String message, final int eventCounter, final int code){
    loggingEvent(message, eventCounter, code);
    setMasterInhibitStatusSync(true);
    eventExecutor.submit(() -> {
      controller.onFraudAttemt(CoinAcceptor.this,message, eventCounter, code);
    });
  }
  
  private void hardwareFatal(final String message, final int eventCounter, final int code){
    loggingEvent(message, eventCounter, code);
    setMasterInhibitStatusSync(true);
    eventExecutor.submit(() -> {
      controller.onHardwareFatal(CoinAcceptor.this,message, eventCounter, code);
    });  
  }
  
  public static final int STATUS_OK=0;
  private void status(final String message, final int eventCounter, final int code){
    loggingEvent(message, eventCounter, code);
    eventExecutor.submit(() -> {
      controller.onStatus(CoinAcceptor.this,message, eventCounter, code);
    });      
  }
  private void coinInsertedTooQuikly(final String message, final int eventCounter, final int code){
    loggingEvent(message, eventCounter, code);
    eventExecutor.submit(() -> {
      controller.onCoinInsertedTooQuikly(CoinAcceptor.this,message, eventCounter, code);
    });  
  }
  
}
