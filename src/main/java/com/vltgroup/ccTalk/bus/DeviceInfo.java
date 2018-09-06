package com.vltgroup.ccTalk.bus;

public class DeviceInfo {
  public final Address    address;
  public final DeviceMode mode;
  public final DeviceType type;
  
  public final String     manufacturerId;
  public final String     productCode;
  public final String     softwareVersion;
  public final long       serialNumber;
  public final long       pollingInterval;

  public DeviceInfo(Address address, DeviceMode mode, DeviceType type, String manufacturerId, String productCode, String  softwareVer,
                    long  serialNumber, long pollingInterval){
    this.address=address;
    this.mode=mode;
    this.type=type;
    
    this.manufacturerId=manufacturerId;
    this.productCode=productCode;
    this.softwareVersion=softwareVer;
    this.serialNumber=serialNumber;
    this.pollingInterval=pollingInterval;
  }

  @Override
  public String toString() {
    return new StringBuilder().append("address:").append(address.address)
                              .append(", mode:").append(mode)
                              .append(", type:").append(type)
            
                              .append(", manufacturerId:").append(manufacturerId)
                              .append(", productCode:").append(productCode)
                              .append(", softwareVersion:").append(softwareVersion)
                              .append(", serialNumber:").append(serialNumber)
                              .append(", pollingInterval:").append(pollingInterval)
                              .toString();
    
  }
  
  public String shortString(){
    return type.toString()+":"+address.address;
  }

  @Override
  public int hashCode() {
    return address.address;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final DeviceInfo other = (DeviceInfo) obj;
    if (this.address.address != other.address.address) {
      return false;
    }
    return true;
  }
}
