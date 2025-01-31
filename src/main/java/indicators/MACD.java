package indicators;

import system.Formatter;
import trading.Trade;

import java.util.List;

//Default setting in crypto are period of 9, short 12 and long 26.
//MACD = 12 EMA - 26 EMA and compare to 9 period of MACD value.
public class MACD implements Indicator {

    private double currentMACD;
    private double currentSignal;
    private final EMA shortEMA; //Will be the EMA object for shortEMA-
    private final EMA longEMA; //Will be the EMA object for longEMA.
    private final int period; //Only value that has to be calculated in setInitial.
    private final double multiplier;
    private final int periodDifference;
    private String explanation;
    public static double SIGNAL_CHANGE;

    private double lastTick;

    public MACD(List<Double> closingPrices, int shortPeriod, int longPeriod, int signalPeriod) {
        this.shortEMA = new EMA(closingPrices, shortPeriod, true); //true, because history is needed in MACD calculations.
        this.longEMA = new EMA(closingPrices, longPeriod, true); //true for the same reasons.
        this.period = signalPeriod;
        this.multiplier = 2.0 / (double) (signalPeriod + 1);
        this.periodDifference = longPeriod - shortPeriod;
        explanation = "";
        init(closingPrices); //initializing the calculations to get current MACD and signal line.
    }

    @Override
    public void updateAlertSent() {}

    @Override
    public double get() {
        return currentMACD - currentSignal; //Difference between the values.
    }

    @Override
    public String getName() { return ""; }

    @Override
    public double getTemp(double newPrice, double openPrice, double previousClosePrice, double previousOpenPrice, boolean hasActiveTrade, Trade activeTrade, String pair) {
        //temporary values
        double longTemp = longEMA.getTemp(newPrice, openPrice, previousClosePrice, previousOpenPrice, hasActiveTrade, activeTrade, pair);
        double shortTemp = shortEMA.getTemp(newPrice, openPrice, previousClosePrice, previousOpenPrice, hasActiveTrade, activeTrade, pair);

        double tempMACD = shortTemp - longTemp;
        double tempSignal = tempMACD * multiplier + currentSignal * (1 - multiplier);
        return tempMACD - tempSignal; //Getting the difference between the two signals.
    }

    @Override
    public void init(List<Double> closingPrices) {
        //Initial signal line
        //i = longEMA.getPeriod(); because the sizes of shortEMA and longEMA are different.
        for (int i = longEMA.getPeriod(); i < longEMA.getPeriod() + period; i++) {
            //i value with shortEMA gets changed to compensate the list size difference
            currentMACD = shortEMA.getEMAhistory().get(i + periodDifference) - longEMA.getEMAhistory().get(i);
            currentSignal += currentMACD;
        }
        currentSignal = currentSignal / (double) period;

        //Everything after the first calculation of signal line.
        for (int i = longEMA.getPeriod() + period; i < longEMA.getEMAhistory().size(); i++) {
            currentMACD = shortEMA.getEMAhistory().get(i + periodDifference) - longEMA.getEMAhistory().get(i);
            currentSignal = currentMACD * multiplier + currentSignal * (1 - multiplier);
        }

        lastTick = get();
    }

    @Override
    public void update(double newPrice, double openPrice, double previousClosePrice, double previousRsi, double previousDbb, Indicator previousEmaCross, Indicator previousRsiCross, double previousOpenPrice, double previousHighPrice) {
        //Updating the EMA values before updating MACD and Signal line.
        lastTick = get();
        shortEMA.update(newPrice, openPrice, previousClosePrice, previousRsi, previousDbb, previousEmaCross, previousRsiCross, previousOpenPrice, previousHighPrice);
        longEMA.update(newPrice, openPrice, previousClosePrice, previousRsi, previousDbb, previousEmaCross, previousRsiCross, previousOpenPrice, previousHighPrice);
        currentMACD = shortEMA.get() - longEMA.get();
        currentSignal = currentMACD * multiplier + currentSignal * (1 - multiplier);
    }

    @Override
    public double check(String pair, double newPrice, double openPrice, double previousClosePrice, double previousOpenPrice, boolean hasActiveTrade, Trade activeTrade) {
        double change = (getTemp(newPrice, openPrice, previousClosePrice, previousOpenPrice, hasActiveTrade, activeTrade, pair) - lastTick) / Math.abs(lastTick);
        if (change > MACD.SIGNAL_CHANGE && get() < 0) {
            //System.out.println("12");
            explanation = "MACD histogram grew by " + Formatter.formatPercent(change);
            return 1;
        }
        /*if (change < -MACD.change) {
            explanation = "MACD histogram fell by " + Formatter.formatPercent(change);
            return -1;
        }*/
        explanation = "";
        return 0;
    }

    @Override
    public String getExplanation() {
        return explanation;
    }

    @Override
    public Indicator getIndicator() { return this; }
}
