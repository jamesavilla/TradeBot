package data;

import system.Formatter;

import java.util.Date;

public class PriceBean {
    private final double price;
    private final long timestamp;
    private boolean closing;
    private double openPrice;
    private double previousClosePrice;
    private double previousRsi;
    private double previousOpenPrice;
    private double previousHighPrice;

    public PriceBean(long timestamp, double price, double openPrice, double previousClosePrice, double previousRsi, double previousOpenPrice, double previousHighPrice) {
        this.price = price;
        this.openPrice = openPrice;
        this.previousClosePrice = previousClosePrice;
        this.previousRsi = previousRsi;
        this.previousOpenPrice = previousOpenPrice;
        this.previousHighPrice = previousHighPrice;
        this.timestamp = timestamp;
        this.closing = false;
    }

    public PriceBean(long timestamp, double price, double openPrice, double previousClosePrice, double previousRsi, double previousOpenPrice, double previousHighPrice, boolean closing) {
        this.price = price;
        this.openPrice = openPrice;
        this.previousClosePrice = previousClosePrice;
        this.previousRsi = previousRsi;
        this.previousOpenPrice = previousOpenPrice;
        this.previousHighPrice = previousHighPrice;
        this.timestamp = timestamp;
        this.closing = closing;
    }

    public double getPrice() {
        return price;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public double getOpenPrice() {
        return openPrice;
    }

    public void setOpenPrice(double openingPrice) {
        this.openPrice = openingPrice;
    }

    public double getPreviousClosePrice() {
        return previousClosePrice;
    }

    public void setPreviousClosePrice(double previousClosingPrice) {
        this.previousClosePrice = previousClosingPrice;
    }

    public double getPreviousRsi() {
        return previousRsi;
    }

    public void setPreviousRsi(double previousRsi) {
        this.previousRsi = previousRsi;
    }

    public double getPreviousOpenPrice() {
        return previousOpenPrice;
    }

    public void setPreviousOpenPrice(double previousOpenPrice) {
        this.previousOpenPrice = previousOpenPrice;
    }

    public String getDate() {
        return Formatter.formatDate((timestamp));
    }

    public void close() {
        this.closing = true;
    }

    public boolean isClosing() {
        return closing;
    }

    @Override
    public String toString() {
        return Formatter.formatDate(timestamp) + " " + price + (closing ? " is closing" : "");
    }

    public String toCsvString(){
        return String.format("%s,%s,%d", new Date(timestamp), price, closing ? 1 : 0);
    }

    public String dumpAll() {
        return String.format("%s,%s,%s,%s,%s", new Date(timestamp), price, openPrice, previousClosePrice, previousOpenPrice);
    }
}
