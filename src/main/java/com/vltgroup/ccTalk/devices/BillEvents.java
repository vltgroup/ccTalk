package com.vltgroup.ccTalk.devices;

class BillEvents {
  final int event_counter;
  final int[][] events = new int[5][2];
  static final int length = 11; //length inside ccTalk
  
  final int[] counter = new int[5];
  final int start_index;

  public BillEvents(byte[] data, final int currentCounter) {
    event_counter = 0xFF & data[0];
    events[0][0] = 0xFF & data[1];
    events[0][1] = 0xFF & data[2];
    counter[0] = event_counter;
    if(currentCounter == counter[0]){ start_index=-1; return;} 
    
    
    events[1][0] = 0xFF & data[3];
    events[1][1] = 0xFF & data[4];
    counter[1] = DecEventCounter(counter[0], currentCounter);
    if(currentCounter == counter[1]){ start_index=0; return;}
    
    events[2][0] = 0xFF & data[5];
    events[2][1] = 0xFF & data[6];
    counter[2] = DecEventCounter(counter[1], currentCounter);
    if(currentCounter == counter[2]){ start_index=1; return;}
    
    events[3][0] = 0xFF & data[7];
    events[3][1] = 0xFF & data[8];
    counter[3] = DecEventCounter(counter[2], currentCounter);
    if(currentCounter == counter[3]){ start_index=2; return;}
    
    events[4][0] = 0xFF & data[9];
    events[4][1] = 0xFF & data[10];
    counter[4] = DecEventCounter(counter[3], currentCounter);
    if(currentCounter == counter[4]){ start_index=3; return;}
    
    start_index=4;
  }
  
  private int DecEventCounter(int counter, final int currentCounter){
    if(currentCounter == 0 && counter == 0) return 0;
    if(currentCounter == 0)  return counter-1;
    
    --counter;
    if(counter == 0) counter = 255;
    return counter;
  }
  
}
