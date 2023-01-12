package com.vltgroup.ccTalk.devices;


public class BillAcceptorEventCodes {
  public static final int MasterInhibit =0;           //Status
  public static final int BillReturned=1;             //Status - in fact Reject
  public static final int BillReject_ByValidation=2;  //Reject
  public static final int BillReject_Transport=3;     //Reject
  public static final int BillReject_Inhibited1=4;    //Status - in fact Reject
  public static final int BillReject_Inhibited2=5;    //Status - in fact Reject

  public static final int StackerOK=10;               //Status 
  public static final int StackerRemoved =11;         //Status
  public static final int StackerInserted=12;         //Status 
  public static final int StackerFull=14;             //Status 
  
  public static final int BillJammedInsafe=6;         //hardwareFatal
  public static final int BillJammedInStacker=7;      //hardwareFatal
  public static final int StackerFaulty=13;           //hardwareFatal
  public static final int StackerJammed=15;           //hardwareFatal
  public static final int BillJammedSafe=16;          //hardwareFatal
  public static final int AntiStringFaulty=19;        //hardwareFatal
  
  public static final int BillPulledBackwards=8;      //Fraud Attempt 
  public static final int BillTamper=9;               //Fraud Attempt 
  public static final int OptoFraud=17;               //Fraud Attempt 
  public static final int StringFraud=18;             //Fraud Attempt 
}
