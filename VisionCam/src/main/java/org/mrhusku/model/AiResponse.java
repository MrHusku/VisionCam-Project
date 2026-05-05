package org.mrhusku.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AiResponse {

    @JsonProperty("detected_item")
    private String detectedItem;

    @JsonProperty("x")
    private int x;

    @JsonProperty("y")
    private int y;

    @JsonProperty("w_px")
    private int wPx;

    @JsonProperty("h_px")
    private int hPx;

    @JsonProperty("real_height_cm")
    private int realHeightCm;



    public String getDetectedItem() {
        return detectedItem;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getwPx() {
        return wPx;
    }

    public int gethPx() {
        return hPx;
    }

    public int getRealHeightCm() {
        return realHeightCm;
    }


    public void setDetectedItem(String detectedItem) {
        this.detectedItem = detectedItem;
    }

    public void setX(int x) {
        this.x = x;
    }

    public void setY(int y) {
        this.y = y;
    }

    public void setwPx(int wPx) {
        this.wPx = wPx;
    }

    public void sethPx(int hPx) {
        this.hPx = hPx;
    }

    public void setRealHeightCm(int realHeightCm) {
        this.realHeightCm = realHeightCm;
    }
}