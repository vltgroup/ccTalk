package com.vltgroup.ccTalk.bus;

import lombok.Getter;

public enum DeviceType {
  BILL_ACC("Bill Validator"),
  COIN_ACC("Coin Acceptor"),
  HOPPER("Payout")
  ;

  @Getter
  private final String signature; // Device type string according to ccTalk specification

  DeviceType(String signature) {
    this.signature = signature;
  }
}
