package com.vltgroup.ccTalk.devices;


public enum BillAcceptorEventCodes {
  BillStacked(0,"Bill validated correctly and sent to stacker"),
  BillEscrow(1,"Bill validated correctly and held in escrow"),
  
  MasterInhibit(0, "Master inhibit active "),                       //Status 
  
  BillReturned(1,"Bill returned from escrow"),                      //Status - in fact Reject
  BillReject_ByValidation(2,"Bill reject due to validation fail"),  //Reject
  BillReject_Transport(3,"Bill reject due to transport problem"),   //Reject
  BillReject_Inhibited1(4,"Inhibited bill (on serial)"),            //Status - in fact Reject
  BillReject_Inhibited2(5,"Inhibited bill (on DIP switches)"),      //Status - in fact Reject

  StackerOK(10,"Stacker OK"),                                       //Status 
  StackerRremoved(11,"Stacker removed"),                            //Status 
  StackerInserted(12,"Stacker inserted"),                           //Status 
  StackerFull(14,"Stacker full"),                                   //Status 
  
  BillJammedInsafe(6,"Bill jammed in transport (unsafe mode)"), //Fatal Error 
  BillJammedInStacker(7,"Bill jammed in stacker"),              //Fatal Error 
  StackerFaulty(13,"Stacker faulty"),                           //Fatal Error 
  StackerJammed(15,"Stacker jammed"),                           //Fatal Error 
  BillJammedSafe(16,"Bill jammed in transport (safe mode)"),    //Fatal Error 
  

  
  BillPulledBackwards(8,"Bill pulled backwards"),               //Fraud Attempt 
  BillTamper(9,"Bill tamper"),                                  //Fraud Attempt 
  OptoFraud(17,"Opto fraud detected"),                          //Fraud Attempt 
  StringFraud(18,"String fraud detected");                      //Fraud Attempt 

  
  
  public final int code;
  public final String describe;

  BillAcceptorEventCodes(int code, String describe){
    this.code=code;
    this.describe=describe;
  }
}
