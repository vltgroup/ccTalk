package com.vltgroup.ccTalk.bus;


public class Address {
  public static final Address ccBroadcastDevId = new Address(0);  // ccTalk broadcast device ID 
  public static final Address ccHostDevId = new Address(1);       // ccTalk host device ID      
  
  public final int address;
  
  public Address(int address){
    this.address=address;
  }

  @Override
  public int hashCode() {
    return address;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final Address other = (Address) obj;
    if (this.address != other.address) {
      return false;
    }
    return true;
  }
  
}
