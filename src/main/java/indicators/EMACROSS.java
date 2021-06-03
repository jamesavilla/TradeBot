package indicators;

import system.Formatter;
import system.Mode;
import trading.Trade;
import utilities.SlackMessage;
import utilities.SlackUtilities;

import javax.sound.midi.SysexMessage;
import java.util.List;

//Default setting in crypto are period of 9, short 12 and long 26.
//MACD = 12 EMA - 26 EMA and compare to 9 period of MACD value.
public class EMACROSS implements Indicator {

    private final EMA shortEMA; //Will be the EMA object for shortEMA-
    private final EMA longEMA; //Will be the EMA object for longEMA.
    private Indicator previousEmaCrossValue;
    private String explanation;
    private boolean alertSent;

    public EMACROSS(List<Double> closingPrices, int shortPeriod, int longPeriod) {
        this.shortEMA = new EMA(closingPrices, shortPeriod, true); //true, because history is needed in MACD calculations.
        this.longEMA = new EMA(closingPrices, longPeriod, true); //true for the same reasons.
        explanation = "";
        previousEmaCrossValue = null;
        init(closingPrices); //initializing the calculations to get current MACD and signal line.
    }

    @Override
    public void updateAlertSent() {
        alertSent = false;
    }

    @Override
    public double get() {
        EMACROSS previousEmaCrossObject = (EMACROSS) previousEmaCrossValue;
        if(previousEmaCrossObject != null) {
            return previousEmaCrossObject.getLongEMA().get();
        }
        return 0; }

    @Override
    public String getName() { return "EMACROSS"; }

    @Override
    public double getTemp(double newPrice, double openPrice, double previousClosePrice, double previousOpenPrice, boolean hasActiveTrade, Trade activeTrade, String pair) {
        double longTemp = longEMA.getTemp(newPrice, openPrice, previousClosePrice, previousOpenPrice, hasActiveTrade, activeTrade, pair);
        double shortTemp = shortEMA.getTemp(newPrice, openPrice, previousClosePrice, previousOpenPrice, hasActiveTrade, activeTrade, pair);
        EMACROSS previousEmaCrossObject = (EMACROSS) previousEmaCrossValue;
        double longTempPadded = longTemp + (longTemp*0.005);
        /*if(previousEmaCrossObject != null) {
            System.out.println("-------------------------------");
            System.out.println(previousEmaCrossObject.getShortEMA().get());
            System.out.println(previousEmaCrossObject.getLongEMA().get());
            System.out.println(shortTemp);
            System.out.println(longTemp);
            System.out.println("-------------------------------");
        }*/

        // previousEmaCrossObject != null && previousEmaCrossObject.getShortEMA().get() < previousEmaCrossObject.getLongEMA().get() &&
        if(previousEmaCrossObject != null && previousEmaCrossObject.getShortEMA().get() < previousEmaCrossObject.getLongEMA().get() && !hasActiveTrade && shortTemp > longTemp) {

//                System.out.println(previousEmaCrossObject.getShortEMA().get());
//                System.out.println(previousEmaCrossObject.getLongEMA().get());
//
//            System.out.println("shortTemp " +shortTemp);
//            System.out.println("longTemp " +longTemp);
//            System.out.println("longTempPadded " +longTempPadded);
            if(!alertSent && (Mode.get().equals(Mode.LIVE) || Mode.get().equals(Mode.SIMULATION))) {
                System.out.println("EMA CROSS BUY!");
                alertSent = true;
                final String message = "EMA cross buy " + pair + "!";
                SlackMessage slackMessage = SlackMessage.builder()
                        .text(":warning: " + message)
                        .build();
                SlackUtilities.sendMessage(slackMessage);
            }
            return 4;
        }

        // previousEmaCrossObject != null && previousEmaCrossObject.getShortEMA().get() > previousEmaCrossObject.getLongEMA().get() &&
        if(previousEmaCrossObject != null && previousEmaCrossObject.getShortEMA().get() > previousEmaCrossObject.getLongEMA().get() && hasActiveTrade && shortTemp < longTemp) {
//            System.out.println("previousEmaCrossObject.getShortEMA().get() " +previousEmaCrossObject.getShortEMA().get());
//            System.out.println("previousEmaCrossObject.getLongEMA().get() " +previousEmaCrossObject.getLongEMA().get());
//            System.out.println("shortTemp " +shortTemp);
//            System.out.println("longTemp " +longTemp);
//            System.out.println("longTempPadded " +longTempPadded);
            if(!alertSent && (Mode.get().equals(Mode.LIVE) || Mode.get().equals(Mode.SIMULATION))) {
                System.out.println("EMA CROSS SELL!");
                alertSent = true;
                final String message = "EMA cross sell " + pair + "!";
                SlackMessage slackMessage = SlackMessage.builder()
                        .text(":warning: " + message)
                        .build();
                SlackUtilities.sendMessage(slackMessage);
            }
            return -5;
        }

        return 0;
    }

    @Override
    public void init(List<Double> closingPrices) {}

    @Override
    public void update(double newPrice, double openPrice, double previousClosePrice, double previousRsi, double previousDbb, Indicator previousEmaCross, Indicator previousRsiCross, double previousOpenPrice, double previousHighPrice) {
        shortEMA.update(newPrice, openPrice, previousClosePrice, previousRsi, previousDbb, previousEmaCross, previousRsiCross, previousOpenPrice, previousHighPrice);
        longEMA.update(newPrice, openPrice, previousClosePrice, previousRsi, previousDbb, previousEmaCross, previousRsiCross, previousOpenPrice, previousHighPrice);
        previousEmaCrossValue = previousEmaCross;
    }

    @Override
    public double check(String pair, double newPrice, double openPrice, double previousClosePrice, double previousOpenPrice, boolean hasActiveTrade, Trade activeTrade) {
        double temp = getTemp(newPrice, openPrice, previousClosePrice, previousOpenPrice, hasActiveTrade, activeTrade, pair);
        if(temp != 0) {
            explanation = "EMA cross";
            return temp;
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

    public EMA getLongEMA() {
        return longEMA;
    }

    public EMA getShortEMA() {
        return shortEMA;
    }

    public Indicator getpreviousEMACROSS() {
        return previousEmaCrossValue;
    }
}
