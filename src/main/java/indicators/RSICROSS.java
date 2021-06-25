package indicators;

import system.Mode;
import trading.Trade;
import utilities.SlackMessage;
import utilities.SlackUtilities;

import java.util.LinkedList;
import java.util.List;

public class RSICROSS implements Indicator {

    private final LinkedList<Double> rsiMaHistory;
    private final RSI longRSI;
    private Indicator previousRSICrossValue;
    private String explanation;
    private boolean alertSent;
    public boolean rsiCrossedAbove = false;
    public boolean rsiCrossedBelow = false;
    private double previousRsiValue;
    double shortTemp;

    public RSICROSS(List<Double> closingPrices, int shortPeriod, int longPeriod) {
        this.rsiMaHistory = new LinkedList<>();
        this.longRSI = new RSI(closingPrices, longPeriod);
        explanation = "";
        previousRsiValue = 0;
        init(closingPrices);
    }

    @Override
    public void updateAlertSent() {
        alertSent = false;
    }

    @Override
    public double get() { return longRSI.get(); }

    @Override
    public String getName() { return "RSICROSS"; }

    @Override
    public double getTemp(double newPrice, double openPrice, double previousClosePrice, double previousOpenPrice, boolean hasActiveTrade, Trade activeTrade, String pair) {
        double longTemp = longRSI.getTemp(newPrice, openPrice, previousClosePrice, previousOpenPrice, hasActiveTrade, activeTrade, pair);
        double currentSum = 0;

        for (int i = 0; i < rsiMaHistory.size() - 1; i++) {
            currentSum += rsiMaHistory.get(i);
        }
        shortTemp = currentSum / (rsiMaHistory.size()-1);

        if(longTemp < 40 && !hasActiveTrade) {
            rsiCrossedBelow = true;
        }

        if(longTemp > 60 && hasActiveTrade) {
            rsiCrossedAbove = true;
        }

        if(rsiMaHistory.size() == 10) {
            /*if (!hasActiveTrade && rsiCrossedBelow && longTemp > (shortTemp + (shortTemp*.01)) && previousRsiValue < longTemp) {
                System.out.println("longTemp - " + longTemp);
                System.out.println("shortTemp - " + shortTemp);
                if (!alertSent && (Mode.get().equals(Mode.LIVE) || Mode.get().equals(Mode.SIMULATION))) {
                    System.out.println("RSI CROSS BUY!");
                    alertSent = true;
                    final String message = "RSI cross buy " + pair + "!";
                    SlackMessage slackMessage = SlackMessage.builder()
                            .text(":warning: " + message)
                            .build();
                    SlackUtilities.sendMessage(slackMessage);
                }
                rsiCrossedBelow = false;
                return 4;
            }*/

//            System.out.println("");
//            System.out.println(rsiCrossedAbove);
//            System.out.println(Math.floor(longTemp));
//            System.out.println(Math.floor(shortTemp)-1);
//            System.out.println("");

            if (hasActiveTrade && rsiCrossedAbove && Math.floor(longTemp) < (Math.floor(shortTemp)-2)) {
//                if(Mode.get().equals(Mode.BACKTESTING)) {
//                    System.out.println("");
//                    System.out.println("newPrice " + newPrice);
//                    System.out.println("RSI 1 " + rsiMaHistory);
//                    System.out.println("RSI 2 " + longTemp);
//                    System.out.println("RSI 3 " + shortTemp);
//                    System.out.println("");
//                }
                if (!alertSent && (Mode.get().equals(Mode.LIVE) || Mode.get().equals(Mode.SIMULATION))) {
                    System.out.println("RSI CROSS SELL!");
                    alertSent = true;
                    final String message = "RSI cross sell " + pair + "!";
                    SlackMessage slackMessage = SlackMessage.builder()
                            .text(":warning: " + message)
                            .build();
                    SlackUtilities.sendMessage(slackMessage);
                }
                rsiCrossedAbove = false;
                return -4;
            }

            /*if (hasActiveTrade && rsiCrossedBelow && newPrice < activeTrade.getOpenPrice()) {
                if (!alertSent && (Mode.get().equals(Mode.LIVE) || Mode.get().equals(Mode.SIMULATION))) {
                    System.out.println("RSI CROSS SELL!");
                    alertSent = true;
                    final String message = "RSI cross sell " + pair + "!";
                    SlackMessage slackMessage = SlackMessage.builder()
                            .text(":warning: " + message)
                            .build();
                    SlackUtilities.sendMessage(slackMessage);
                }
                rsiCrossedBelow = false;
                return -4;
            }*/
        }

        return 0;
    }

    @Override
    public void init(List<Double> closingPrices) {}

    @Override
    public void update(double newPrice, double openPrice, double previousClosePrice, double previousRsi, double previousDbb, Indicator previousEmaCross, Indicator previousRsiCross, double previousOpenPrice, double previousHighPrice) {
        longRSI.update(newPrice, openPrice, previousClosePrice, previousRsi, previousDbb, previousEmaCross, previousRsiCross, previousOpenPrice, previousHighPrice);

        previousRsiValue = previousRsi;

        if(rsiMaHistory.size() >= 10) {
            rsiMaHistory.removeFirst();
        }
        rsiMaHistory.add(longRSI.get());

        previousRSICrossValue = previousRsiCross;
    }

    @Override
    public double check(String pair, double newPrice, double openPrice, double previousClosePrice, double previousOpenPrice, boolean hasActiveTrade, Trade activeTrade) {
        double temp = getTemp(newPrice, openPrice, previousClosePrice, previousOpenPrice, hasActiveTrade, activeTrade, pair);
        if(temp != 0) {
            explanation = "RSI cross";
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

    public void resetFlags() {
        rsiCrossedAbove = false;
        rsiCrossedBelow = false;
    }

}
