package modes;

import com.binance.api.client.BinanceApiAsyncRestClient;
import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.domain.market.AggTrade;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.exception.BinanceApiException;
import data.PriceBean;
import data.PriceReader;
import data.PriceWriter;
import org.apache.commons.io.FileUtils;
import system.ConfigSetup;
import trading.CurrentAPI;
import system.Formatter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Semaphore;

//TODO: Identify cause and fix occasional date regression (maybe related to resending?)
public final class Collection {
    private static String symbol;
    private static String lastMessage = "Sending requests...";
    private static boolean braked = false;
    private static boolean createdBrakeTimer = false;
    private static int brakeSeconds = 1;
    private static int chunks;
    private static long initTime;
    private static final Scanner sc = new Scanner(System.in);
    private static final File backtestingFolder = new File("backtesting");

    public static final String INTERRUPT_MESSAGE = "Thread interrupted while waiting for request permission";
    private static final Semaphore blocker = new Semaphore(0);
    private static final Semaphore requestTracker = new Semaphore(0);
    private static final BinanceApiAsyncRestClient client = CurrentAPI.getFactory().newAsyncRestClient();

    private Collection() {
        throw new IllegalStateException("Utility class");
    }

    public static void setBrakeSeconds(int brakeSeconds) {
        Collection.brakeSeconds = brakeSeconds;
    }

    public static boolean isBraked() {
        return braked;
    }

    public static void setBraked(boolean braked) {
        Collection.braked = braked;
    }

    public static Semaphore getRequestTracker() {
        return requestTracker;
    }

    public static Semaphore getBlocker() {
        return blocker;
    }

    public static BinanceApiAsyncRestClient getClient() {
        return client;
    }

    public static String getSymbol() {
        return symbol;
    }

    public static void printProgress() {
        double progress = (double) blocker.availablePermits() / (double) chunks;
        long time = System.currentTimeMillis() - initTime;
        System.out.print("\r(" + Formatter.formatDuration((long) Math.ceil((time / progress) - time)) + ") (" + Formatter.formatPercent(progress) + ") " + lastMessage);
    }

    public static void setLastMessage(String lastMessage) {
        Collection.lastMessage = lastMessage;
        printProgress();
    }

    private static void collectionInterface() {
        if (backtestingFolder.exists() && backtestingFolder.isDirectory()) {
            String[] backtestingFiles = getDataFiles();
            if (backtestingFiles.length == 0) {
                System.out.println("---No backtesting files detected");
                return;
            }

            String input = "";
            while (!input.equalsIgnoreCase("new")) {
                if (input.equalsIgnoreCase("quit")) {
                    System.exit(0);
                }
                if (input.matches("\\d+")) {
                    int index = Integer.parseInt(input);
                    if (index <= backtestingFiles.length) {
                        describe("backtesting\\" + backtestingFiles[index - 1]);
                    }
                }
                System.out.println("\nEnter a number to describe the backtesting data file\n");
                for (int i = 0; i < backtestingFiles.length; i++) {
                    System.out.println("[" + (i + 1) + "] " + backtestingFiles[i]);
                }
                System.out.println("\nEnter \"new\" to start collecting a new data file");
                System.out.println("Enter \"quit\" to exit the program\n");
                input = sc.nextLine();
            }
        } else {
            System.out.println("---No backtesting files detected");
        }
    }

    public static String[] getDataFiles() {
        String[] backtestingFiles = backtestingFolder.list();
        if (backtestingFiles == null) {
            return new String[0];
        }
        return backtestingFiles;
    }

    public static void startCollection() {
        collectionInterface();
        System.out.println("Enter collectable currency (BTC, LINK, ETH...)");
        while (true) {
            try {
                symbol = sc.nextLine().toUpperCase() + ConfigSetup.getFiat();
                CurrentAPI.get().getPrice(symbol);
                break;
            } catch (BinanceApiException e) {
                System.out.println(e.getMessage());
            }
        }

        System.out.println("Enter everything in double digits. (1 = 01) \n " +
                "example: 2020/03/01 00:00:00");
        System.out.println("Date format = 'yyyy/MM/dd HH:mm:ss'");
        System.out.println("Start needs to be earlier than end\n");

        SimpleDateFormat dateFormat = Formatter.getSimpleFormatter();
        Date startDate;
        Date stopDate;
        while (true) {
            System.out.println("Enter the date you want to start from: ");
            String begin = sc.nextLine();
            System.out.println("Enter the date you want to finish with (type \"now\" for current time): ");
            String finish = sc.nextLine();
            try {
                startDate = dateFormat.parse(begin);
                if (finish.equalsIgnoreCase("now")) {
                    stopDate = new Date(System.currentTimeMillis());
                } else {
                    stopDate = dateFormat.parse(finish);
                }
                if (startDate.getTime() >= stopDate.getTime() || stopDate.getTime() > System.currentTimeMillis()) {
                    System.out.println("Start needs to be earlier in time than end and end cannot be greater than current time");
                    continue;
                }
                break;
            } catch (ParseException e) {
                System.out.println(e.getLocalizedMessage());
            }
        }

        long start = startDate.getTime(); // March 1 00:00:00 1583020800000
        long end = stopDate.getTime();// April 1 00:00:00 1585699200000
        chunks = (int) Math.ceil((double) (end - start) / 3600000L);

        System.out.println("\n---Setting up...");
        String filename = Path.of("backtesting", symbol + "_" + Formatter.formatOnlyDate(start) + "-" + Formatter.formatOnlyDate(end) + ".dat").toString();

        deleteTemp();
        new File("temp").mkdir();
        if (!(backtestingFolder.exists() && backtestingFolder.isDirectory())) backtestingFolder.mkdir();

        System.out.println("---Sending " + chunks + " requests (minimum estimate is " + (Formatter.formatDuration((long) ((double) chunks / (double) ConfigSetup.getRequestLimit() * 60000L)) + ")..."));
        int requestDelay = 60000 / ConfigSetup.getRequestLimit();
        initTime = System.currentTimeMillis();
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!isBraked()) {
                    requestTracker.release(1);
                } else if (!createdBrakeTimer) {
                    Timer brakeTimer = new Timer();
                    brakeTimer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            setLastMessage("Removing request brake");
                            setBraked(false);
                            createdBrakeTimer = false;
                        }
                    }, brakeSeconds);
                    setLastMessage("Braked requests for " + brakeSeconds + " seconds");
                    createdBrakeTimer = true;
                }
            }
        }, requestDelay, requestDelay);
        int id = 0;
        while (true) {
            long diff = end - start;
            if (diff == 0) break;
            try {
                requestTracker.acquire();
            } catch (InterruptedException e) {
                setLastMessage(INTERRUPT_MESSAGE);
                Thread.currentThread().interrupt();
            }
            id++;
            long requestStart = diff < 3600000L ? start : end - 3600000L;
            client.getAggTrades(symbol, null, null, requestStart, end, new TradesCallback(id, requestStart, end));
            if (diff < 3600000L) break;
            end -= 3600000L;
        }
        try {
            blocker.acquire(chunks);
        } catch (InterruptedException e) {
            setLastMessage(INTERRUPT_MESSAGE);
            Thread.currentThread().interrupt();
        }
        timer.cancel();
        timer.purge();
        System.out.print("\r(" + Formatter.formatDuration(System.currentTimeMillis() - initTime) + ") (" + Formatter.formatPercent(1.0) + ") Data collected in temp files");

        compileBackTestingData(start, filename);

        checkBacktestingData(filename);

        System.out.println("\n---Collection completed in "
                + Formatter.formatDuration(System.currentTimeMillis() - initTime) + ", result in "
                + new File(filename).getAbsolutePath());
        System.out.println("---Files may only appear after quitting");

        describe(filename);

        startCollection();
    }

    public static void dataToCsv(String filename) {
        System.out.println("Writing .csv file...");
        final String csv = filename.replace(".dat", ".csv").replace("backtesting", "csv");
        new File("csv").mkdir();
        try (PriceReader reader = new PriceReader(filename); PrintWriter writer = new PrintWriter(csv)) {
            writer.write("timestamp,price,is5minClosing\n");
            PriceBean bean = reader.readPrice();
            while (bean != null) {
                writer.write(bean.toCsvString() + "\n");
                bean = reader.readPrice();
            }
            System.out.println("Result of collection written to " + new File(csv).getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static boolean compileBackTestingData(long start, String filename) {
        System.out.println("\n---Writing data from temp files to main file");
        try {
            Files.deleteIfExists(Path.of(filename));
        } catch (IOException e) {
            System.out.println("---Could not automatically delete previous file at " + filename);
            return false;
        }
        if (symbol == null) {
            symbol = filename.split("_")[0];
        }
         try (PriceWriter writer = new PriceWriter(filename)) {
            List<Candlestick> candlesticks = CurrentAPI.get().getCandlestickBars(symbol, CandlestickInterval.HOURLY, null, null, start);
            for (int i = 0; i < candlesticks.size() - 1; i++) {
                Candlestick candlestick = candlesticks.get(i);
                writer.writeBean(new PriceBean(candlestick.getCloseTime(), Double.parseDouble(candlestick.getClose()), 0, 0, 0, 0, 0, 0, true));
            }
            Candlestick lastCandle = candlesticks.get(candlesticks.size() - 1);
            long candleTime = lastCandle.getCloseTime();
            if (lastCandle.getCloseTime() == start) {
                candleTime += 3600000L;
                writer.writeBean(new PriceBean(lastCandle.getCloseTime(), Double.parseDouble(lastCandle.getClose()), 0, 0, 0, 0, 0, 0));
            }
            PriceBean lastBean = null;
            boolean first = true;
            for (int i = chunks; i >= 1; i--) {
                System.out.print("\r(" + Formatter.formatDuration(System.currentTimeMillis() - initTime) + ") (" + Formatter.formatPercent(1 - (double) i / (double) chunks) + ") /temp/" + i + ".dat");
                File tempFile = new File("temp/" + i + ".dat");
                try (PriceReader reader = new PriceReader(tempFile.getPath())) {
                    if (first) {
                        lastBean = reader.readPrice();
                        first = false;
                    }
                    PriceBean bean = reader.readPrice();
                    while (bean != null) {
                        if (bean.getTimestamp() > candleTime) {
                            lastBean.close();
                            while (candleTime <= bean.getTimestamp()) candleTime += 3600000L;
                        }
                        writer.writeBean(lastBean);
                        lastBean = bean;
                        bean = reader.readPrice();
                    }
                } catch (FileNotFoundException ignored) {
                } catch (IOException e) {
                    e.printStackTrace();
                }
                deleteTempFile(tempFile);
            }
            assert lastBean != null;
            writer.writeBean(lastBean);
        } catch (IOException e) {
            System.out.println();
            e.printStackTrace();
            System.out.println("\n---Could not compile backtesting data into main file from temp files!");
            return false;
        }
        deleteTemp();
        System.out.print("\r(" + Formatter.formatDuration(System.currentTimeMillis() - initTime) + ") (" + Formatter.formatPercent(1.0) + ") Temp files processed");
        return true;
    }

    private static void deleteTempFile(File tempFile) {
        try {
            Files.delete(tempFile.toPath());
        } catch (NoSuchFileException ignored) {
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void checkBacktestingData(String filename) {
        if (!Files.exists(Path.of(filename))) {
            System.out.println("\n---File at " + filename + " does not exist!");
            return;
        }
        System.out.println("\n\n---Checking data for consistency");
        boolean firstGap = true;
        boolean firstReg = true;
        try (PriceReader reader = new PriceReader(filename)) {
            PriceBean bean = reader.readPrice();
            long last = Long.MIN_VALUE;
            while (bean != null) {
                if (bean.getTimestamp() < last) {
                    System.out.println("!-----Date regression from " + Formatter.formatDate(last) + " to " + Formatter.formatDate(bean.getTimestamp()) + "------!");
                    if (firstReg) {
                        System.out.println("!--Date regression should never occour in data. File an issue on https://github.com/markusaksli/TradeBot with your terminal history--!");
                        firstReg = false;
                    }
                }
                if (bean.getTimestamp() - last > 1800000L && !bean.isClosing()) {
                    if (firstGap) {
                        System.out.println("-Gaps (checking for 30min+) usually point to exchange maintenance times, check https://www.binance.com/en/trade/pro/" + symbol.replace(ConfigSetup.getFiat(), "_" + ConfigSetup.getFiat()) + " if suspicious");
                        firstGap = false;
                    }
                    System.out.println("Gap from " + Formatter.formatDate(last) + " to " + Formatter.formatDate(bean.getTimestamp()));
                }
                last = bean.getTimestamp();
                bean = reader.readPrice();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.print(firstGap && firstReg ? "---Data is completely consistent" : "");
        return;
    }

    private static void deleteTemp() {
        try {
            FileUtils.deleteDirectory(new File("temp"));
        } catch (IOException e) {
            System.out.println("---Could not automatically delete temp folder!");
        }
    }

    public static void describe(String filename) {
        try (PriceReader reader = new PriceReader(filename)) {
            long count = 0;
            long totalTimeDiff = 0;
            long max = Integer.MIN_VALUE;
            PriceBean bean = reader.readPrice();
            long lastTime = bean.getTimestamp();
            bean = reader.readPrice();
            while (bean.getTimestamp() - lastTime > 290000L) {
                lastTime = bean.getTimestamp();
                bean = reader.readPrice();
            }
            while (bean != null) {
                final long timeDiff = bean.getTimestamp() - lastTime;
                if (timeDiff >= 300000L) {
                    lastTime = bean.getTimestamp();
                    bean = reader.readPrice();
                    continue;
                }
                count++;
                totalTimeDiff += timeDiff;
                if (timeDiff > max) {
                    max = timeDiff;
                }
                lastTime = bean.getTimestamp();
                bean = reader.readPrice();
            }
            System.out.println("---File contains: " + Formatter.formatLarge(count) + " entries (average interval " + Formatter.formatDecimal((double) totalTimeDiff / count) + " ms)");
            System.out.println("-Longest gap in consistent data: " + Formatter.formatDuration(max));
            System.out.println("---Covered time period: " + Formatter.formatDuration(totalTimeDiff));
            System.out.println("---File size: " + Formatter.formatDecimal((double) new File(filename).length() / 1048576.0) + " MB");

            while (true) {
                System.out.println("\nEnter \"back\" to return, \"check\" to verify the data, and \"csv\" to create .csv file with price data");
                String s = sc.nextLine();
                //TODO: Method to get csv with indicators for ML (5min, interval, realtime)
                //https://github.com/markrkalder/crypto-ds/blob/transformer/src/main/java/ml/DataCalculator.java
                if (s.equalsIgnoreCase("back")) {
                    return;
                } else if (s.equalsIgnoreCase("csv")) {
                    dataToCsv(filename);
                } else if (s.equalsIgnoreCase("check")) {
                    checkBacktestingData(filename);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class TradesCallback implements BinanceApiCallback<List<AggTrade>> {
    int id;
    long start;
    long end;

    public TradesCallback(int id, long start, long end) {
        this.id = id;
        this.start = start;
        this.end = end;
    }

    @Override
    public void onFailure(Throwable cause) {
        try {
            Collection.setLastMessage("Request " + id + " failed due to: \"" + cause.getMessage() + "\"");
            if (cause.getMessage().toLowerCase().contains("weight")) {
                Collection.setBrakeSeconds(cause.getMessage().toLowerCase().contains("banned") ? 61 : 1);
                Collection.setBraked(true);
            }
            Collection.getRequestTracker().acquire();
            Collection.getClient().getAggTrades(Collection.getSymbol(), null, null, start, end, new TradesCallback(id, start, end));
            Collection.setLastMessage("Resent request " + id);
        } catch (InterruptedException e) {
            Collection.setLastMessage(Collection.INTERRUPT_MESSAGE);
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void onResponse(List<AggTrade> response) {
        if (!response.isEmpty()) {
            try (PriceWriter writer = new PriceWriter("temp/" + id + ".dat")) {
                double lastPrice = Double.parseDouble(response.get(0).getPrice());
                for (int i = 1; i < response.size(); i++) {
                    AggTrade trade = response.get(i);
                    double newPrice = Double.parseDouble(trade.getPrice());
                    if (lastPrice == newPrice) continue;
                    lastPrice = newPrice;
                    System.out.println(new Date(trade.getTradeTime()) + " - " + newPrice);
                    writer.writeBean(new PriceBean(trade.getTradeTime(), newPrice, 0, 0, 0, 0, 0, 0));
                }
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }
        Collection.getBlocker().release();
        Collection.printProgress();
    }
}
