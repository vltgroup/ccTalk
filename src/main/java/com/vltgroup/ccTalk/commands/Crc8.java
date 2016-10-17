package com.vltgroup.ccTalk.commands;

public class Crc8 {

    private byte crc = 0;

    public void update(byte[] bytes) {
        for (byte b : bytes) {
            update(b);
        }
    }

    public void update(byte b) {
        crc -= b;
    }

    public byte getCrc() {
        return crc;
    }
}
