package indicators;

import lombok.SneakyThrows;
import system.Formatter;
import system.Mode;
import trading.Trade;
import utilities.SlackMessage;
import utilities.SlackUtilities;

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
    private boolean alertSent;

    public RSI(List<Double> closingPrice, int period) {
        avgUp = 0;
        avgDwn = 0;
        this.period = period;
        explanation = "";
        previousRsiValue = 0;
        init(closingPrice);
    }

    @Override
    public void updateAlertSent() {
        alertSent = false;
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
            update(closingPrices.get(i), 0, 0, 0, 0, 0, 0);
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
    public void update(double newPrice, double openPrice, double previousClosePrice, double previousRsi, double previousDbb, double previousOpenPrice, double previousHighPrice) {
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

    @SneakyThrows
    @Override
    public double check(String pair, double newPrice, double openPrice, double previousClosePrice, double previousOpenPrice, boolean hasActiveTrade, Trade activeTrade) {
        double temp = getTemp(newPrice, openPrice, previousClosePrice, previousOpenPrice, hasActiveTrade, activeTrade);
        double previousRsiPadded = (previousRsiValue*0.05) + previousRsiValue;
        double previousRsiNegativePadded = previousRsiValue - (previousRsiValue*0.15);
        int rsi = 0;
        boolean debugging = Mode.get() == Mode.BACKTESTING;

        if (debugging) {
            System.out.println("RSI temp: " + temp + " newPrice: " + newPrice + " POSITIVE_MAX: " + POSITIVE_MAX + " hasActiveTrade: " + hasActiveTrade + " previousRsiValue: " + previousRsiValue + " previousRsiPadded: " + previousRsiPadded + " previousRsiNegativePadded: " + previousRsiNegativePadded);
        }

        // RSI alerts to slack for possible manual positions
        if (!alertSent && temp < 30 && !hasActiveTrade && (Mode.get().equals(Mode.LIVE) || Mode.get().equals(Mode.SIMULATION))) {
            alertSent = true;
            final String message = pair + " rsi of " + temp + "!";
            SlackMessage slackMessage = SlackMessage.builder()
                    .text(":warning: " + message)
                    .build();
            //SlackUtilities.sendMessage(slackMessage);
        }

        if(previousClosePrice == 0 || openPrice == 0) {
            rsi = 8;
            if (debugging) { System.out.println("RSI: " + rsi); }
            return 0;
        }
        else if (temp < POSITIVE_MIN) {
            rsi = 9;
            if (debugging) { System.out.println("RSI: " + rsi); }
            explanation = "RSI of " + Formatter.formatDecimal(temp);
            return 2;
        }
        else if (temp < POSITIVE_MAX && newPrice < openPrice) {
            rsi = 10;
            if (debugging) { System.out.println("RSI: " + rsi); }
            explanation = "RSI of " + Formatter.formatDecimal(temp);
            return -1;
        }
        else if (temp < POSITIVE_MAX && newPrice >= previousClosePrice && temp > previousRsiPadded) {
            rsi = 11;
            if (debugging) { System.out.println("RSI: " + rsi); }
            explanation = "RSI of " + Formatter.formatDecimal(temp) + " previousRsiValue:" + previousRsiValue + " previousRsiPadded:" + previousRsiPadded;
            return 1;
        }
        else if (temp > NEGATIVE_MIN && temp < previousRsiNegativePadded) {
            rsi = 12;
            if (debugging) { System.out.println("RSI: " + rsi + " previousRsiPadded: " + previousRsiPadded + " previousRsiValue: " + previousRsiValue + " previousRsiNegativePadded: " + previousRsiNegativePadded); }
            explanation = "RSI of " + Formatter.formatDecimal(temp);
            return -1;
        }
        else if (temp > NEGATIVE_MAX && hasActiveTrade) {
            rsi = 13;
            if (debugging) { System.out.println("RSI: " + rsi); }
            explanation = "RSI of " + Formatter.formatDecimal(temp);
            return -1;
        }
        // Not sure about this, too risky to jump in on a possible breakout based on RSI
        /*else if (temp > 68 && temp < 78 && !hasActiveTrade) {
            rsi = 14;
            if (debugging) { System.out.println("RSI: " + rsi); }
            explanation = "RSI of " + Formatter.formatDecimal(temp);
            return 1;
        }*/
        else if (temp < (previousRsiValue-10)) {
            rsi = 15;
            if (debugging) { System.out.println("RSI: " + rsi); }
            return -1;
        }
        else if (temp > previousRsiPadded) {
            rsi = 15;
            if (debugging) { System.out.println("RSI: " + rsi); }
            return 0.5;
        }

        explanation = "";
        return 0;
    }

    @Override
    public String getExplanation() {
        return explanation;
    }
}
