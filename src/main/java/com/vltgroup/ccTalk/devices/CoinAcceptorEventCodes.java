package com.vltgroup.ccTalk.devices;

public class CoinAcceptorEventCodes {
  public static final int NullEvent=0;                  //status
  public static final int RejectCoin=1;                 //status
  public static final int InhibitedCoin=2;              //status
  public static final int MultipleWindow=3;             //status
  
  public static final int WakeupTimeout=4;              //hardwareFatal
  public static final int ValidationTimeout=5;          //hardwareFatal
  public static final int CreditSensorTimeout=6;        //hardwareFatal
  public static final int SorterOptoTimeout=7;          //hardwareFatal                        
  
  public static final int nd2CloseCoin=8;               //coinInsertedTooQuickly
  public static final int AcceptGateNotReady=9;         //coinInsertedTooQuickly
  public static final int CreditSensorNotReady=10;      //coinInsertedTooQuickly
  public static final int SorterNotReady=11;            //coinInsertedTooQuickly
  public static final int RejectCoinNotCleared=12;      //coinInsertedTooQuickly
  
  public static final int ValidationSensorNotReady=13;  //hardwareFatal
  public static final int CreditSensorBlocked=14;       //hardwareFatal
  public static final int SorterOptoBlocked=15;         //hardwareFatal
  public static final int CreditQequenceError=16;       //fraudAttempt
  public static final int CoinGoingBackwards=17;        //fraudAttempt
  public static final int CoinTooFast=18;               //fraudAttempt
  public static final int CoinTooSlow=19;               //fraudAttempt
  public static final int COSMechanismActivated=20;     //fraudAttempt
  public static final int DCEOptoTimeout=21;            //hardwareFatal
  public static final int DCEOptoNotSeen=22;            //fraudAttempt
  public static final int CreditSensorReachedTooEarly=23;     //fraudAttempt
  public static final int RejectCoinRepeatedSequentialTrip=24;//fraudAttempt
  public static final int RejectSlug=25;                //fraudAttempt
  public static final int RejectSensorBlocked=26;       //hardwareFatal
  public static final int GamesOverload=27;             //hardwareFatal
  public static final int MaxCoinMeterPulsesExceeded=28;//hardwareFatal
  public static final int AcceptGateOpenNotClosed=29;   //hardwareFatal
  public static final int AcceptGateClosedNotOpen=30;   //hardwareFatal
  public static final int ManifoldOptoTimeout=31;       //hardwareFatal
  public static final int ManifoldOptoBlocked=32;       //hardwareFatal
  public static final int ManifoldNotReady=33;          //coinInsertedTooQuickly
  public static final int SecurityStatusChanged=34;     //fraudAttempt
  public static final int MotorException=35;            //hardwareFatal
  
  public static final int SwallowedCoin=36;             //hardwareFatal
  public static final int CoinTooFastValidation=37;     //fraudAttempt
  public static final int CoinTooSlowValidation=38;     //fraudAttempt
  public static final int CoinIncorrectlySorted=39;     //hardwareFatal
  public static final int ExternalLightAttack=40;       //fraudAttempt
}
