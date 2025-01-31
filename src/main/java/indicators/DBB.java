package indicators;

import system.Mode;
import trading.Trade;

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
    public void updateAlertSent() {}

    @Override
    public double get() {
        return upperBand;
    }

    @Override
    public String getName() { return "DBB"; }

    @Override
    public double getTemp(double newPrice, double openPrice, double previousClosePrice, double previousOpenPrice, boolean hasActiveTrade, Trade activeTrade, String pair) {
        double tempMidBand = sma.getTemp(newPrice, openPrice, previousClosePrice, previousOpenPrice, hasActiveTrade, activeTrade, pair);
        double tempStdev = sma.tempStandardDeviation(newPrice);
        double tempUpperBand = tempMidBand + tempStdev * 2;
        double tempUpperMidBand = tempMidBand + tempStdev;
        double tempLowerMidBand = tempMidBand - tempStdev;
        double tempLowerBand = tempMidBand - tempStdev * 2;
        double previousOpenPricePadded = (previousOpenPrice*0.0025)+previousOpenPrice;
        double previousClosePricePadded = (previousClosePrice*0.0025)+previousClosePrice;
        double openPricePadded = (openPrice*0.0018)+openPrice;
        double activeTradeOpenPrice = hasActiveTrade ? activeTrade.getOpenPrice() : 0;
        double openPriceDrop = openPrice - (openPrice*0.02);
        double previousOpenToClose = (previousClosePrice>previousOpenPrice ? previousClosePrice-previousOpenPrice : previousOpenPrice-previousClosePrice)*0.25;
        double previousUpperPricePlusPreviousGap = ((Math.max(previousClosePrice, previousOpenPrice)) + previousOpenToClose);
        double tempPreviousUpperBand = previousDbbValue;
        int dbb = 0;
        boolean debugging = Mode.get() == Mode.BACKTESTING;

        if (newPrice > upperBand && hasActiveTrade) {
            activeTrade.setBrokeUpperDbbBand(true);
        }

        if (debugging) {
            System.out.println("DBB tempPreviousUpperBand: " + tempPreviousUpperBand + " DBB: newPrice: " + newPrice + " previousUpperPricePlusPreviousGap: " + previousUpperPricePlusPreviousGap + " tempLowerBand: " + tempLowerBand + " tempUpperBand: " + tempUpperBand + " previousClosePrice: " + previousClosePrice + " openPrice: " + openPrice + " previousOpenPrice: " + previousOpenPrice + " previousHighValue: " + previousHighValue);
        }

        /*if((previousOpenPrice < tempLowerMidBand && previousClosePrice < tempMidBand) &&
                newPrice > tempLowerBand &&
                newPrice < tempMidBand &&
                newPrice > previousOpenPricePadded &&
                newPrice > previousClosePricePadded &&
                previousClosePrice > previousOpenPrice &&
                openPrice != activeTradeOpenPrice &&
                newPrice > previousUpperPricePlusPreviousGap &&
                !hasActiveTrade) {
            dbb = 1;
            if (debugging) { System.out.println("DBB: " + dbb); }
            return 1;
        }
        else if(previousClosePrice > tempLowerBand &&
                previousClosePrice < tempLowerMidBand &&
                newPrice < tempLowerMidBand &&
                newPrice > openPricePadded &&
                newPrice > previousOpenPricePadded &&
                newPrice > previousClosePricePadded &&
                openPrice != activeTradeOpenPrice &&
                newPrice > previousUpperPricePlusPreviousGap &&
                !hasActiveTrade) {
            dbb = 2;
            if (debugging) { System.out.println("DBB: " + dbb); }
            return 1;
        }*/
        // BREAKOUT BUY
        if(openPrice > tempUpperMidBand &&
                previousOpenPrice > tempUpperMidBand &
                newPrice > openPrice &&
                newPrice > previousOpenPricePadded &&
                newPrice > previousClosePricePadded &&
                newPrice > previousUpperPricePlusPreviousGap &&
                openPrice != activeTradeOpenPrice &&
                !hasActiveTrade &&
                newPrice > previousHighValue) {
            dbb = 3;
            if (debugging) { System.out.println("DBB: " + dbb); }
            return 0.5;
        }
        // BREAKOUT SELL
        /*else if (previousClosePrice > tempPreviousUpperBand &&
                newPrice < previousClosePrice &&
                newPrice < openPrice &&
                newPrice < tempUpperMidBand &&
                openPrice != activeTradeOpenPrice &&
                hasActiveTrade) {
            dbb = 4;
            if (debugging) { System.out.println("DBB: " + dbb); }
            return -1;
        }*/
        // FALSE BREAKOUT SELL - GOES OVER UPPER BAND THEN DROPS
        /*else if (hasActiveTrade && activeTrade.getBrokeUpperDbbBand() && newPrice < tempMidBand && openPrice != activeTradeOpenPrice) {
            dbb = 5;
            if (debugging) { System.out.println("DBB: " + dbb); }
            return -1;
        }*/
        else if (newPrice < previousClosePrice && newPrice < tempLowerBand && newPrice < openPrice && openPrice != activeTradeOpenPrice && hasActiveTrade) {
            dbb = 6;
            if (debugging) { System.out.println("DBB: " + dbb); }
            return -1;
        }
        // IF CANDLE DROPS 1.0% IN A CANDLE THEN EXIT - openPrice > tempUpperMidBand maybe?
        else if (newPrice < openPriceDrop && openPrice != activeTradeOpenPrice && hasActiveTrade && newPrice < tempUpperMidBand) {
            dbb = 7;
            if (debugging) { System.out.println("DBB: " + dbb); }
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
    public void update(double newPrice, double openPrice, double previousClosePrice, double previousRsi, double previousDbb, Indicator previousEmaCross, Indicator previousRsiCross, double previousOpenPrice, double previousHighPrice) {
        closingPrice = newPrice;
        openingPrice = openPrice;
        previousClosingPrice = previousClosePrice;
        sma.update(newPrice, openPrice, previousClosePrice, previousRsi, previousDbb, previousEmaCross, previousRsiCross, previousOpenPrice, previousHighPrice);
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
    public double check(String pair, double newPrice, double openPrice, double previousClosePrice, double previousOpenPrice, boolean hasActiveTrade, Trade activeTrade) {
        double totalCnt = getTemp(newPrice, openPrice, previousClosePrice, previousOpenPrice, hasActiveTrade, activeTrade, pair);
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

    @Override
    public Indicator getIndicator() { return this; }
}
