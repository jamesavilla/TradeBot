package indicators;

import trading.Trade;

import java.util.LinkedList;
import java.util.List;

public class SMA implements Indicator {

    private double currentSum;
    private final int period;
    private final LinkedList<Double> prices;

    public SMA(List<Double> closingPrices, int period) {
        this.period = period;
        prices = new LinkedList<>();
        init(closingPrices);
    }

    @Override
    public void updateAlertSent() {}

    @Override
    public double get() {
        return currentSum / (double) period;
    }

    @Override
    public String getName() { return ""; }

    @Override
    public double getTemp(double newPrice, double openPrice, double previousClosePrice, double previousOpenPrice, boolean hasActiveTrade, Trade activeTrade, String pair) {
        return ((currentSum - prices.get(0) + newPrice) / (double) period);
    }

    @Override
    public void init(List<Double> closingPrices) {
        if (period > closingPrices.size()) return;

        //Initial sum
        for (int i = closingPrices.size() - period - 1; i < closingPrices.size() - 1; i++) {
            prices.add(closingPrices.get(i));
            currentSum += (closingPrices.get(i));
        }
    }

    @Override
    public void update(double newPrice, double openPrice, double previousClosePrice, double previousRsi, double previousDbb, Indicator previousEmaCross, Indicator previousRsiCross, double previousOpenPrice, double previousHighPrice) {
        currentSum -= prices.get(0);
        prices.removeFirst();
        prices.add(newPrice);
        currentSum += newPrice;
    }

    @Override
    public double check(String pair, double newPrice, double openPrice, double previousClosePrice, double previousOpenPrice, boolean hasActiveTrade, Trade activeTrade) {
        return 0;
    }

    @Override
    public String getExplanation() {
        return null;
    }

    public double standardDeviation(){
        double mean = currentSum/ (double) period;
        double stdev = 0.0;
        for (double price : prices) {
            stdev += Math.pow(price-mean, 2);
        }
        return Math.sqrt(stdev/ (double) period);
    }

    public double tempStandardDeviation(double newPrice){

        double tempMean = (currentSum-prices.get(0) + newPrice) / (double) period;
        double tempStdev = 0.0;

        for (int i = 1; i < prices.size(); i++) {
            tempStdev += Math.pow(prices.get(i) - tempMean, 2);
        }

        tempStdev += Math.pow(newPrice - tempMean, 2);
        return Math.sqrt(tempStdev/ (double) period);

    }

    @Override
    public Indicator getIndicator() { return this; }
}
