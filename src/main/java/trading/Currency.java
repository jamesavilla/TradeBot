package trading;

import data.PriceBean;
import data.PriceReader;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import indicators.DBB;
import indicators.Indicator;
import indicators.MACD;
import indicators.RSI;
import system.ConfigSetup;
import system.Formatter;
import system.Mode;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class Currency {
    public static int CONFLUENCE;

    private final String pair;
    private Trade activeTrade;
    private long candleTime;
    private final List<Indicator> indicators = new ArrayList<>();
    private final AtomicBoolean currentlyCalculating = new AtomicBoolean(false);

    private double currentPrice;
    private long currentTime;
    private double currentOpenPrice;
    private double previousClosePrice;
    private double previousRSI;
    private double previousOpenPrice;

    //Backtesting data
    private final StringBuilder log = new StringBuilder();
    private PriceBean firstBean;

    private PriceBean previousBean = null;

    //Used for SIMULATION and LIVE
    public Currency(String coin) {
        this.pair = coin + ConfigSetup.getFiat();

        AtomicBoolean setCurrentOpenPrice = new AtomicBoolean(false);

        //Every currency needs to contain and update our indicators
        List<Candlestick> history = CurrentAPI.get().getCandlestickBars(pair, CandlestickInterval.FIVE_MINUTES);
        List<Double> closingPrices = history.stream().map(candle -> Double.parseDouble(candle.getClose())).collect(Collectors.toList());
        indicators.add(new RSI(closingPrices, 14));
        indicators.add(new MACD(closingPrices, 12, 26, 9));
        indicators.add(new DBB(closingPrices, 20));

        //We set the initial values to check against in onMessage based on the latest candle in history
        currentTime = System.currentTimeMillis();
        candleTime = history.get(history.size() - 1).getCloseTime();
        currentPrice = Double.parseDouble(history.get(history.size() - 1).getClose());
        currentOpenPrice = Double.parseDouble(history.get(history.size() - 1).getOpen());
        previousClosePrice = Double.parseDouble(history.get(history.size() - 2).getClose());
        previousOpenPrice = Double.parseDouble(history.get(history.size() - 2).getOpen());

        BinanceApiWebSocketClient client = CurrentAPI.getFactory().newWebSocketClient();
        //We add a websocket listener that automatically updates our values and triggers our strategy or trade logic as needed
        client.onAggTradeEvent(pair.toLowerCase(), response -> {
            //Every message and the resulting indicator and strategy calculations is handled concurrently
            //System.out.println(Thread.currentThread().getId());
            double newPrice = Double.parseDouble(response.getPrice());
            long newTime = response.getEventTime();

            //We want to toss messages that provide no new information
            if (currentPrice == newPrice && newTime <= candleTime) {
                return;
            }

            if(setCurrentOpenPrice.get()) {
                currentOpenPrice = newPrice;
                setCurrentOpenPrice.set(false);
            }

            //System.out.println("CANDLE " + response);

            if (newTime > candleTime) {
                //System.out.println("CANDLE " + response);
                accept(new PriceBean(candleTime, newPrice, currentOpenPrice, previousClosePrice, 0, previousOpenPrice, true));
                candleTime += 300000L;
                setCurrentOpenPrice.set(true);
                previousClosePrice = newPrice;
                previousOpenPrice = currentOpenPrice;
            }

            //System.out.println(String.format("%s,%s,%s,%s,%s",new Date(candleTime),newPrice,currentPrice,currentOpenPrice,previousClosePrice));

            accept(new PriceBean(newTime, newPrice, currentOpenPrice, previousClosePrice, 0, previousOpenPrice));
        });
        System.out.println("---SETUP DONE FOR " + this);
    }

    //Used for BACKTESTING
    public Currency(String pair, String filePath) {
        this.pair = pair;

        AtomicBoolean setCurrentOpenPrice = new AtomicBoolean(false);

        currentOpenPrice = 0;
        previousClosePrice = 0;

        try (PriceReader reader = new PriceReader(filePath)) {
            PriceBean bean = reader.readPrice();

            firstBean = bean;
            List<Double> closingPrices = new ArrayList<>();
            while (bean.isClosing()) {
                closingPrices.add(bean.getPrice());
                bean = reader.readPrice();
            }
            //TODO: Fix slight mismatch between MACD backtesting and server values.
            indicators.add(new RSI(closingPrices, 14));
            indicators.add(new MACD(closingPrices, 12, 26, 9));
            indicators.add(new DBB(closingPrices, 20));
            while (bean != null) {

                if(setCurrentOpenPrice.get()) {
                    currentOpenPrice = bean.getPrice();
                    setCurrentOpenPrice.set(false);
                }

                bean.setOpenPrice(currentOpenPrice);
                bean.setPreviousClosePrice(previousClosePrice);
                accept(bean);

                if(bean.isClosing()) {
                    setCurrentOpenPrice.set(true);
                    previousClosePrice = bean.getPrice();
                    previousOpenPrice = currentOpenPrice;
                }
                //System.out.println(bean.dumpAll());
                bean = reader.readPrice();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void accept(PriceBean bean) {
        //Make sure we dont get concurrency issues
        if (currentlyCalculating.get()) {
            System.out.println("------------WARNING, NEW THREAD STARTED ON " + pair + " MESSAGE DURING UNFINISHED PREVIOUS MESSAGE CALCULATIONS");
        }

        currentPrice = bean.getPrice();
        currentTime = bean.getTimestamp();
        currentOpenPrice = bean.getOpenPrice();
        previousClosePrice = bean.getPreviousClosePrice();

        if (bean.isClosing()) {
            Optional<Indicator> maybeRsiIndicator = indicators.stream().filter(indicator -> indicator.getName().equals("RSI")).findFirst();
            if(maybeRsiIndicator.isPresent()) {
                Indicator rsiIndicator = maybeRsiIndicator.get();
                previousRSI = rsiIndicator.get();
                bean.setPreviousRsi(previousRSI);
            }
            //System.out.println(new Date(currentTime) + " " + currentPrice + " " + previousClosePrice + " " + currentOpenPrice + " " + previousRSI);

//            Optional<Indicator> maybeDbbIndicator = indicators.stream().filter(indicator -> indicator.getName().equals("DBB")).findFirst();
//            if(maybeDbbIndicator.isPresent()) {
//                System.out.println(maybeDbbIndicator.get().get());
//            }

            indicators.forEach(indicator -> {
                indicator.update(bean.getPrice(), bean.getOpenPrice(), bean.getPreviousClosePrice(), bean.getPreviousRsi(), bean.getPreviousOpenPrice());
            });
            if (Mode.get().equals(Mode.BACKTESTING)) {
                appendLogLine(system.Formatter.formatDate(currentTime) + "  " + toString());
            }
        }

        if (!currentlyCalculating.get()) {
            currentlyCalculating.set(true);
            //We can disable the strategy and trading logic to only check indicator and price accuracy
            int confluence = check();
            //System.out.println("confluence " + confluence);
            if (hasActiveTrade()) { //We only allow one active trade per currency, this means we only need to do one of the following:
                activeTrade.update(currentPrice, confluence);//Update the active trade stop-loss and high values
            } else {
                if (confluence >= CONFLUENCE) {
                    BuySell.open(Currency.this, "Trade opened due to: " + getExplanations());
                }
            }
            currentlyCalculating.set(false);
        }
    }

    public int check() {
        return indicators.stream().mapToInt(indicator -> indicator.check(currentPrice, currentOpenPrice, previousClosePrice, previousOpenPrice)).sum();
    }

    public String getExplanations() {
        StringBuilder builder = new StringBuilder();
        for (Indicator indicator : indicators) {
            String explanation = indicator.getExplanation();
            if (explanation == null) explanation = "";
            builder.append(explanation.equals("") ? "" : explanation + "\t");
        }
        return builder.toString();
    }

    public String getPair() {
        return pair;
    }

    public double getPrice() {
        return currentPrice;
    }

    public long getCurrentTime() {
        return currentTime;
    }

    public boolean hasActiveTrade() {
        return activeTrade != null;
    }

    public void setActiveTrade(Trade activeTrade) {
        this.activeTrade = activeTrade;
    }

    public Trade getActiveTrade() {
        return activeTrade;
    }

    public void appendLogLine(String s) {
        log.append(s).append("\n");
    }

    public void log(String path) {
        List<Trade> tradeHistory = new ArrayList<>(BuySell.getAccount().getTradeHistory());
        try (FileWriter writer = new FileWriter(path)) {
            writer.write("Test ended " + system.Formatter.formatDate(LocalDateTime.now()) + " \n");
            writer.write("\n\nCONFIG:\n");
            writer.write(ConfigSetup.getSetup());
            writer.write("\n\nMarket performance: " + system.Formatter.formatPercent((currentPrice - firstBean.getPrice()) / firstBean.getPrice()));
            if (!tradeHistory.isEmpty()) {
                tradeHistory.sort(Comparator.comparingDouble(Trade::getProfit));
                double maxLoss = tradeHistory.get(0).getProfit();
                double maxGain = tradeHistory.get(tradeHistory.size() - 1).getProfit();
                int lossTrades = 0;
                double lossSum = 0;
                int gainTrades = 0;
                double gainSum = 0;
                long tradeDurs = 0;
                for (Trade trade : tradeHistory) {
                    double profit = trade.getProfit();
                    if (profit < 0) {
                        lossTrades += 1;
                        lossSum += profit;
                    } else if (profit > 0) {
                        gainTrades += 1;
                        gainSum += profit;
                    }
                    tradeDurs += trade.getDuration();
                }

                double tradePerWeek = 604800000.0 / (((double) currentTime - firstBean.getTimestamp()) / tradeHistory.size());

                writer.write("\nBot performance: " + system.Formatter.formatPercent(BuySell.getAccount().getProfit()) + "\n\n");
                writer.write(BuySell.getAccount().getTradeHistory().size() + " closed trades"
                        + " (" + system.Formatter.formatDecimal(tradePerWeek) + " trades per week) with an average holding length of "
                        + system.Formatter.formatDuration(Duration.of(tradeDurs / tradeHistory.size(), ChronoUnit.MILLIS)) + " hours");
                if (lossTrades != 0) {
                    writer.write("\nLoss trades:\n");
                    writer.write(lossTrades + " trades, " + system.Formatter.formatPercent(lossSum / (double) lossTrades) + " average, " + system.Formatter.formatPercent(maxLoss) + " max");
                }
                if (gainTrades != 0) {
                    writer.write("\nProfitable trades:\n");
                    writer.write(gainTrades + " trades, " + system.Formatter.formatPercent(gainSum / (double) gainTrades) + " average, " + system.Formatter.formatPercent(maxGain) + " max");
                }
                writer.write("\n\nClosed trades (least to most profitable):\n");
                for (Trade trade : tradeHistory) {
                    writer.write(trade.toString() + "\n");
                }
            } else {
                writer.write("\n(Not trades made)\n");
                System.out.println("---No trades made in the time period!");
            }
            writer.write("\n\nFULL LOG:\n\n");
            writer.write(log.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("---Log file generated at " + new File(path).getAbsolutePath());
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder(pair + " price: " + currentPrice + " open: " + currentOpenPrice + " previous close: " + previousClosePrice + " previous open: " + previousOpenPrice);
        if (currentTime == candleTime)
            indicators.forEach(indicator -> s.append(", ").append(indicator.getClass().getSimpleName()).append(": ").append(system.Formatter.formatDecimal(indicator.get())));
        else
            indicators.forEach(indicator -> s.append(", ").append(indicator.getClass().getSimpleName()).append(": ").append(Formatter.formatDecimal(indicator.getTemp(currentPrice, currentOpenPrice, previousClosePrice, previousOpenPrice))));
        s.append(", hasActive: ").append(hasActiveTrade()).append(")");
        return s.toString();
    }

    @Override
    public int hashCode() {
        return pair.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (obj.getClass() != Currency.class) return false;
        return pair.equals(((Currency) obj).pair);
    }
}
