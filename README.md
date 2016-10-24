# ccTalk
ccTalk implementation over simple async com port interface

# Builds
[![](https://jitpack.io/v/vltgroup/ccTalk.svg)](https://jitpack.io/#vltgroup/ccTalk)

# Device support
- coin acceptors
- bill acceptors
- encrypted and unencrypted mode

# Usage 
 - to start: instantiate com.vltgroup.ccTalk.bus.Bus over you com.vltgroup.ccTalk.comport.ComPort implementation
 - to implement comport for windows, linux, osX, solaris - recomended to use jssc  https://code.google.com/archive/p/java-simple-serial-connector/ 
 - to implement comport for android - recomended to use UsbSerial https://github.com/felHR85/UsbSerial
