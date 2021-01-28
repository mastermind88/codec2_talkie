package com.radio.codec2talkie.protocol;

public class ProtocolFactory {

    public enum ProtocolType {
        RAW,
        KISS,
        KISS_PARROT
    };

    public static Protocol create(ProtocolType protocolType) {
        switch (protocolType) {
            case KISS:
                return new Kiss();
            case KISS_PARROT:
                return new KissParrot();
            case RAW:
            default:
                return null;
        }
    }
}
