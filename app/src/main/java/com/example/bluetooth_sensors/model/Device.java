package com.example.bluetooth_sensors.model;

public class Device {
    private final String address;
    private final String name;
    private float pressure = 0;
    private float temperature = 0;
    private float ch4 = 0;
    private float co = 0;
    private float humidity =0;
    private String status = "disconnected";

    public Device(String address, String name) {
        this.address = address;
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public String getName() {
        return name;
    }

    public float getPressure() {
        return pressure;
    }

    public void setPressure(float pressure) {
        this.pressure = pressure;
    }

    public float getTemperature() {
        return temperature;
    }

    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public float getCh4() {
        return ch4;
    }

    public void setCh4(float ch4) {
        this.ch4 = ch4;
    }

    public float getCo() {
        return co;
    }

    public void setCo(float co) {
        this.co = co;
    }

    public float getHumidity() {
        return humidity;
    }

    public void setHumidity(float humidity) {
        this.humidity = humidity;
    }
}
