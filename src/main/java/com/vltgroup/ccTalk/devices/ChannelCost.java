package com.vltgroup.ccTalk.devices;

public class ChannelCost {
  public final long costInCents;  
  
  public final int  scaling;
  public final int  decimal;
  public final String countryCode;
  public final String costString;
  
  public ChannelCost(long costInCents, int  scaling, int  decimal, String countryCode, String costString){
    this.costInCents=costInCents;
    this.scaling=scaling;
    this.decimal=decimal;
    this.countryCode=countryCode;
    this.costString=costString;
  }
  
  @Override
  public String toString() {
    return costString;
  }
}
