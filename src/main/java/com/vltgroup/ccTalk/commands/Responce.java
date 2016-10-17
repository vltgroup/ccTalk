package com.vltgroup.ccTalk.commands;


public class Responce {
  public final boolean isValid;
  public final int    responceHeader;
  public final byte[] data;
  
  public Responce(int command, byte[] data, boolean isValid) { 
    this.responceHeader     = command;
    this.data        = data;
    this.isValid = isValid;
  }
  
  public boolean isACK(){
    return responceHeader == CommandHeader.ACK.command;
  }
}
