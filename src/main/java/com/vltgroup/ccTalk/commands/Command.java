package com.vltgroup.ccTalk.commands;

import com.vltgroup.ccTalk.bus.Address;
import com.vltgroup.ccTalk.bus.DeviceMode;
import static com.vltgroup.ccTalk.commands.BNVEncode.BNV_decrypt;
import static com.vltgroup.ccTalk.commands.BNVEncode.BNV_encrypt;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;


public class Command {
  public static final Command AddessPoll = new Command(Address.ccBroadcastDevId, CommandHeader.ADDRESS_POLL);
  
  public final Address destination;
  public final Address source;
  public final CommandHeader command;
  private final int _command;
  public final byte[] data;

  public Command(Address destination, CommandHeader command) {                    //for send
    this.destination = destination;
    this.source      = Address.ccHostDevId;
    this.command     = command;
    _command = command.command;
    data = new byte[0];
  }

  public Command(Address destination, CommandHeader command, byte[] data) {     //for send
    this.destination = destination;
    this.source      = Address.ccHostDevId;
    this.command     = command;
    _command = command.command;
    this.data        = data;
  }
  
  
  private Command(Address destination, Address source, int command, byte[] data) { //for recieve
    this.destination = destination;
    this.source      = source;
    this.command     = null;
    _command         = command;
    this.data        = data;
  }

  public Crc8 calcCrc8(){
    Crc8 crc8 = new Crc8();
    crc8.update((byte)destination.address);
    crc8.update((byte)data.length);
    crc8.update((byte)source.address);
    crc8.update((byte)_command);
    crc8.update(data);
    
    return crc8;
  }
  
  public Crc16 calcCrc16(){
    Crc16 crc16 = new Crc16();
    crc16.update((byte)destination.address);
    crc16.update((byte)data.length);
    crc16.update((byte)_command);
    crc16.update(data);
    
    return crc16;
  }
  
  public byte[] getBytesCRC8(){
    Crc8 crc8 = calcCrc8();

    ByteArrayOutputStream raw = new ByteArrayOutputStream();
    raw.write( (byte)destination.address );
    raw.write( (byte)data.length );
    raw.write( (byte)source.address );
    raw.write( (byte)_command);
    try {
      raw.write( data );
    } catch (IOException ignored) { }
    raw.write( crc8.getCrc() );

    return raw.toByteArray();
  }
  private Responce constructResponce(int expectedDataLength){
    boolean isValid=true;
    if(_command != CommandHeader.ACK.command) isValid=false;
    if(expectedDataLength >= 0 && data.length != expectedDataLength) isValid=false;
    return new Responce(_command, data, isValid);
  }
  
  
  public static Responce decodeCommand(Address sender, byte[] raw, DeviceMode mode, byte[] BNVCode, int expectedDataLength){
    if(raw.length < 5) return null;
    
    int destination   = 0xFF & raw[0];
    int dataLength    = 0xFF & raw[1];

    if(raw.length - 5 != dataLength) return null;
    if(destination != Address.ccHostDevId.address) return null; // This is not our packet, i.e. destination address is not 1 (host) - flush it!
    
    switch(mode){
      case CRC8:
        { int source        = 0xFF & raw[2];
          int commandHeader = 0xFF & raw[3];
          byte crc          = raw[raw.length-1];
          if(source != sender.address) return null; 
          byte[] data       = Arrays.copyOfRange(raw, 4, raw.length-1);

          Command result  = new Command(Address.ccHostDevId, new Address(source), commandHeader, data);
          if(result.calcCrc8().getCrc() != crc) return null;
          return result.constructResponce(expectedDataLength);
        }
     case ENCRYPTED:  
        byte[] toDecrypt = java.util.Arrays.copyOfRange(raw, 2, raw.length);
        BNV_decrypt(BNVCode, toDecrypt);
        System.arraycopy(toDecrypt, 0, raw, 2, toDecrypt.length);  
        //not put break; here !!!
      case CRC16:
        { int LSB           = 0xFF & raw[2];
          int commandHeader = 0xFF & raw[3]; 
          int MSB           = 0xFF & raw[raw.length-1];
          int crc16 = (MSB << 8) | LSB;
          byte[] data       = Arrays.copyOfRange(raw, 4, raw.length-1);
          
          Command result  = new Command(Address.ccHostDevId, sender, commandHeader, data);
          if(result.calcCrc16().getCRC() != crc16) return null;
          return result.constructResponce(expectedDataLength);
        }
    }
    return null;  //can't be here
  }
  

  public byte[] getBytesCRC16() {
    Crc16 crc16 = calcCrc16();

    ByteArrayOutputStream raw = new ByteArrayOutputStream();
    raw.write((byte)destination.address);
    raw.write((byte)data.length);
    raw.write((byte)(crc16.getCRC() & 0xff));// LSB
    raw.write((byte)_command);
    try {
      raw.write( data );
    } catch (IOException ignored) { }
    raw.write((byte)((crc16.getCRC() >> 8) & 0xff)); // MSB

    return raw.toByteArray();
  }
  
  public byte[] getBytesEncrypted(byte[] BNVCode){
    byte[] raw = getBytesCRC16(); 
    byte[] toEncrypt = java.util.Arrays.copyOfRange(raw, 2, raw.length);
    BNV_encrypt(BNVCode, toEncrypt);
    System.arraycopy(toEncrypt, 0, raw, 2, toEncrypt.length);
    
    return raw;
  }
  
  final private static char[] hexArray = "0123456789ABCDEF".toCharArray();
  public static String bytesToHex(byte[] bytes) {
    StringBuilder sb =new StringBuilder(bytes.length * 3);
    for(int j = 0;j<bytes.length; ++j){
        int v = bytes[j] & 0xFF;
        sb.append(hexArray[v >>> 4]);
        sb.append(hexArray[v & 0x0F]);
        sb.append(' ');
    }
    return sb.toString();
  }
}
