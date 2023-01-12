package com.vltgroup.ccTalk.examplePC;

import com.vltgroup.ccTalk.comport.ReceiveCallback;
import java.io.Closeable;
import java.io.IOException;
import jssc.SerialPort;
import jssc.SerialPortException;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class ComPort implements com.vltgroup.ccTalk.comport.ComPort, Closeable{
  private final SerialPort serialPort;
  
  public ComPort(String portName) throws SerialPortException{
    serialPort = new SerialPort(portName);

    serialPort.openPort();
    
    serialPort.setParams(SerialPort.BAUDRATE_9600,
                         SerialPort.DATABITS_8,
                         SerialPort.STOPBITS_1,
                         SerialPort.PARITY_NONE);
    
    
    serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);
  }
  
  @Override
  public void sendBytes(byte[] bytes) {
    try {
      //log.debug(bytesToHex(bytes));
      serialPort.writeBytes(bytes);
    } catch (SerialPortException ex) {
      log.error("",ex);
    }
    
  }

  @Override
  public void setReceiveCallback(final ReceiveCallback callback) {
    try {
      serialPort.addEventListener(event -> {
        try {
          if (event.isRXCHAR()){
            byte[] data=serialPort.readBytes();
            if(data != null){
              //log.debug(bytesToHex(data));
              callback.onReceivedData(data);
            }
          }
        } catch(SerialPortException ex) {
          log.error("error at read data from com port",ex);
        }
      }, SerialPort.MASK_RXCHAR );
    } catch (SerialPortException ex) {
      log.error("can't set com port listener",ex);
    }
  }

  @Override
  public void close() throws IOException{
    try {
      serialPort.closePort();
    } catch (SerialPortException ex) {
      throw new IOException(ex);
    }
  }
}
