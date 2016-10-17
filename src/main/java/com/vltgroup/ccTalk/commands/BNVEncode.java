package com.vltgroup.ccTalk.commands;

public class BNVEncode {
  public static final int BNVCodeLength=6;
  
  public static boolean IsValidBNVCode(byte[] BNVCode) {
    if(BNVCode == null || BNVCode.length != BNVCodeLength) return false;
    
    for(byte b: BNVCode){
      if (b != 0) return true;
    }
    return false;
  }
  
  private static final int rotatePlaces=12;
  private static final int feedMaster=99;
  private static final byte[] tapArray = {7,4,5,3,1,2,3,2,6,1};
  
  public static void BNV_encrypt(byte[] secCode, byte[] data){
    { int initXOR= ~(secCode[0]<<4 | secCode[4]);
      for(int i=0;i < data.length;++i) data[i] ^=initXOR;
    }
  
    for(int i=0;i < data.length;++i) if( (secCode[3] & (1<< (i & 0x03))) != 0) {
      byte t=data[i];
      data[i]=  (byte) (((t & 0x01) <<7) | ((t & 0x02) <<5) | ((t & 0x04) <<3) | ((t & 0x08) <<1) |
                        ((t & 0x10) >>1) | ((t & 0x20) >>3) | ((t & 0x40) >>5) | ((t & 0x80) >>7)); 
    }

    for(int i=0; i < rotatePlaces; ++i){
      byte c1= (data[data.length-1] & 0x01) !=0 ? (byte)128 : 0;

      for(int j=0; j < data.length;++j){
        if( (data[j] & (1 << tapArray[ (secCode[1]+j) % 10 ])) != 0 )    c1 ^=(byte)128;
      }

      for(int j=0; j < data.length;++j){
        byte c = (data[j] & 0x01) != 0 ? (byte)128 : 0;
        if( ((secCode[5] ^ feedMaster) & ( 1 << ( (i+j) %8  ))) != 0)     c^=(byte)128;
      
        data[j] = (byte)(((data[j]&0xFF) >>> 1) +  c1);
        c1=c;
      }
    }

    { int finalXOR= (secCode[2]<<4 | secCode[2]);
      for(int i=0;i < data.length;++i) data[i] ^=finalXOR;
    }
  }
  
  public static void BNV_decrypt(byte[] secCode, byte[] data){
    { int initXOR= (secCode[2]<<4 | secCode[2]);
      for(int i=0;i < data.length;++i) data[i] ^=initXOR;
    }

    for(int i=rotatePlaces-1; i >= 0 ; --i){
      byte c1= (data[0] & 0x80) !=0 ? (byte)1 : 0;

      for(int j=0; j < data.length;++j){
        if((data[j] & (1 << (tapArray[ (secCode[1]+j) % 10 ]-1)  )) != 0)   c1 ^=1;
      }

      for(int j=data.length-1; j >=0;--j){
        byte c = (data[j] & 0x80) !=0 ? (byte)1 : 0;
      
        if( ((secCode[5] ^ feedMaster) & ( 1 << ( (i+j-1) %8  ))) != 0)  c^=1;
      
        data[j] =(byte) ((data[j] << 1) + c1);
        c1=c;
      }
    }


    for(int i=0;i < data.length;++i)   if((secCode[3] & (1<< (i & 0x03)  )) != 0) {
      byte t=data[i];
      data[i]=  (byte)( ((t & 0x01) <<7) | ((t & 0x02) <<5) | ((t & 0x04) <<3) | ((t & 0x08) <<1) |
                        ((t & 0x10) >>1) | ((t & 0x20) >>3) | ((t & 0x40) >>5) | ((t & 0x80) >>7)); 
    }

    { int finalXOR= ~(secCode[0]<<4 | secCode[4]);
      for(int i=0;i < data.length;++i) data[i] ^=finalXOR;
    }
  } 
}
