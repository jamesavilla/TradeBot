package indicators;

import trading.Trade;

import java.util.ArrayList;
import java.util.List;

/**
 * EXPONENTIAL MOVING AVERAGE
 */
public class EMA implements Indicator {

    private double currentEMA;
    private final int period;
    private final double multiplier;
    private final List<Double> EMAhistory;
    private final boolean historyNeeded;
    private String fileName;

    public EMA(List<Double> closingPrices, int period, boolean historyNeeded) {
        currentEMA = 0;
        this.period = period;
        this.historyNeeded = historyNeeded;
        this.multiplier = 2.0 / (double) (period + 1);
        this.EMAhistory = new ArrayList<>();
        init(closingPrices);
    }

    @Override
    public void updateAlertSent() {}

    @Override
    public double get() {
        return currentEMA;
    }

    @Override
    public String getName() { return ""; }

    @Override
    public double getTemp(double newPrice, double openPrice, double previousClosePrice, double previousOpenPrice, boolean hasActiveTrade, Trade activeTrade, String pair) {
        return (newPrice - currentEMA) * multiplier + currentEMA;
    }

    @Override
    public void init(List<Double> closingPrices) {
        if (period > closingPrices.size()) return;

        //Initial SMA
        for (int i = 0; i < period; i++) {
            currentEMA += closingPrices.get(i);
        }

        currentEMA = currentEMA / (double) period;
        if (historyNeeded) EMAhistory.add(currentEMA);
        //Dont use latest unclosed candle;
        for (int i = period; i < closingPrices.size() - 1; i++) {
            update(closingPrices.get(i), 0, 0, 0, 0, null, null, 0, 0);
        }
    }

    @Override
    public void update(double newPrice, double openPrice, double previousClosePrice, double previousRsi, double previousDbb, Indicator previousEmaCross, Indicator previousRsiCross, double previousOpenPrice, double previousHighPrice) {
        // EMA = (Close - EMA(previousBar)) * multiplier + EMA(previousBar)
        currentEMA = (newPrice - currentEMA) * multiplier + currentEMA;

        if (historyNeeded) EMAhistory.add(currentEMA);
    }

    @Override
    public double check(String pair, double newPrice, double openPrice, double previousClosePrice, double previousOpenPrice, boolean hasActiveTrade, Trade activeTrade) {
        return 0;
    }

    @Override
    public String getExplanation() {
        return null;
    }

    public List<Double> getEMAhistory() {
        return EMAhistory;
    }

    public int getPeriod() {
        return period;
    }

    @Override
    public Indicator getIndicator() { return this; }
}
