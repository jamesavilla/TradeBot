package indicators;
import system.Mode;
import trading.Trade;

import java.util.Date;
import java.util.List;

public class DBB implements Indicator {
    private double closingPrice;
    private double openingPrice;
    private double previousClosingPrice;
    private double standardDeviation;
    private final int period;
    private double upperBand;
    private double upperMidBand;
    private double middleBand;
    private double lowerMidBand;
    private double lowerBand;
    private String explanation;
    private SMA sma;
    private double previousDbbValue;
    private double previousHighValue;

    public DBB(List<Double> closingPrices, int period) {
        this.period = period;
        this.sma = new SMA(closingPrices, period);
        previousDbbValue = 0;
        previousHighValue = 0;
        init(closingPrices);
    }

    @Override
    public double get() {
        return upperBand;
    }

    @Override
    public String getName() { return "DBB"; }

    @Override
    public double getTemp(double newPrice, double openPrice, double previousClosePrice, double previousOpenPrice, boolean hasActiveTrade, Trade activeTrade) {
        double tempMidBand = sma.getTemp(newPrice, openPrice, previousClosePrice, previousOpenPrice, hasActiveTrade, activeTrade);
        double tempStdev = sma.tempStandardDeviation(newPrice);
        double tempUpperBand = tempMidBand + tempStdev * 2;
        double tempUpperMidBand = tempMidBand + tempStdev;
        double tempLowerMidBand = tempMidBand - tempStdev;
        double tempLowerBand = tempMidBand - tempStdev * 2;
        double previousOpenPricePadded = (previousOpenPrice*0.0025)+previousOpenPrice;
        double previousClosePricePadded = (previousClosePrice*0.0025)+previousClosePrice;
        double openPricePadded = (openPrice*0.0018)+openPrice;
        double activeTradeOpenPrice = hasActiveTrade ? activeTrade.getOpenPrice() : 0;
        double openPriceDrop = openPrice - (openPrice*0.01);
        double previousOpenToClose = (previousClosePrice>previousOpenPrice ? previousClosePrice-previousOpenPrice : previousOpenPrice-previousClosePrice)*0.25;
        double newPricePlusPreviousGap = (previousClosePrice>previousOpenPrice ? previousClosePrice : previousOpenPrice) + previousOpenToClose;
        double tempPreviousUpperBand = previousDbbValue;
        int dbb = 0;

        if (Mode.get() == Mode.BACKTESTING) {
            System.out.println("DBB tempPreviousUpperBand: " + tempPreviousUpperBand + " DBB: newPrice: " + newPrice + " newPricePlusPreviousGap: " + newPricePlusPreviousGap + " tempLowerBand: " + tempLowerBand + " tempUpperBand: " + tempUpperBand + " previousClosePrice: " + previousClosePrice + " openPrice: " + openPrice + " previousOpenPrice: " + previousOpenPrice + " previousHighValue: " + previousHighValue);
        }

        if((previousOpenPrice < tempLowerMidBand && previousClosePrice < tempLowerMidBand) && newPrice > tempLowerBand && newPrice < tempMidBand && newPrice > previousClosePrice && newPrice > previousOpenPricePadded && newPrice > previousClosePricePadded && previousClosePrice > previousOpenPrice && openPrice != activeTradeOpenPrice && newPrice > newPricePlusPreviousGap && !hasActiveTrade) {
            dbb = 1;
            if (Mode.get() == Mode.BACKTESTING) { System.out.println("DBB: " + dbb); }
            return 1;
        }
        else if(previousClosePrice > tempLowerBand && previousClosePrice < tempLowerMidBand && newPrice > openPrice && newPrice > previousClosePrice && newPrice < tempLowerMidBand && newPrice > openPricePadded && newPrice > previousOpenPricePadded && newPrice > previousClosePricePadded && openPrice != activeTradeOpenPrice && newPrice > newPricePlusPreviousGap && !hasActiveTrade) {
            dbb = 2;
            if (Mode.get() == Mode.BACKTESTING) { System.out.println("DBB: " + dbb); }
            return 1;
        }
        // BREAKOUT BUY
        else if(openPrice > tempUpperMidBand && previousOpenPrice > tempUpperMidBand & newPrice > openPrice && newPrice > tempMidBand && newPrice > previousOpenPricePadded && newPrice > previousClosePricePadded && newPrice > newPricePlusPreviousGap && openPrice != activeTradeOpenPrice && !hasActiveTrade && newPrice > previousHighValue) {
            dbb = 3;
            if (Mode.get() == Mode.BACKTESTING) { System.out.println("DBB: " + dbb); }
            return 1;
        }
        // BREAKOUT SELL
        else if (previousClosePrice > tempPreviousUpperBand && newPrice < previousOpenPrice && newPrice < openPrice && newPrice < tempUpperBand && openPrice != activeTradeOpenPrice && hasActiveTrade) {
            dbb = 4;
            if (Mode.get() == Mode.BACKTESTING) { System.out.println("DBB: " + dbb); }
            return -1;
        }
        else if (newPrice < previousClosePrice && newPrice < tempLowerBand && newPrice < openPrice && openPrice != activeTradeOpenPrice && hasActiveTrade) {
            dbb = 5;
            if (Mode.get() == Mode.BACKTESTING) { System.out.println("DBB: " + dbb); }
            return -1;
        }
        // IF CANDLE DROPS 1.0% IN A 5 MINUTE CANDLE THEN EXIT
        else if (openPrice > tempUpperMidBand && newPrice < openPriceDrop && openPrice != activeTradeOpenPrice && hasActiveTrade && newPrice < tempUpperBand) {
            dbb = 6;
            if (Mode.get() == Mode.BACKTESTING) { System.out.println("DBB: " + dbb); }
            return -2;
        }

        return 0;
    }

    @Override
    public void init(List<Double> closingPrices) {
        if (period > closingPrices.size()) return;

        closingPrice = closingPrices.size() - 2;
        standardDeviation = sma.standardDeviation();
        middleBand = sma.get();
        upperBand = middleBand + standardDeviation*2;
        upperMidBand = middleBand + standardDeviation;
        lowerMidBand = middleBand - standardDeviation;
        lowerBand = middleBand - standardDeviation*2;

    }

    @Override
    public void update(double newPrice, double openPrice, double previousClosePrice, double previousRsi, double previousDbb, double previousOpenPrice, double previousHighPrice) {
        closingPrice = newPrice;
        openingPrice = openPrice;
        previousClosingPrice = previousClosePrice;
        sma.update(newPrice, openPrice, previousClosePrice, previousRsi, previousDbb, previousOpenPrice, previousHighPrice);
        standardDeviation = sma.standardDeviation();
        middleBand = sma.get();
        upperBand = middleBand + standardDeviation*2;
        upperMidBand = middleBand + standardDeviation;
        lowerMidBand = middleBand - standardDeviation;
        lowerBand = middleBand - standardDeviation*2;
        previousDbbValue = previousDbb;
        previousHighValue = previousHighPrice;
    }

    @Override
    public int check(double newPrice, double openPrice, double previousClosePrice, double previousOpenPrice, boolean hasActiveTrade, Trade activeTrade) {
        int totalCnt = (int) getTemp(newPrice, openPrice, previousClosePrice, previousOpenPrice, hasActiveTrade, activeTrade);
        if (totalCnt >= 1) {
            explanation = "Price in DBB buy zone";
            return totalCnt;
        }
        else if (totalCnt <= -1) {
            explanation = "Price in DBB sell zone";
            return totalCnt;
        }
        explanation = "";
        return 0;
    }

    @Override
    public String getExplanation() {
        return explanation;
    }
}
