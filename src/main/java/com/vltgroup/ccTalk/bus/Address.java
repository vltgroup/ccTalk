package com.vltgroup.ccTalk.bus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class Address {
  public static final Address ccBroadcastDevId = new Address(0);  // ccTalk broadcast device ID 
  public static final Address ccHostDevId = new Address(1);       // ccTalk host device ID

  @Getter
  private final int address;

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
    return this.address == ((Address)obj).address;
  }
  
}
