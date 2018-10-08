package com.vltgroup.ccTalk.devices;

import com.vltgroup.ccTalk.bus.Bus;
import com.vltgroup.ccTalk.bus.DeviceInfo;
import com.vltgroup.ccTalk.bus.DeviceType;
import com.vltgroup.ccTalk.commands.CommandHeader;
import com.vltgroup.ccTalk.commands.Responce;
import static com.vltgroup.ccTalk.devices.CoinAcceptorEventCodes.*;
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
    
    Responce response = executeCommandSync(CommandHeader.Read_Buff_Credit, BillEvents.length,false);
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
      if(getNotRespondStatus()) return; 
    }

    m_LastEventCounter=0;
    
    QueryChannelInfo();
    if(getNotRespondStatus()) return; 
    
    executeCommandSync(CommandHeader.ModInhibitStat,new byte[]{(byte)0xFF,(byte)0xFF},0, false);//TODO: inhibit channel with zero cost
    if(getNotRespondStatus()) return; 
    executeCommandSync(CommandHeader.ModMasterInhibit, new byte[]{m_lastInhibit ? 0 : (byte)1},0, false);
    if(getNotRespondStatus()) return; 

    status("dummy after init", 0, NullEvent);
  } 
  
  private void QueryChannelInfo() {
    //Responce temp = executeCommandSync(CommandHeader.REQ_InhibitStat, -1); //just to know channel amount
    //int channelAmount = temp.data.length < channelCostInCents.length ? temp.data.length : channelCostInCents.length;
    
    
    for(int channel = 1; channel < channelCostInCents.length ; ++channel){
      Responce coinId = executeCommandSync(CommandHeader.REQ_CoinId, new byte[]{(byte)channel},6, false);
      
      if (coinId != null && coinId.isValid){
        try{
          channelCostInCents[channel]=Integer.parseInt(new String(coinId.data, 2, 3));
          String channelCostString =new String(coinId.data);
          
          byte[] country =java.util.Arrays.copyOfRange(coinId.data, 0, 2);
          log.info(info.shortString()+" channel index{}={}", channel, channelCostString);
          
          //coin acceptors not support REQ_ScalingFactor command, so emulate it result
          channelCost[channel] = new ChannelCost(channelCostInCents[channel],1,2,new String(country),channelCostString);
        }catch(Exception ignored){
          channelCostInCents[channel]=0;
          channelCost[channel]=null;
        }
      }else{
        channelCostInCents[channel]=0;
        channelCost[channel]=null;
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
            case NullEvent: status("Null event ( no error )",m_LastEventCounter,eventCode);                                     break;
            case RejectCoin: status("Reject coin",m_LastEventCounter,eventCode);                                                break;
            case InhibitedCoin: status("Inhibited coin",m_LastEventCounter,eventCode);                                          break;
            case MultipleWindow: status("Multiple window",m_LastEventCounter,eventCode);                                        break;
              
            case WakeupTimeout:       hardwareFatal("Wake-up timeout",m_LastEventCounter,eventCode);                            break;
            case ValidationTimeout:   hardwareFatal("Validation timeout",m_LastEventCounter,eventCode);                         break;
            case CreditSensorTimeout: hardwareFatal("Credit sensor timeout",m_LastEventCounter,eventCode);                      break;
            case SorterOptoTimeout:   hardwareFatal("Sorter opto timeout",m_LastEventCounter,eventCode);                        break;
              
            case nd2CloseCoin:        coinInsertedTooQuikly("2nd close coin error",m_LastEventCounter,eventCode);               break;
            case AcceptGateNotReady:  coinInsertedTooQuikly("Accept gate not ready",m_LastEventCounter,eventCode);              break;
            case CreditSensorNotReady:coinInsertedTooQuikly("Credit sensor not ready",m_LastEventCounter,eventCode);            break;
            case SorterNotReady:      coinInsertedTooQuikly("Sorter not ready",m_LastEventCounter,eventCode);                   break;
            case RejectCoinNotCleared:coinInsertedTooQuikly("Reject coin not cleared",m_LastEventCounter,eventCode);            break;
              
            case ValidationSensorNotReady:hardwareFatal("Validation sensor not ready",m_LastEventCounter,eventCode);                break;
            case CreditSensorBlocked:     hardwareFatal("Credit sensor blocked",m_LastEventCounter,eventCode);                      break;
            case SorterOptoBlocked:       hardwareFatal("Sorter opto blocked",m_LastEventCounter,eventCode);                        break;
            case CreditQequenceError:     fraudAttemt("Credit sequence error",m_LastEventCounter,eventCode);                        break;
            case CoinGoingBackwards:      fraudAttemt("Coin going backwards",m_LastEventCounter,eventCode);                         break;
            case CoinTooFast:             fraudAttemt("Coin too fast (over credit sensor)",m_LastEventCounter,eventCode);           break;
            case CoinTooSlow:             fraudAttemt("Coin too slow (over credit sensor)",m_LastEventCounter,eventCode);           break;
            case COSMechanismActivated:   fraudAttemt("C.O.S. mechanism activated (coin-on-string)",m_LastEventCounter,eventCode);  break;
            case DCEOptoTimeout:          hardwareFatal("DCE opto timeout",m_LastEventCounter,eventCode);                           break;
            case DCEOptoNotSeen:          fraudAttemt("DCE opto not seen",m_LastEventCounter,eventCode);                            break;
            case CreditSensorReachedTooEarly:fraudAttemt("Credit sensor reached too early",m_LastEventCounter,eventCode);           break;
            case RejectCoinRepeatedSequentialTrip:fraudAttemt("Reject coin (repeated sequential trip)",m_LastEventCounter,eventCode);break;
            case RejectSlug:              fraudAttemt("Reject slug",m_LastEventCounter,eventCode);                                  break;
            case RejectSensorBlocked:     hardwareFatal("Reject sensor blocked",m_LastEventCounter,eventCode);                      break;
            case GamesOverload:           hardwareFatal("Games overload",m_LastEventCounter,eventCode);                             break;
            case MaxCoinMeterPulsesExceeded:hardwareFatal("Max. coin meter pulses exceeded",m_LastEventCounter,eventCode);          break;
            case AcceptGateOpenNotClosed: hardwareFatal("Accept gate open not closed",m_LastEventCounter,eventCode);                break;  
            case AcceptGateClosedNotOpen: hardwareFatal("Accept gate closed not open",m_LastEventCounter,eventCode);                break;  
            case ManifoldOptoTimeout:     hardwareFatal("Manifold opto timeout",m_LastEventCounter,eventCode);                      break;
            case ManifoldOptoBlocked:     hardwareFatal("Manifold opto blocked",m_LastEventCounter,eventCode);                      break;
            case ManifoldNotReady:        coinInsertedTooQuikly("Manifold not ready",m_LastEventCounter,eventCode);                 break;    
            case SecurityStatusChanged:   fraudAttemt("Security status changed",m_LastEventCounter,eventCode);                      break;
            case MotorException:          hardwareFatal("Motor exception",m_LastEventCounter,eventCode);                            break;
              
            case SwallowedCoin:           hardwareFatal("Swallowed coin",m_LastEventCounter,eventCode);                             break;
            case CoinTooFastValidation:   fraudAttemt("Coin too fast ( over validation sensor )",m_LastEventCounter,eventCode);     break;
            case CoinTooSlowValidation:   fraudAttemt("Coin too slow ( over validation sensor )",m_LastEventCounter,eventCode);     break;
            case CoinIncorrectlySorted:   hardwareFatal("Coin incorrectly sorted",m_LastEventCounter,eventCode);                    break;
            case ExternalLightAttack:     fraudAttemt("External light attack",m_LastEventCounter,eventCode);                        break;
            default:unknownEvent(m_LastEventCounter,events.events[i][0],eventCode);                                                 break;
          }
        }else if(128<= eventCode &&  eventCode <= 159){
          status("inhibited coin", m_LastEventCounter, eventCode);
        }else if(eventCode > 159){
          unknownEvent(m_LastEventCounter,events.events[i][0],eventCode); 
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
  private void unknownEvent(final int eventCounter, final int code1, final int code2){
    loggingEvent("coinacc unknown event", eventCounter, code1,code2);
    eventExecutor.submit(() -> {
      controller.onUnknownEvent(CoinAcceptor.this, eventCounter, code1,code2);
    });  
  }
  
}
