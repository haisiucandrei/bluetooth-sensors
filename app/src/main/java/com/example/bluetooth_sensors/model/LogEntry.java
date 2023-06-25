package com.example.bluetooth_sensors.model;

import java.text.DecimalFormat;
import java.time.LocalDateTime;

public class LogEntry {
    public final float temperature;
    public final float pressure;
    public final float ch4;
    public final float co;
    public final float humidity;

    public static String getMeasurementDate() {
        LocalDateTime now = LocalDateTime.now();
        DecimalFormat decimalFormat = new DecimalFormat();
        decimalFormat.setMinimumIntegerDigits(2);

        String measurementDate = now.getYear() + "-"
                + decimalFormat.format(now.getMonthValue()) + "-"
                + decimalFormat.format(now.getDayOfMonth());
        return measurementDate;
    }

    public static String getMeasurementTime() {
        LocalDateTime now = LocalDateTime.now();
        DecimalFormat decimalFormat = new DecimalFormat();
        decimalFormat.setMinimumIntegerDigits(2);

        String measurementTime = decimalFormat.format(now.getHour()) + "-"
                + decimalFormat.format(now.getMinute()) + "-"
                + decimalFormat.format(now.getSecond());
        return measurementTime;
    }

    public static String getMeasurementDateAndTime() {
        return getMeasurementDate() + "-" + getMeasurementTime();
    }

    public LogEntry(float temperature, float pressure, float ch4, float co,float humidity) {
        this.temperature = temperature;
        this.pressure = pressure;
        this.ch4 = ch4;
        this.co = co;
        this.humidity = humidity;
    }

    public float getTemperature() {
        return temperature;
    }

    public float getPressure() {
        return pressure;
    }
}
