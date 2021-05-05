package indicators;

import trading.Trade;

import java.util.List;

public interface Indicator {

    void updateAlertSent();

    //Used to get the latest indicator value updated with closed candle
    double get();

    //Used to get the name of the indicator for filtering
    String getName();

    //Used to get value of indicator simulated with the latest non-closed price
    double getTemp(double newPrice, double openPrice, double previousClosePrice, double previousOpenPrice, boolean hasActiveTrade, Trade activeTrade);

    //Used in constructor to set initial value
    void init(List<Double> closingPrices);

    //Used to update value with latest closed candle closing price
    void update(double newPrice, double openPrice, double previousClosePrice, double previousRsi, double previousDbb, double previousOpenPrice, double previousHighPrice);

    //Used to check for buy signal
    double check(String pair, double newPrice, double openPrice, double previousClosePrice, double previousOpenPrice, boolean hasActiveTrade, Trade activeTrade);

    String getExplanation();
}
