package com.allaymc.exile.data;

public class ExileData {

    private boolean exiled;
    private long exileEndTime;
    private int exileCount;
    private String reason = "No reason provided";

    private String normalInventory = "";
    private String normalArmor = "";
    private String normalOffhand = "";

    private String exileInventory = "";
    private String exileArmor = "";
    private String exileOffhand = "";

    private String normalLocation = "";
    private String exileLocation = "";

    private double exileBorderSize = 64.0;

    public boolean isExiled() {
        return exiled;
    }

    public void setExiled(boolean exiled) {
        this.exiled = exiled;
    }

    public long getExileEndTime() {
        return exileEndTime;
    }

    public void setExileEndTime(long exileEndTime) {
        this.exileEndTime = exileEndTime;
    }

    public int getExileCount() {
        return exileCount;
    }

    public void setExileCount(int exileCount) {
        this.exileCount = exileCount;
    }

    public String getReason() {
        return reason == null ? "No reason provided" : reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getNormalInventory() {
        return normalInventory;
    }

    public void setNormalInventory(String normalInventory) {
        this.normalInventory = normalInventory;
    }

    public String getNormalArmor() {
        return normalArmor;
    }

    public void setNormalArmor(String normalArmor) {
        this.normalArmor = normalArmor;
    }

    public String getNormalOffhand() {
        return normalOffhand;
    }

    public void setNormalOffhand(String normalOffhand) {
        this.normalOffhand = normalOffhand;
    }

    public String getExileInventory() {
        return exileInventory;
    }

    public void setExileInventory(String exileInventory) {
        this.exileInventory = exileInventory;
    }

    public String getExileArmor() {
        return exileArmor;
    }

    public void setExileArmor(String exileArmor) {
        this.exileArmor = exileArmor;
    }

    public String getExileOffhand() {
        return exileOffhand;
    }

    public void setExileOffhand(String exileOffhand) {
        this.exileOffhand = exileOffhand;
    }

    public String getNormalLocation() {
        return normalLocation;
    }

    public void setNormalLocation(String normalLocation) {
        this.normalLocation = normalLocation;
    }

    public String getExileLocation() {
        return exileLocation;
    }

    public void setExileLocation(String exileLocation) {
        this.exileLocation = exileLocation;
    }

    public double getExileBorderSize() {
        return exileBorderSize;
    }

    public void setExileBorderSize(double exileBorderSize) {
        this.exileBorderSize = exileBorderSize;
    }
}
