package com.vltgroup.ccTalk.commands;


public class Response {
  public final boolean isValid;
  private final int responseHeader;

  public final byte[] data;
  
  public Response(int command, byte[] data, boolean isValid) {
    this.responseHeader = command;
    this.data = data;
    this.isValid = isValid;
  }

  public int getResponseHeader() {
    return responseHeader;
  }
  public boolean isACK(){
    return responseHeader == CommandHeader.ACK.command;
  }
  public boolean isNack() { return responseHeader == CommandHeader.NACK.command; }
}
