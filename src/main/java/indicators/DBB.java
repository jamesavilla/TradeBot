package indicators;
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

    public DBB(List<Double> closingPrices, int period) {
        this.period = period;
        this.sma = new SMA(closingPrices, period);
        init(closingPrices);
    }

    @Override
    public double get() {
        if(closingPrice < lowerBand && closingPrice > previousClosingPrice && openingPrice < closingPrice) {
            //System.out.println("1");
            return 1;
        }
//        else if (lowerBand < closingPrice && closingPrice <= lowerMidBand) {
//            System.out.println("2");
//            return -1;
//        }
        else if (previousClosingPrice > upperBand && closingPrice < previousClosingPrice) {
            //System.out.println("3");
            return -1;
        }

//        else if (closingPrice < lowerBand) {
//            //System.out.println("4");
//            return -1;
//        }
        return 0;
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

        System.out.println("DBB - newPrice: " + newPrice + " tempLowerBand: " + tempLowerBand + " tempUpperBand: " + tempUpperBand + " previousClosePrice: " + previousClosePrice + " openPrice: " + openPrice + " previousOpenPrice: " + previousOpenPrice);

        //if ((tempUpperBand - tempLowerBand) / tempMidBand < 0.05) //Low volatility case
            //return 0;
        double previousOpenPricePadded = (previousOpenPrice*0.0025)+previousOpenPrice;
        double openPricePadded = (openPrice*0.001)+openPrice;
        double activeTradeOpenPrice = hasActiveTrade ? activeTrade.getOpenPrice() : 0;

        if((previousOpenPrice < tempLowerMidBand && previousClosePrice < tempLowerMidBand) && newPrice > tempLowerBand && newPrice < tempMidBand && newPrice > previousClosePrice && newPrice > previousOpenPricePadded && previousClosePrice > previousOpenPrice) {
        //if((previousClosePrice < tempLowerBand || previousOpenPrice < tempLowerBand) && newPrice > tempLowerBand && newPrice > openPrice) {
            System.out.println("1");
            return 1;
        }
        else if(previousClosePrice > tempLowerBand && previousClosePrice < tempLowerMidBand && newPrice > openPrice && newPrice > previousClosePrice && newPrice < tempMidBand && newPrice > openPricePadded) {
            System.out.println("2");
            return 1;
        }
        else if(newPrice > openPricePadded && newPrice > tempMidBand) {
            System.out.println("3");
            return 2;
        }
        // MID BUYS
        /*else if(newPrice > openPricePadded && newPrice > previousClosePrice && newPrice < tempUpperMidBand && newPrice > tempMidBand && previousClosePrice > previousOpenPrice) {
            return 1;
        }*/
//        else if (newPrice < tempLowerBand) {
//            return -1;
//        }
        else if (previousClosePrice > tempUpperBand && newPrice < previousClosePrice && newPrice < openPrice && newPrice < tempUpperBand && openPrice != activeTradeOpenPrice) {
            System.out.println("4");
            return -1;
        }
        else if (openPrice < previousClosePrice && newPrice < previousClosePrice && newPrice < tempLowerBand && newPrice < openPrice && openPrice != activeTradeOpenPrice) {
            System.out.println("5");
            return -1;
        }
//        else if (openPrice > tempUpperBand && newPrice < openPrice) {
//            System.out.println("INNNNNNNNNNNNNN" + newPrice);
//            return -1;
//        }

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
    public void update(double newPrice, double openPrice, double previousClosePrice, double previousRsi, double previousOpenPrice) {
        closingPrice = newPrice;
        openingPrice = openPrice;
        previousClosingPrice = previousClosePrice;
        sma.update(newPrice, openPrice, previousClosePrice, previousRsi, previousOpenPrice);
        standardDeviation = sma.standardDeviation();
        middleBand = sma.get();
        upperBand = middleBand + standardDeviation*2;
        upperMidBand = middleBand + standardDeviation;
        lowerMidBand = middleBand - standardDeviation;
        lowerBand = middleBand - standardDeviation*2;
    }

    @Override
    public int check(double newPrice, double openPrice, double previousClosePrice, double previousOpenPrice, boolean hasActiveTrade, Trade activeTrade) {
        if (getTemp(newPrice, openPrice, previousClosePrice, previousOpenPrice, hasActiveTrade, activeTrade) == 1) {
            explanation = "Price in DBB buy zone";
            return 1;
        }
        else if (getTemp(newPrice, openPrice, previousClosePrice, previousOpenPrice, hasActiveTrade, activeTrade) == -1) {
            explanation = "Price in DBB sell zone";
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
