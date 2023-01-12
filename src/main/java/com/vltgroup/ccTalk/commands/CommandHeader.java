package com.vltgroup.ccTalk.commands;


public enum CommandHeader {      //normal response
  ACK(0), // Non-existing command
  RESET_DEVICE(1),
  NACK(5), // NACK response
  ADDRESS_POLL(253),            //byte[]
  SIMPLE_POLL(254),             //ACK
  REQ_DEV_CATEGORY_ID(245),     //String
  REQ_ManufacturerId(246),      //String
  REQ_ProductCode(244),         //String
  REQ_SerialNumber(242),        //byte[3]
  REQ_PollingPriority(249),     //byte[2]
  REQ_SoftwareVer(241),         //string


  ModMasterInhibit(228),          //ACK
  ModInhibitStat(231),            //ACK
  ModifyBillOperatingMode(153),   //ACK
  Read_Buffered_BillEvents(159),  //byte[11]
  RouteBill(154),                 //ACK
  REQ_BillId(157),                //byte[7]
  REQ_ScalingFactor(156),

  REQ_CoinId(184),
  REQ_InhibitStat(230),
  Read_Buff_Credit(229);

  public final int command;

  CommandHeader(int command){
    this.command = command;
  }
}
  