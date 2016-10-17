package com.vltgroup.ccTalk.devices;

public interface BaseController {
  void onDeviceNotRespond(BaseDevice device);
  void onDeviceRestored(BaseDevice device);
}
