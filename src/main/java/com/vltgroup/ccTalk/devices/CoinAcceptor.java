package com.vltgroup.ccTalk.devices;

import com.vltgroup.ccTalk.bus.Bus;
import com.vltgroup.ccTalk.bus.DeviceInfo;
import com.vltgroup.ccTalk.bus.DeviceType;
import com.vltgroup.ccTalk.commands.CommandHeader;
import com.vltgroup.ccTalk.commands.Response;
import static com.vltgroup.ccTalk.devices.CoinAcceptorEventCodes.*;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CoinAcceptor extends BaseDevice{

  private int lastEventCounter;
  private final CoinAcceptorController controller;

  private boolean supportMasterInhibit = true; // Some coin-acceptors doesn't support SetMasterInhibit command,
                                               // therefore modify inhibit status (231) should be used
  
  public CoinAcceptor(Bus bus, DeviceInfo info, CoinAcceptorController controller){
    super(bus, info, controller);

    if (info.type != DeviceType.COIN_ACC) {
      throw new RuntimeException("invalid type");
    }

    this.controller = controller;
    
    Response response = executeCommandSync(CommandHeader.Read_Buff_Credit, BillEvents.length,false);
    if (response != null && response.isValid) {
      BillEvents events = new BillEvents(response.data,0);  
      init(events.event_counter != 0);
    } else {
      init(true);   //but it very strange
    }
  }
  

  @Override
  protected final void init(boolean makeReset) {
    if (makeReset) {
      reset(5, CommandHeader.Read_Buff_Credit, BillEvents.length);
      if(getNotRespondStatus()) return; 
    }

    lastEventCounter =0;
    
    QueryChannelInfo();
    if(getNotRespondStatus()) return;

    if (executeCommandSync(CommandHeader.ModMasterInhibit, new byte[]{lastInhibit ? 0 : (byte)1},0, false).isNack()) {
      log.warn("Coin acceptor doesn't support ModMasterInhibit(228) command, ModInhibitStat(231) will be used.");
      supportMasterInhibit = false;
    }

    // Allow all channels
    executeCommandSync(CommandHeader.ModInhibitStat,new byte[]{(byte)0xFF,(byte)0xFF},0, false);
    if(getNotRespondStatus()) return; 

    status("dummy after init", 0, NullEvent);
  } 
  
  private void QueryChannelInfo() {
    //Responce temp = executeCommandSync(CommandHeader.REQ_InhibitStat, -1); //just to know channel amount
    //int channelAmount = temp.data.length < channelCostInCents.length ? temp.data.length : channelCostInCents.length;
    
    
    for(int channel = 1; channel < channelCostInCents.length ; ++channel){
      Response coinId = executeCommandSync(CommandHeader.REQ_CoinId, new byte[]{(byte)channel},6, false);
      
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
    Response response = executeCommandSync(CommandHeader.Read_Buff_Credit, BillEvents.length);
    if (response == null || !response.isValid) return;
        
    final BillEvents events = new BillEvents(response.data, lastEventCounter);
    
    for(int i = events.start_index; lastEventCounter != events.event_counter; --i) {
      lastEventCounter =events.counter[i];
      
      if (events.events[i][0] != 0){
        final int channelIndex=events.events[i][0];
        loggingEvent("Coin accepted", lastEventCounter, channelIndex);
        setMasterInhibitStatusSync(true);
        eventExecutor.submit(() -> controller.onCoinAccepted(CoinAcceptor.this, channelCostInCents[channelIndex]));
      } else {
        final int eventCode = events.events[i][1];
        if(eventCode < 128){
          setMasterInhibitStatusSync(true);
          switch(eventCode){
            case NullEvent: status("Null event ( no error )", lastEventCounter,eventCode);                                     break;
            case RejectCoin: status("Reject coin", lastEventCounter,eventCode);                                                break;
            case InhibitedCoin: status("Inhibited coin", lastEventCounter,eventCode);                                          break;
            case MultipleWindow: status("Multiple window", lastEventCounter,eventCode);                                        break;
              
            case WakeupTimeout:       hardwareFatal("Wake-up timeout", lastEventCounter,eventCode);                            break;
            case ValidationTimeout:   hardwareFatal("Validation timeout", lastEventCounter,eventCode);                         break;
            case CreditSensorTimeout: hardwareFatal("Credit sensor timeout", lastEventCounter,eventCode);                      break;
            case SorterOptoTimeout:   hardwareFatal("Sorter opto timeout", lastEventCounter,eventCode);                        break;
              
            case nd2CloseCoin:        coinInsertedTooQuickly("2nd close coin error", lastEventCounter,eventCode);               break;
            case AcceptGateNotReady:  coinInsertedTooQuickly("Accept gate not ready", lastEventCounter,eventCode);              break;
            case CreditSensorNotReady:coinInsertedTooQuickly("Credit sensor not ready", lastEventCounter,eventCode);            break;
            case SorterNotReady:      coinInsertedTooQuickly("Sorter not ready", lastEventCounter,eventCode);                   break;
            case RejectCoinNotCleared:coinInsertedTooQuickly("Reject coin not cleared", lastEventCounter,eventCode);            break;
              
            case ValidationSensorNotReady:hardwareFatal("Validation sensor not ready", lastEventCounter,eventCode);                break;
            case CreditSensorBlocked:     hardwareFatal("Credit sensor blocked", lastEventCounter,eventCode);                      break;
            case SorterOptoBlocked:       hardwareFatal("Sorter opto blocked", lastEventCounter,eventCode);                        break;
            case CreditQequenceError:     fraudAttempt("Credit sequence error", lastEventCounter,eventCode);                        break;
            case CoinGoingBackwards:      fraudAttempt("Coin going backwards", lastEventCounter,eventCode);                         break;
            case CoinTooFast:             fraudAttempt("Coin too fast (over credit sensor)", lastEventCounter,eventCode);           break;
            case CoinTooSlow:             fraudAttempt("Coin too slow (over credit sensor)", lastEventCounter,eventCode);           break;
            case COSMechanismActivated:   fraudAttempt("C.O.S. mechanism activated (coin-on-string)", lastEventCounter,eventCode);  break;
            case DCEOptoTimeout:          hardwareFatal("DCE opto timeout", lastEventCounter,eventCode);                           break;
            case DCEOptoNotSeen:          fraudAttempt("DCE opto not seen", lastEventCounter,eventCode);                            break;
            case CreditSensorReachedTooEarly:
              fraudAttempt("Credit sensor reached too early", lastEventCounter,eventCode);           break;
            case RejectCoinRepeatedSequentialTrip:
              fraudAttempt("Reject coin (repeated sequential trip)", lastEventCounter,eventCode);break;
            case RejectSlug:              fraudAttempt("Reject slug", lastEventCounter,eventCode);                                  break;
            case RejectSensorBlocked:     hardwareFatal("Reject sensor blocked", lastEventCounter,eventCode);                      break;
            case GamesOverload:           hardwareFatal("Games overload", lastEventCounter,eventCode);                             break;
            case MaxCoinMeterPulsesExceeded:hardwareFatal("Max. coin meter pulses exceeded", lastEventCounter,eventCode);          break;
            case AcceptGateOpenNotClosed: hardwareFatal("Accept gate open not closed", lastEventCounter,eventCode);                break;
            case AcceptGateClosedNotOpen: hardwareFatal("Accept gate closed not open", lastEventCounter,eventCode);                break;
            case ManifoldOptoTimeout:     hardwareFatal("Manifold opto timeout", lastEventCounter,eventCode);                      break;
            case ManifoldOptoBlocked:     hardwareFatal("Manifold opto blocked", lastEventCounter,eventCode);                      break;
            case ManifoldNotReady:        coinInsertedTooQuickly("Manifold not ready", lastEventCounter,eventCode);                 break;
            case SecurityStatusChanged:   fraudAttempt("Security status changed", lastEventCounter,eventCode);                      break;
            case MotorException:          hardwareFatal("Motor exception", lastEventCounter,eventCode);                            break;
              
            case SwallowedCoin:           hardwareFatal("Swallowed coin", lastEventCounter,eventCode);                             break;
            case CoinTooFastValidation:   fraudAttempt("Coin too fast ( over validation sensor )", lastEventCounter,eventCode);     break;
            case CoinTooSlowValidation:   fraudAttempt("Coin too slow ( over validation sensor )", lastEventCounter,eventCode);     break;
            case CoinIncorrectlySorted:   hardwareFatal("Coin incorrectly sorted", lastEventCounter,eventCode);                    break;
            case ExternalLightAttack:     fraudAttempt("External light attack", lastEventCounter,eventCode);                        break;
            default:unknownEvent(lastEventCounter,events.events[i][0],eventCode);                                                 break;
          }
        }else if(128 <= eventCode && eventCode <= 159){
          status("inhibited coin", lastEventCounter, eventCode);
        }else if(eventCode > 159){
          unknownEvent(lastEventCounter,events.events[i][0],eventCode);
        }
      }
    }
  }

  private void fraudAttempt(final String message, final int eventCounter, final int code){
    loggingEvent(message, eventCounter, code);
    setMasterInhibitStatusSync(true);
    eventExecutor.submit(() -> controller.onFraudAttempt(CoinAcceptor.this,message, eventCounter, code));
  }
  
  private void hardwareFatal(final String message, final int eventCounter, final int code){
    loggingEvent(message, eventCounter, code);
    setMasterInhibitStatusSync(true);
    eventExecutor.submit(() -> controller.onHardwareFatal(CoinAcceptor.this,message, eventCounter, code));
  }
  
  private void status(final String message, final int eventCounter, final int code){
    loggingEvent(message, eventCounter, code);
    eventExecutor.submit(() -> controller.onStatus(CoinAcceptor.this,message, eventCounter, code));
  }
  private void coinInsertedTooQuickly(final String message, final int eventCounter, final int code){
    loggingEvent(message, eventCounter, code);
    eventExecutor.submit(() -> controller.onCoinInsertedTooQuickly(CoinAcceptor.this,message, eventCounter, code));
  }
  private void unknownEvent(final int eventCounter, final int code1, final int code2){
    loggingEvent("coin acceptor unknown event", eventCounter, code1,code2);
    eventExecutor.submit(() -> controller.onUnknownEvent(CoinAcceptor.this, eventCounter, code1,code2));
  }

  @Override
  public void setMasterInhibitStatusSync(boolean inhibit) {
    if (supportMasterInhibit) {
      super.setMasterInhibitStatusSync(inhibit);
    } else {
      byte status = inhibit ? (byte)0 : (byte)0xff;
      executeCommandSync(CommandHeader.ModInhibitStat, new byte[]{status, status}, 0, false);
    }
  }

}
