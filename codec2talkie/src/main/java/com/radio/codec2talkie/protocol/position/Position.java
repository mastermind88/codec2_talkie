package com.radio.codec2talkie.protocol.position;

public class Position {
    public String timestamp;
    public String srcCallsign;
    public String dstCallsign;
    public double latitude;
    public double longitude;
    public double altitudeMeters;
    public float bearingDegrees;
    public float speedMetersPerSecond;
    public String status;
    public String comment;
    public String symbolCode;
    public boolean isCompressed;
    public int privacyLevel;
    public int extDigipathSsid;
    public boolean isSpeedBearingEnabled;
    public boolean isAltitudeEnabled;
}
