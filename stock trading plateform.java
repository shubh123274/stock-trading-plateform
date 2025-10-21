import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class StockTradingPlatform extends JFrame {
    private Market market;
    private Portfolio portfolio;

    // GUI components
    private DefaultTableModel marketTableModel;
    private JTable marketTable;
    private DefaultTableModel portfolioTableModel;
    private JTable portfolioTable;
    private DefaultTableModel txnTableModel;
    private JTable txnTable;
    private JLabel cashLabel;
    private JLabel marketValueLabel;
    private DrawingPanel chartPanel;
    private DecimalFormat moneyFmt = new DecimalFormat("#,##0.00");

    // Controls
    private JTextField tickerField;
    private JTextField qtyField;
    private JButton buyButton;
    private JButton sellButton;
    private JButton saveButton;
    private JButton loadButton;

    // Timer for market updates
    private Timer marketTimer;

    public StockTradingPlatform() {
        super("Mini Stock Trading Platform");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 720);
        setLocationRelativeTo(null);

        market = new Market();
        portfolio = new Portfolio(100000.0); // start with ₹100,000 cash (or any currency)

        initGUI();
        startMarketUpdates();
    }

    private void initGUI() {
        setLayout(new BorderLayout(8, 8));

        // --- Left: Market + Buy/Sell ---
        JPanel leftPanel = new JPanel(new BorderLayout(8, 8));
        leftPanel.setPreferredSize(new Dimension(520, 700));

        // Market table
        marketTableModel = new DefaultTableModel(new Object[] { "Ticker", "Name", "Price", "Change %" }, 0) {
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        marketTable = new JTable(marketTableModel);
        JScrollPane marketScroll = new JScrollPane(marketTable);
        marketScroll.setBorder(new TitledBorder("Market"));
        leftPanel.add(marketScroll, BorderLayout.CENTER);

        // Buy/Sell panel
        JPanel tradePanel = new JPanel(new GridBagLayout());
        tradePanel.setBorder(new TitledBorder("Trade"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        tradePanel.add(new JLabel("Ticker:"), gbc);
        gbc.gridx = 1;
        tickerField = new JTextField(8);
        tradePanel.add(tickerField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        tradePanel.add(new JLabel("Quantity:"), gbc);
        gbc.gridx = 1;
        qtyField = new JTextField(8);
        tradePanel.add(qtyField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        buyButton = new JButton("Buy");
        tradePanel.add(buyButton, gbc);
        gbc.gridx = 1;
        sellButton = new JButton("Sell");
        tradePanel.add(sellButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        saveButton = new JButton("Save Portfolio");
        loadButton = new JButton("Load Portfolio");
        JPanel ioPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        ioPanel.add(saveButton);
        ioPanel.add(loadButton);
        tradePanel.add(ioPanel, gbc);

        leftPanel.add(tradePanel, BorderLayout.SOUTH);

        add(leftPanel, BorderLayout.WEST);

        // --- Right: Portfolio, Transactions, Chart ---
        JPanel rightPanel = new JPanel(new BorderLayout(8, 8));

        // Portfolio top
        portfolioTableModel = new DefaultTableModel(
                new Object[] { "Ticker", "Qty", "Avg Price", "Market Price", "Mkt Value" }, 0) {
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        portfolioTable = new JTable(portfolioTableModel);
        JScrollPane portfolioScroll = new JScrollPane(portfolioTable);
        portfolioScroll.setBorder(new TitledBorder("Portfolio"));

        // Summary (cash + market value)
        JPanel summaryPanel = new JPanel(new GridLayout(2, 1, 4, 4));
        cashLabel = new JLabel("Cash: ₹" + moneyFmt.format(portfolio.getCash()));
        marketValueLabel = new JLabel("Portfolio Market Value: ₹0.00");
        summaryPanel.add(cashLabel);
        summaryPanel.add(marketValueLabel);

        JPanel topRight = new JPanel(new BorderLayout(6, 6));
        topRight.add(portfolioScroll, BorderLayout.CENTER);
        topRight.add(summaryPanel, BorderLayout.SOUTH);

        rightPanel.add(topRight, BorderLayout.NORTH);

        // Transaction log
        txnTableModel = new DefaultTableModel(new Object[] { "Time", "Type", "Ticker", "Qty", "Price", "Value" }, 0) {
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        txnTable = new JTable(txnTableModel);
        JScrollPane txnScroll = new JScrollPane(txnTable);
        txnScroll.setBorder(new TitledBorder("Transactions"));
        txnScroll.setPreferredSize(new Dimension(520, 180));

        rightPanel.add(txnScroll, BorderLayout.CENTER);

        // Chart panel
        chartPanel = new DrawingPanel();
        chartPanel.setPreferredSize(new Dimension(520, 220));
        chartPanel.setBorder(new TitledBorder("Portfolio Value (Live)"));
        rightPanel.add(chartPanel, BorderLayout.SOUTH);

        add(rightPanel, BorderLayout.CENTER);

        // Fill initial market table from market
        refreshMarketTable();

        // Action listeners
        buyButton.addActionListener(e -> handleTrade(true));
        sellButton.addActionListener(e -> handleTrade(false));
        saveButton.addActionListener(e -> savePortfolio());
        loadButton.addActionListener(e -> loadPortfolio());

        // Double-click a market row to autofill ticker
        marketTable.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = marketTable.getSelectedRow();
                    if (row >= 0) {
                        String t = (String) marketTableModel.getValueAt(row, 0);
                        tickerField.setText(t);
                    }
                }
            }
        });
    }

    private void handleTrade(boolean isBuy) {
        String ticker = tickerField.getText().trim().toUpperCase();
        if (ticker.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter ticker", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        int qty;
        try {
            qty = Integer.parseInt(qtyField.getText().trim());
            if (qty <= 0)
                throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Enter a valid positive integer quantity", "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        Stock s = market.getStock(ticker);
        if (s == null) {
            JOptionPane.showMessageDialog(this, "Ticker not found in market", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        double price = s.getPrice();
        double value = price * qty;

        if (isBuy) {
            if (portfolio.getCash() < value) {
                JOptionPane.showMessageDialog(this, "Insufficient cash to buy: required ₹" + moneyFmt.format(value),
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            portfolio.buy(ticker, qty, price);
            addTransaction("BUY", ticker, qty, price, value);
        } else {
            if (!portfolio.canSell(ticker, qty)) {
                JOptionPane.showMessageDialog(this, "Not enough holdings to sell", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            portfolio.sell(ticker, qty, price);
            addTransaction("SELL", ticker, qty, price, value);
        }

        updatePortfolioTable();
        updateSummary();
        chartPanel.repaint();
        clearTradeFields();
    }

    private void addTransaction(String type, String ticker, int qty, double price, double value) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        txnTableModel.insertRow(0,
                new Object[] { time, type, ticker, qty, "₹" + moneyFmt.format(price), "₹" + moneyFmt.format(value) });
    }

    private void clearTradeFields() {
        tickerField.setText("");
        qtyField.setText("");
    }

    private void refreshMarketTable() {
        SwingUtilities.invokeLater(() -> {
            marketTableModel.setRowCount(0);
            for (Stock s : market.getAllStocks()) {
                String changePct = String.format("%.2f%%", s.getChangePercent() * 100);
                marketTableModel.addRow(
                        new Object[] { s.getTicker(), s.getName(), "₹" + moneyFmt.format(s.getPrice()), changePct });
            }
        });
    }

    private void updatePortfolioTable() {
        SwingUtilities.invokeLater(() -> {
            portfolioTableModel.setRowCount(0);
            for (Holding h : portfolio.getHoldings()) {
                Stock s = market.getStock(h.getTicker());
                double mPrice = (s == null) ? 0.0 : s.getPrice();
                double marketValue = h.getQty() * mPrice;
                portfolioTableModel.addRow(new Object[] {
                        h.getTicker(),
                        h.getQty(),
                        "₹" + moneyFmt.format(h.getAvgPrice()),
                        "₹" + moneyFmt.format(mPrice),
                        "₹" + moneyFmt.format(marketValue)
                });
            }
        });
    }

    private void updateSummary() {
        SwingUtilities.invokeLater(() -> {
            cashLabel.setText("Cash: ₹" + moneyFmt.format(portfolio.getCash()));
            marketValueLabel.setText("Portfolio Market Value: ₹" + moneyFmt.format(portfolio.getMarketValue(market)));
        });
    }

    private void startMarketUpdates() {
        marketTimer = new Timer(true);
        marketTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                market.step(); // update prices
                portfolio.recordHistory(market); // record portfolio value for chart
                refreshMarketTable();
                updatePortfolioTable();
                updateSummary();
                chartPanel.repaint();
            }
        }, 0, 1000); // every 1 second
    }

    private void stopMarketUpdates() {
        if (marketTimer != null)
            marketTimer.cancel();
    }

    private void savePortfolio() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save portfolio CSV");
        int ret = chooser.showSaveDialog(this);
        if (ret != JFileChooser.APPROVE_OPTION)
            return;
        File f = chooser.getSelectedFile();
        try (PrintWriter pw = new PrintWriter(new FileWriter(f))) {
            pw.println("cash," + portfolio.getCash());
            for (Holding h : portfolio.getHoldings()) {
                pw.println(String.join(",",
                        Arrays.asList(h.getTicker(), String.valueOf(h.getQty()), String.valueOf(h.getAvgPrice()))));
            }
            JOptionPane.showMessageDialog(this, "Portfolio saved to " + f.getAbsolutePath(), "Saved",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to save: " + ex.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadPortfolio() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Load portfolio CSV");
        int ret = chooser.showOpenDialog(this);
        if (ret != JFileChooser.APPROVE_OPTION)
            return;
        File f = chooser.getSelectedFile();
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            Portfolio loaded = new Portfolio(0.0);
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty())
                    continue;
                String[] parts = line.split(",");
                if (parts[0].equalsIgnoreCase("cash")) {
                    loaded.setCash(Double.parseDouble(parts[1]));
                } else {
                    // ticker,qty,avgPrice
                    String t = parts[0].trim().toUpperCase();
                    int q = Integer.parseInt(parts[1]);
                    double ap = Double.parseDouble(parts[2]);
                    loaded.setHolding(new Holding(t, q, ap));
                }
            }
            this.portfolio = loaded;
            // attach portfolio to UI and clear txn log & history
            txnTableModel.setRowCount(0);
            portfolio.clearHistory();
            JOptionPane.showMessageDialog(this, "Portfolio loaded from " + f.getAbsolutePath(), "Loaded",
                    JOptionPane.INFORMATION_MESSAGE);
            updatePortfolioTable();
            updateSummary();
            chartPanel.repaint();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to load: " + ex.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---------------------------
    // Model classes (OOP)
    // ---------------------------
    static class Stock {
        private final String ticker;
        private final String name;
        private double price;
        private double prevPrice;

        public Stock(String ticker, String name, double price) {
            this.ticker = ticker;
            this.name = name;
            this.price = price;
            this.prevPrice = price;
        }

        public String getTicker() {
            return ticker;
        }

        public String getName() {
            return name;
        }

        public double getPrice() {
            return price;
        }

        public void setPrice(double price) {
            this.prevPrice = this.price;
            this.price = price;
        }

        public double getChangePercent() {
            if (prevPrice == 0)
                return 0;
            return (price - prevPrice) / prevPrice;
        }
    }

    static class Market {
        private final Map<String, Stock> stocks = new LinkedHashMap<>();
        private final Random rnd = new Random();

        public Market() {
            // initial selection of stocks
            addStock(new Stock("RELI", "Reliance Industries", 2500.0));
            addStock(new Stock("TCS", "Tata Consultancy", 3600.0));
            addStock(new Stock("INFY", "Infosys", 1450.0));
            addStock(new Stock("HDFC", "HDFC Bank", 1500.0));
            addStock(new Stock("ICIC", "ICICI Bank", 920.0));
            addStock(new Stock("HIND", "Hindustan Unilever", 2500.0));
        }

        public void addStock(Stock s) {
            stocks.put(s.getTicker(), s);
        }

        public Stock getStock(String ticker) {
            return stocks.get(ticker);
        }

        public Collection<Stock> getAllStocks() {
            return stocks.values();
        }

        // Random walk step (simple)
        public void step() {
            for (Stock s : stocks.values()) {
                double p = s.getPrice();
                // simulate percent move between -1.5% and +1.5%
                double pct = (rnd.nextDouble() * 3.0) - 1.5;
                double newP = p * (1 + pct / 100.0);
                if (newP <= 0.01)
                    newP = p; // avoid zero/negative
                s.setPrice(round2(newP));
            }
        }

        private static double round2(double v) {
            return Math.round(v * 100.0) / 100.0;
        }
    }

    static class Holding {
        private final String ticker;
        private int qty;
        private double avgPrice;

        public Holding(String ticker, int qty, double avgPrice) {
            this.ticker = ticker;
            this.qty = qty;
            this.avgPrice = avgPrice;
        }

        public String getTicker() {
            return ticker;
        }

        public int getQty() {
            return qty;
        }

        public double getAvgPrice() {
            return avgPrice;
        }

        public void addQty(int q, double price) {
            double totalCost = this.avgPrice * this.qty + price * q;
            this.qty += q;
            this.avgPrice = totalCost / this.qty;
        }

        public void reduceQty(int q) {
            if (q > this.qty)
                throw new IllegalArgumentException("Reduce more than holding");
            this.qty -= q;
        }
    }

    static class Transaction {
        public final String time;
        public final String type; // BUY/SELL
        public final String ticker;
        public final int qty;
        public final double price;
        public final double value;

        public Transaction(String time, String type, String ticker, int qty, double price) {
            this.time = time;
            this.type = type;
            this.ticker = ticker;
            this.qty = qty;
            this.price = price;
            this.value = price * qty;
        }
    }

    static class Portfolio {
        private double cash;
        private final Map<String, Holding> holdings = new LinkedHashMap<>();
        private final List<Transaction> transactions = new ArrayList<>();
        private final List<Double> history = new ArrayList<>(); // portfolio market value over time

        public Portfolio(double initialCash) {
            this.cash = initialCash;
            recordHistory(null); // initial
        }

        public synchronized double getCash() {
            return cash;
        }

        public synchronized void setCash(double c) {
            this.cash = c;
        }

        public synchronized void buy(String ticker, int qty, double price) {
            double cost = qty * price;
            if (cost > cash)
                throw new IllegalArgumentException("Not enough cash");
            Holding h = holdings.get(ticker);
            if (h == null) {
                holdings.put(ticker, new Holding(ticker, qty, price));
            } else {
                h.addQty(qty, price);
            }
            cash -= cost;
            transactions.add(new Transaction(now(), "BUY", ticker, qty, price));
        }

        public synchronized void sell(String ticker, int qty, double price) {
            Holding h = holdings.get(ticker);
            if (h == null || h.getQty() < qty)
                throw new IllegalArgumentException("Not enough holdings");
            double proceeds = qty * price;
            h.reduceQty(qty);
            if (h.getQty() == 0)
                holdings.remove(ticker);
            cash += proceeds;
            transactions.add(new Transaction(now(), "SELL", ticker, qty, price));
        }

        public synchronized boolean canSell(String ticker, int qty) {
            Holding h = holdings.get(ticker);
            return h != null && h.getQty() >= qty;
        }

        public synchronized List<Holding> getHoldings() {
            return new ArrayList<>(holdings.values());
        }

        public synchronized double getMarketValue(Market market) {
            double mv = 0.0;
            for (Holding h : holdings.values()) {
                Stock s = market.getStock(h.getTicker());
                double price = (s == null) ? 0.0 : s.getPrice();
                mv += h.getQty() * price;
            }
            return mv;
        }

        public synchronized void recordHistory(Market market) {
            double total = cash + (market == null ? 0.0 : getMarketValue(market));
            history.add(total);
            // keep history length manageable
            if (history.size() > 500)
                history.remove(0);
        }

        public synchronized List<Double> getHistory() {
            return new ArrayList<>(history);
        }

        public synchronized void clearHistory() {
            history.clear();
        }

        public synchronized List<Transaction> getTransactions() {
            return new ArrayList<>(transactions);
        }

        public String now() {
            return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }

        // for save/load
        public synchronized void setHolding(Holding h) {
            holdings.put(h.getTicker(), h);
        }
    }

    // Simple JPanel to draw portfolio value history
    class DrawingPanel extends JPanel {
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            List<Double> hist = portfolio.getHistory();
            if (hist.isEmpty()) {
                g.drawString("No history recorded yet.", 10, 20);
                return;
            }
            int w = getWidth(), h = getHeight();
            double max = Collections.max(hist);
            double min = Collections.min(hist);
            if (Math.abs(max - min) < 1e-6)
                max = min + 1.0;

            // draw axes and grid
            g.setColor(Color.LIGHT_GRAY);
            for (int i = 0; i < 5; i++) {
                int y = (int) (i * (h - 30) / 4.0) + 10;
                g.drawLine(40, y, w - 10, y);
            }

            // Y labels
            g.setColor(Color.BLACK);
            for (int i = 0; i < 5; i++) {
                double val = max - i * (max - min) / 4.0;
                String s = "₹" + moneyFmt.format(val);
                int y = (int) (i * (h - 30) / 4.0) + 14;
                g.drawString(s, 4, y);
            }

            // draw polyline
            int n = hist.size();
            int left = 40, right = w - 10;
            int plotW = right - left;
            int plotH = h - 30;
            int baseY = 10;
            g.setColor(Color.BLUE);
            int prevX = left, prevY = baseY + (int) ((max - hist.get(0)) / (max - min) * plotH);
            for (int i = 1; i < n; i++) {
                int x = left + (int) ((i / (double) (n - 1)) * plotW);
                int y = baseY + (int) ((max - hist.get(i)) / (max - min) * plotH);
                g.drawLine(prevX, prevY, x, y);
                prevX = x;
                prevY = y;
            }

            // draw last value label
            double last = hist.get(hist.size() - 1);
            g.setColor(Color.BLACK);
            String lastLabel = "Latest: ₹" + moneyFmt.format(last);
            g.drawString(lastLabel, w - 150, 18);
        }
    }

    // Utility
    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    // Shutdown hook
    public void close() {
        stopMarketUpdates();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            StockTradingPlatform app = new StockTradingPlatform();
            app.setVisible(true);
            app.addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) {
                    app.close();
                }
            });
        });
    }
}