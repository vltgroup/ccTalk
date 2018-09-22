package com.vltgroup.ccTalk.devices;

public interface BaseController {
  void onDeviceNotRespond(BaseDevice device);
  void onDeviceRestored(BaseDevice device);
  void onUnknownEvent(BaseDevice device, final int eventCounter, final int code1, final int code2);
  void onDeviceDead(BaseDevice device);
}
