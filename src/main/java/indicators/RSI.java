package indicators;

import system.Formatter;
import trading.Trade;

import java.util.Date;
import java.util.List;

public class RSI implements Indicator {

    private double avgUp;
    private double avgDwn;
    private double prevClose;
    private final int period;
    private String explanation;
    public static int POSITIVE_MIN;
    public static int POSITIVE_MAX;
    public static int NEGATIVE_MIN;
    public static int NEGATIVE_MAX;
    private double previousRsiValue;

    public RSI(List<Double> closingPrice, int period) {
        avgUp = 0;
        avgDwn = 0;
        this.period = period;
        explanation = "";
        previousRsiValue = 0;
        init(closingPrice);
    }

    @Override
    public void init(List<Double> closingPrices) {
        prevClose = closingPrices.get(0);
        for (int i = 1; i < period + 1; i++) {
            double change = closingPrices.get(i) - prevClose;
            if (change > 0) {
                avgUp += change;
            } else {
                avgDwn += Math.abs(change);
            }
        }

        //Initial SMA values
        avgUp = avgUp / (double) period;
        avgDwn = avgDwn / (double) period;

        //Dont use latest unclosed value
        for (int i = period + 1; i < closingPrices.size() - 1; i++) {
            update(closingPrices.get(i), 0, 0, 0, 0);
        }
    }

    @Override
    public double get() {
        return 100 - 100.0 / (1 + avgUp / avgDwn);
    }

    @Override
    public String getName() { return "RSI"; }

    @Override
    public double getTemp(double newPrice, double openPrice, double previousClosePrice, double previousOpenPrice, boolean hasActiveTrade, Trade activeTrade) {
        double change = newPrice - prevClose;
        double tempUp;
        double tempDwn;
        if (change > 0) {
            tempUp = (avgUp * (period - 1) + change) / (double) period;
            tempDwn = (avgDwn * (period - 1)) / (double) period;
        } else {
            tempDwn = (avgDwn * (period - 1) + Math.abs(change)) / (double) period;
            tempUp = (avgUp * (period - 1)) / (double) period;
        }
        return 100 - 100.0 / (1 + tempUp / tempDwn);
    }

    @Override
    public void update(double newPrice, double openPrice, double previousClosePrice, double previousRsi, double previousOpenPrice) {
        previousRsiValue = previousRsi;
        double change = newPrice - prevClose;
        if (change > 0) {
            avgUp = (avgUp * (period - 1) + change) / (double) period;
            avgDwn = (avgDwn * (period - 1)) / (double) period;
        } else {
            avgUp = (avgUp * (period - 1)) / (double) period;
            avgDwn = (avgDwn * (period - 1) + Math.abs(change)) / (double) period;
        }
        prevClose = newPrice;
    }

    @Override
    public int check(double newPrice, double openPrice, double previousClosePrice, double previousOpenPrice, boolean hasActiveTrade, Trade activeTrade) {
        double temp = getTemp(newPrice, openPrice, previousClosePrice, previousOpenPrice, hasActiveTrade, activeTrade);
        //System.out.println("RSI - temp: " + temp + " newPrice: " + newPrice + " POSITIVE_MAX: " + POSITIVE_MAX + " hasActiveTrade: " + hasActiveTrade);
        //System.out.println(" activeTrade: " + activeTrade);
        double previousRsiPadded = (previousRsiValue*0.05)+previousRsiValue;

        if(previousClosePrice == 0 || openPrice == 0) {
            //System.out.println("6");
            return 0;
        }
        else if (temp < POSITIVE_MIN) {
            //System.out.println("7");
            explanation = "RSI of " + Formatter.formatDecimal(temp);
            return 2;
        }
        else if (temp < POSITIVE_MAX && newPrice < openPrice) {
            //System.out.println("8");
            explanation = "RSI of " + Formatter.formatDecimal(temp);
            return -1;
        }
        else if (temp < POSITIVE_MAX && newPrice >= previousClosePrice && temp > previousRsiPadded) {
            //System.out.println("9");
            explanation = "RSI of " + Formatter.formatDecimal(temp) + " previousRsiValue:" + previousRsiValue + " previousRsiPadded:" + previousRsiPadded;
            return 1;
        }
        else if (temp > NEGATIVE_MIN && temp < previousRsiValue) {
            //System.out.println("10");
            explanation = "RSI of " + Formatter.formatDecimal(temp);
            return -1;
        }
        else if (temp > NEGATIVE_MAX && hasActiveTrade) {
            //System.out.println("11");
            explanation = "RSI of " + Formatter.formatDecimal(temp);
            return -1;
        }
        else if (temp > 60 && !hasActiveTrade) {
            //System.out.println("14");
            explanation = "RSI of " + Formatter.formatDecimal(temp);
            return 1;
        }
        else if (temp < (previousRsiValue-10)) {
            return -1;
        }
        explanation = "";
        return 0;
    }

    @Override
    public String getExplanation() {
        return explanation;
    }
}
