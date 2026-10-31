package com.mycompany.mavenproject7;

import javax.swing.*;
import javax.imageio.ImageIO;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.TextAttribute;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import static javax.swing.WindowConstants.EXIT_ON_CLOSE;

/**
 * StyleHub - Clothing Sales and Ordering System
 *
 * Two branches: Kinawe (Main Branch) and Opol Branch. Every product has its own
 * stock per branch, customers shop and order from one branch at a time, and the
 * admin can manage stock, transfer items between branches and track sales per branch.
 *
 * All data is stored in the MySQL database "stylehubsystem" (see Database.java),
 * so accounts, products, stock, carts and orders are kept between runs.
 */
public class StyleHubSystem extends JFrame {

    // =====================================================================
    //                               THEME
    // =====================================================================
    static final Color IVORY  = new Color(250, 248, 245);
    static final Color INK    = new Color(28, 28, 28);
    static final Color GOLD   = new Color(176, 141, 87);
    static final Color MUTED  = new Color(122, 118, 112);
    static final Color LINE   = new Color(229, 224, 216);
    static final Color SAND   = new Color(244, 240, 233);
    static final Color DANGER = new Color(171, 59, 48);
    static final Color OK     = new Color(58, 122, 86);
    static final Color NAV_IDLE = new Color(198, 193, 184);

    static final String BRAND = "StyleHub";
    static final String SYSTEM = "Clothing Sales and Ordering System";
    static final String[] BRANCHES = {"Kinawe (Main Branch)", "Opol Branch"};
    static final String[] BRANCH_SHORT = {"Kinawe", "Opol"};

    static final String[] CATEGORIES = {"Tops", "Bottoms", "Outerwear", "Dresses", "Accessories"};
    static final String[] STATUSES = {"Pending", "Processing", "Shipped", "Delivered", "Completed", "Cancelled"};
    static final String[] PAYMENTS = {"Cash on Delivery", "GCash", "Credit/Debit Card"};
    static final String[] METHODS = {"Pick-up", "Delivery"};
    static final int LOW_STOCK = 5;

    // Checkout configuration. Replace these with your actual StyleHub details.
    static final String GCASH_ACCOUNT_NAME = "StyleHub Clothing";
    static final String GCASH_ACCOUNT_NUMBER = "09XX XXX XXXX";
    static final String[] PICKUP_ADDRESSES = {
        "StyleHub Clothing - Kinawe Main Branch, Kinawe, Manolo Fortich, Bukidnon",
        "StyleHub Clothing - Opol Branch, Opol, Misamis Oriental"
    };

    static Font serif(int style, int size) { return new Font("Georgia", style, size); }
    static Font sans(int style, int size)  { return new Font("SansSerif", style, size); }

    static Font tracked(Font f, float tracking) {
        Map<TextAttribute, Object> m = new HashMap<>();
        m.put(TextAttribute.TRACKING, tracking);
        return f.deriveFont(m);
    }

    // =====================================================================
    //                               DATA
    // =====================================================================
    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASS = "admin123";

    // username (lowercase) -> {password, email, fullName}
    private final Map<String, String[]> customers = new HashMap<>();
    private final ArrayList<Product> catalog = new ArrayList<>();
    private final Map<String, ArrayList<CartItem>> carts = new HashMap<>();
    private final Map<String, Integer> cartBranch = new HashMap<>();
    private final ArrayList<Order> orders = new ArrayList<>();
    private final Set<Integer> wishlist = new HashSet<>();
    private final ArrayList<NavButton> navButtons = new ArrayList<>();
    private String currentUser;
    private String adminDateFilter = "This Month";
    private JPanel content;
    private JLabel whoLabel;

    static class Product {
        final int id;
        String name;
        String category;
        double price;
        String imagePath;
        String colors;
        String colorImages; // encoded color-specific image paths
        String availability;
        final int[] stock = new int[2]; // [0] = Kinawe, [1] = Opol
        Product(int id, String name, String category, double price, int kinawe, int opol) {
            this(id, name, category, price, kinawe, opol, null, "Black", "Available");
        }
        Product(int id, String name, String category, double price, int kinawe, int opol, String imagePath) {
            this(id, name, category, price, kinawe, opol, imagePath, "Black", "Available", "");
        }
        Product(int id, String name, String category, double price, int kinawe, int opol,
                String imagePath, String colors, String availability) {
            this(id, name, category, price, kinawe, opol, imagePath, colors, availability, "");
        }
        Product(int id, String name, String category, double price, int kinawe, int opol,
                String imagePath, String colors, String availability, String colorImages) {
            this.id = id; this.name = name; this.category = category; this.price = price;
            this.imagePath = imagePath; this.colors = (colors == null || colors.isBlank()) ? "Black" : colors;
            this.colorImages = colorImages == null ? "" : colorImages;
            this.availability = (availability == null || availability.isBlank()) ? "Available" : availability;
            this.stock[0] = kinawe; this.stock[1] = opol;
        }
    }

    static class CartItem {
        final Product product;
        String size;
        String color;
        int qty;
        CartItem(Product product, String size, int qty) {
            this(product, size, product == null ? "Black" : firstColor(product), qty);
        }
        CartItem(Product product, String size, String color, int qty) {
            this.product = product; this.size = size; this.color = color; this.qty = qty;
        }
        double subtotal() { return product.price * qty; }
    }

    static class Order {
        final int id;
        final String owner;
        final String date;
        final int branch;
        final ArrayList<CartItem> items;
        String method;
        String address;
        String payment;
        String status = "Pending";
        Order(int id, String owner, String date, int branch, ArrayList<CartItem> items,
              String method, String address, String payment) {
            this.id = id; this.owner = owner; this.date = date; this.branch = branch;
            this.items = items; this.method = method; this.address = address; this.payment = payment;
        }
        double total() { double t = 0; for (CartItem i : items) t += i.subtotal(); return t; }
        int itemCount() { int n = 0; for (CartItem i : items) n += i.qty; return n; }
        String where() {
            return method.equals("Delivery") ? address : pickupAddress(branch);
        }
        boolean live() {
            return status.equals("Pending") || status.equals("Processing") || status.equals("Shipped");
        }
    }

   
    private void loadFromDatabase() {
        customers.putAll(Database.loadCustomers());

        for (Object[] row : Database.loadProducts()) {
            catalog.add(new Product((Integer) row[0], (String) row[1], (String) row[2],
                    (Double) row[3], (Integer) row[4], (Integer) row[5],
                    row.length > 6 ? (String) row[6] : null,
                    row.length > 7 ? (String) row[7] : "Black",
                    row.length > 8 ? (String) row[8] : "Available",
                    row.length > 9 ? (String) row[9] : ""));
        }

        Map<String, ArrayList<Object[]>> savedCarts = Database.loadCarts();
        for (Map.Entry<String, ArrayList<Object[]>> en : savedCarts.entrySet()) {
            ArrayList<CartItem> cart = new ArrayList<>();
            int branch = 0;
            for (Object[] row : en.getValue()) {
                Product p = findProduct((Integer) row[0]);
                if (p == null) continue;
                branch = (Integer) row[1];
                cart.add(new CartItem(p, (String) row[2], row.length > 4 ? (String) row[4] : firstColor(p), (Integer) row[3]));
            }
            if (!cart.isEmpty()) {
                carts.put(en.getKey(), cart);
                cartBranch.put(en.getKey(), branch);
            }
        }

        for (Object[] row : Database.loadOrders()) {
            ArrayList<CartItem> items = new ArrayList<>();
            @SuppressWarnings("unchecked")
            ArrayList<Object[]> rawItems = (ArrayList<Object[]>) row[8];
            for (Object[] it : rawItems) {
                Product p = findProduct((Integer) it[0]);
                if (p == null) continue;
                items.add(new CartItem(p, (String) it[1], it.length > 4 ? (String) it[4] : firstColor(p), (Integer) it[2]));
            }
            Order o = new Order((Integer) row[0], (String) row[1], (String) row[3],
                    (Integer) row[2], items, (String) row[4], (String) row[5], (String) row[6]);
            o.status = (String) row[7];
            orders.add(o);
        }
        wishlist.clear();
        wishlist.addAll(Database.loadWishlist(currentUser == null ? null : currentUser));
    }
    
    

    private static String firstColor(Product p) {
        if (p == null || p.colors == null || p.colors.isBlank()) return "Black";
        String[] parts = p.colors.split(",");
        return parts.length == 0 ? "Black" : parts[0].trim();
    }

    private static String[] colorOptions(Product p) {
        if (p == null || p.colors == null || p.colors.isBlank()) return new String[]{"Black"};
        String[] raw = p.colors.split(",");
        ArrayList<String> out = new ArrayList<>();
        for (String x : raw) if (!x.trim().isEmpty() && !out.contains(x.trim())) out.add(x.trim());
        return out.isEmpty() ? new String[]{"Black"} : out.toArray(new String[0]);
    }

    private static String encodeColorImages(Map<String, String> images) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> e : images.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue().isBlank()) continue;
            if (out.length() > 0) out.append(";");
            out.append(java.util.Base64.getEncoder().encodeToString(e.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
               .append(":")
               .append(java.util.Base64.getEncoder().encodeToString(e.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        return out.toString();
    }
    private static Map<String, String> decodeColorImages(String encoded) {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        if (encoded == null || encoded.isBlank()) return result;
        for (String entry : encoded.split(";")) {
            int sep = entry.indexOf(':'); if (sep < 1) continue;
            try {
                String color = new String(java.util.Base64.getDecoder().decode(entry.substring(0, sep)), java.nio.charset.StandardCharsets.UTF_8);
                String path = new String(java.util.Base64.getDecoder().decode(entry.substring(sep + 1)), java.nio.charset.StandardCharsets.UTF_8);
                if (!color.isBlank() && !path.isBlank()) result.put(color, path);
            } catch (IllegalArgumentException ignored) { }
        }
        return result;
    }

    private Product findProduct(int id) {
        for (Product p : catalog) if (p.id == id) return p;
        return null;
    }

    // =====================================================================
    //                    CUSTOM COMPONENTS (look & feel)
    // =====================================================================

    static class RefreshIconButton extends JButton {
        private boolean hover;

        RefreshIconButton() {
            setPreferredSize(new Dimension(40, 40));
            setMinimumSize(new Dimension(40, 40));
            setMaximumSize(new Dimension(40, 40));
            setToolTipText("Refresh data");
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g.setColor(hover ? SAND : Color.WHITE);
            g.fillRoundRect(0, 0, w - 1, h - 1, 12, 12);
            g.setColor(new Color(205, 198, 188));
            g.drawRoundRect(0, 0, w - 1, h - 1, 12, 12);

            g.setColor(hover ? INK : MUTED);
            g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int cx = w / 2, cy = h / 2;
            int r = 9;
            g.drawArc(cx - r, cy - r, r * 2, r * 2, 45, 285);
            Polygon arrow = new Polygon();
            arrow.addPoint(cx + 9, cy - 7);
            arrow.addPoint(cx + 9, cy - 1);
            arrow.addPoint(cx + 3, cy - 3);
            g.fillPolygon(arrow);
            g.dispose();
        }
    }

    
    static class RoundButton extends JButton {
        static final int PRIMARY = 0, GOLDV = 1, OUTLINE = 2, DANGERV = 3, LINK = 4;
        private int variant;
        private boolean hover;

        RoundButton(String text, int variant) {
            super(text);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setFont(sans(Font.BOLD, 12));
            setBorder(new EmptyBorder(11, 22, 11, 22));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; hoverChanged(); }
                @Override public void mouseExited(MouseEvent e)  { hover = false; hoverChanged(); }
            });
            setVariant(variant);
        }

        private void hoverChanged() {
            if (variant == LINK) setForeground(hover ? INK : GOLD);
            repaint();
        }

        void setVariant(int v) {
            variant = v;
            setForeground(v == OUTLINE ? INK : v == LINK ? GOLD : Color.WHITE);
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            if (variant != LINK) {
                Color bg;
                switch (variant) {
                    case GOLDV:    bg = hover ? new Color(158, 124, 72) : GOLD; break;
                    case OUTLINE:  bg = hover ? SAND : Color.WHITE; break;
                    case DANGERV:  bg = hover ? new Color(145, 46, 36) : DANGER; break;
                    default:       bg = hover ? new Color(62, 62, 62) : INK;
                }
                if (getModel().isPressed()) bg = bg.darker();
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, w, h, 12, 12);
                if (variant == OUTLINE) {
                    g2.setColor(new Color(205, 198, 188));
                    g2.drawRoundRect(0, 0, w - 1, h - 1, 12, 12);
                }
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

       static class NavButton extends JButton {
        private boolean active, hover;

        NavButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setFont(sans(Font.PLAIN, 14));
            setHorizontalAlignment(SwingConstants.LEFT);
            setBorder(new EmptyBorder(13, 28, 13, 16));
            setForeground(NAV_IDLE);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
            });
        }

        void setActive(boolean a) {
            active = a;
            setForeground(a ? Color.WHITE : NAV_IDLE);
            repaint();
        }

        boolean isActive() {
            return active;
        }

        @Override protected void paintComponent(Graphics g) {
            if (active || hover) {
                g.setColor(new Color(255, 255, 255, active ? 26 : 12));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
            if (active) {
                g.setColor(GOLD);
                g.fillRect(0, 0, 3, getHeight());
            }
            super.paintComponent(g);
        }
    }

    /** Thin rounded border used by text fields. */
    static class RoundBorder extends AbstractBorder {
        private final Color color;
        private final int thick, arc;
        private final Insets pad;

        RoundBorder(Color color, int thick, int arc, Insets pad) {
            this.color = color; this.thick = thick; this.arc = arc; this.pad = pad;
        }

        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(thick));
            float o = thick / 2f;
            g2.draw(new RoundRectangle2D.Float(x + o, y + o, w - thick, h - thick, arc, arc));
            g2.dispose();
        }

        @Override public Insets getBorderInsets(Component c) {
            return new Insets(pad.top + thick, pad.left + thick, pad.bottom + thick, pad.right + thick);
        }
    }

    /** Pill-style selector (one choice at a time). */
    static class Segmented extends JPanel {
        private final RoundButton[] btns;
        private int sel;
        private IntConsumer onChange;

        Segmented(String[] labels, int initial) {
            super(new GridLayout(1, labels.length, 8, 0));
            setOpaque(false);
            sel = initial;
            btns = new RoundButton[labels.length];
            for (int i = 0; i < labels.length; i++) {
                final int idx = i;
                btns[i] = new RoundButton(labels[i], i == sel ? RoundButton.PRIMARY : RoundButton.OUTLINE);
                btns[i].addActionListener(e -> select(idx, true));
                add(btns[i]);
            }
        }

        void setOnChange(IntConsumer c) { onChange = c; }
        int getSelected() { return sel; }

        void select(int i, boolean fire) {
            sel = i;
            for (int k = 0; k < btns.length; k++) {
                btns[k].setVariant(k == sel ? RoundButton.PRIMARY : RoundButton.OUTLINE);
            }
            if (fire && onChange != null) onChange.accept(i);
        }
    }

    /** Slim progress bar (gold fill on a light track). */
    static class ShareBar extends JPanel {
        private final double fraction;
        ShareBar(double fraction) {
            this.fraction = Math.max(0, Math.min(1, fraction));
            setOpaque(false);
            setPreferredSize(new Dimension(100, 8));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g2.setColor(LINE);
            g2.fillRoundRect(0, 0, w, h, h, h);
            g2.setClip(new RoundRectangle2D.Float(0, 0, w, h, h, h));
            g2.setColor(GOLD);
            g2.fillRect(0, 0, (int) (w * fraction), h);
            g2.dispose();
        }
    }

    // =====================================================================
    //                       SMALL UI HELPER FUNCTIONS
    // =====================================================================
    static JLabel lbl(String text, Font f, Color c) {
        JLabel l = new JLabel(text);
        l.setFont(f);
        l.setForeground(c);
        return l;
    }

    static JLabel capLabel(String text) {
        return lbl(text.toUpperCase(), tracked(sans(Font.BOLD, 10), 0.12f), MUTED);
    }

    static <T extends JComponent> T L(T c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    /** Wraps a component so it hugs the left edge inside a centered BoxLayout card. */
    static JPanel left(JComponent c) {
        JPanel r = new JPanel(new BorderLayout());
        r.setOpaque(false);
        r.add(c, BorderLayout.WEST);
        r.setAlignmentX(Component.CENTER_ALIGNMENT);
        r.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height + 2));
        return r;
    }

    static Component vs(int h) { return Box.createVerticalStrut(h); }

    static JPanel rule(Color c, int w, int h) {
        JPanel p = new JPanel();
        p.setBackground(c);
        p.setPreferredSize(new Dimension(w, h));
        p.setMaximumSize(new Dimension(w, h));
        p.setMinimumSize(new Dimension(w, h));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    static void styleField(JTextField f) {
        Border normal = new RoundBorder(new Color(205, 198, 188), 1, 10, new Insets(0, 14, 0, 14));
        Border focus = new RoundBorder(GOLD, 2, 10, new Insets(0, 13, 0, 13));
        f.setFont(sans(Font.PLAIN, 14));
        f.setForeground(INK);
        f.setBackground(Color.WHITE);
        f.setCaretColor(INK);
        f.setBorder(normal);
        f.setAlignmentX(Component.CENTER_ALIGNMENT);
        f.setPreferredSize(new Dimension(330, 42));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        f.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { f.setBorder(focus); }
            @Override public void focusLost(FocusEvent e)   { f.setBorder(normal); }
        });
    }

    /** Simple padded text field for dialogs and filters. */
    static JTextField dlgField(String text, int cols) {
        JTextField f = new JTextField(text, cols);
        f.setFont(sans(Font.PLAIN, 13));
        f.setBorder(new CompoundBorder(new LineBorder(new Color(205, 198, 188)),
                new EmptyBorder(6, 10, 6, 10)));
        return f;
    }

    // Centralized input validation used by the major forms.
    static boolean validName(String s) { return s != null && s.length() >= 1 && s.length() <= 50 && s.matches("[A-Za-z][A-Za-z .'-]*"); }
    static boolean validUsername(String s) { return s != null && s.length() >= 3 && s.length() <= 50 && s.matches("[A-Za-z0-9._-]+"); }
    static boolean validEmail(String s) { return s != null && s.length() <= 100 && s.matches("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$"); }
    static boolean validPassword(String s) { return s != null && s.length() >= 6 && s.length() <= 128; }
    static String cleanText(String s, int max) {
        if (s == null) return "";
        String x = s.trim().replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "");
        return x.length() > max ? x.substring(0, max) : x;
    }

    static void styleTable(JTable t) {
        t.setRowHeight(40);
        t.setFont(sans(Font.PLAIN, 13));
        t.setForeground(INK);
        t.setBackground(Color.WHITE);
        t.setShowVerticalLines(false);
        t.setShowHorizontalLines(true);
        t.setGridColor(LINE);
        t.setIntercellSpacing(new Dimension(0, 1));
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setFillsViewportHeight(true);
        t.setSelectionBackground(new Color(240, 230, 213));
        t.setSelectionForeground(INK);

        JTableHeader h = t.getTableHeader();
        h.setReorderingAllowed(false);
        h.setPreferredSize(new Dimension(100, 38));
        h.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable tb, Object v,
                    boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(tb,
                        v == null ? "" : v.toString().toUpperCase(), false, false, r, c);
                setBackground(SAND);
                setForeground(MUTED);
                setFont(tracked(sans(Font.BOLD, 10), 0.1f));
                setBorder(new CompoundBorder(new MatteBorder(0, 0, 1, 0, LINE),
                        new EmptyBorder(0, 14, 0, 14)));
                return this;
            }
        });

        t.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable tb, Object v,
                    boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(tb, v, sel, false, r, c);
                setBorder(new EmptyBorder(0, 14, 0, 14));
                setFont(sans(Font.PLAIN, 13));
                if (!sel) setForeground(INK);
                String col = tb.getColumnName(c);
                if (col.equals("Status") && v != null) {
                    setForeground(statusColor(v.toString()));
                    setFont(sans(Font.BOLD, 12));
                }
                boolean stockCol = col.equals("In Stock") || col.equals("Other Branch")
                        || col.equals("Kinawe") || col.equals("Opol") || col.equals("Left");
                if (stockCol && v instanceof Integer && (Integer) v <= LOW_STOCK) {
                    setForeground(DANGER);
                    setFont(sans(Font.BOLD, 13));
                }
                return this;
            }
        });
    }

    static Color statusColor(String s) {
        switch (s) {
            case "Pending":    return new Color(176, 125, 40);
            case "Processing": return new Color(60, 100, 160);
            case "Shipped":    return new Color(110, 80, 160);
            case "Delivered":  return OK;
            case "Cancelled":  return DANGER;
            default:           return INK;
        }
    }

    static JScrollPane scroll(JComponent c) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBorder(new LineBorder(LINE));
        sp.getViewport().setBackground(Color.WHITE);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    static DefaultTableModel newModel(String... cols) {
        return new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
    }

    static JTable newTable(DefaultTableModel m) {
        JTable t = new JTable(m);
        styleTable(t);
        return t;
    }


    private void styleReportTable(JTable t, int[] widths) {
        t.setFont(sans(Font.PLAIN, 14));
        t.setRowHeight(46);
        t.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        t.setIntercellSpacing(new Dimension(0, 1));
        t.setShowHorizontalLines(true);
        t.setShowVerticalLines(false);
        t.setFillsViewportHeight(true);
        t.getTableHeader().setFont(sans(Font.BOLD, 12));
        t.getTableHeader().setPreferredSize(new Dimension(0, 38));
        t.getTableHeader().setBackground(SAND);
        t.getTableHeader().setForeground(INK);
        for (int i = 0; i < widths.length && i < t.getColumnCount(); i++) {
            TableColumn c = t.getColumnModel().getColumn(i);
            c.setPreferredWidth(widths[i]);
            c.setMinWidth(Math.min(widths[i], 80));
        }
    }

    /** Makes the final report column a real clickable View Details button. */
    private void installViewDetailsButton(JTable table, Consumer<Integer> action) {
        final int col = table.getColumnCount() - 1;
        table.getColumnModel().getColumn(col).setCellRenderer(new ButtonRenderer());
        table.getColumnModel().getColumn(col).setCellEditor(new ButtonEditor(action));
        table.getColumnModel().getColumn(col).setPreferredWidth(135);
        table.getColumnModel().getColumn(col).setMinWidth(135);
    }

    static class ButtonRenderer extends JButton implements TableCellRenderer {
        ButtonRenderer() {
            setOpaque(true);
            setFocusPainted(false);
            setBorder(new LineBorder(GOLD, 1, true));
            setBackground(new Color(250, 246, 239));
            setForeground(INK);
            setFont(sans(Font.BOLD, 12));
        }
        @Override public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focused, int row, int column) {
            setText(value == null ? "VIEW DETAILS" : String.valueOf(value));
            if (selected) { setBackground(new Color(239, 229, 211)); }
            else { setBackground(new Color(250, 246, 239)); }
            return this;
        }
    }

    class ButtonEditor extends AbstractCellEditor implements TableCellEditor {
        private final JButton button = new JButton("VIEW DETAILS");
        private final Consumer<Integer> action;
        private int row = -1;
        ButtonEditor(Consumer<Integer> action) {
            this.action = action;
            button.setFocusPainted(false);
            button.setBorder(new LineBorder(GOLD, 1, true));
            button.setBackground(new Color(250, 246, 239));
            button.setForeground(INK);
            button.setFont(sans(Font.BOLD, 12));
            button.addActionListener(e -> {
                int r = row;
                fireEditingStopped();
                if (r >= 0) action.accept(r);
            });
        }
        @Override public Component getTableCellEditorComponent(JTable table, Object value,
                boolean selected, int row, int column) {
            this.row = row;
            button.setText(value == null ? "VIEW DETAILS" : String.valueOf(value));
            return button;
        }
        @Override public Object getCellEditorValue() { return "VIEW DETAILS"; }
    }

    static JPanel flow(JComponent... cs) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        p.setOpaque(false);
        for (JComponent c : cs) p.add(c);
        return p;
    }

    static JPanel actionBar(JComponent buttons, JLabel msg) {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setOpaque(false);
        p.add(buttons, BorderLayout.NORTH);
        p.add(msg, BorderLayout.CENTER);
        return p;
    }

    static void say(JLabel l, String text, boolean ok) {
        l.setFont(sans(Font.PLAIN, 12));
        l.setForeground(ok ? OK : DANGER);
        l.setText(text);
    }

    static JPanel whiteCard(int pad) {
        JPanel p = new JPanel();
        p.setBackground(Color.WHITE);
        p.setBorder(new CompoundBorder(new LineBorder(LINE), new EmptyBorder(pad, pad, pad, pad)));
        return p;
    }

    static String money(double v) { return String.format("\u20B1%,.2f", v); }

    static String today() {
        return java.time.LocalDate.now().format(
                java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy"));
    }

    static void applyDefaults() {
        UIManager.put("Panel.background", IVORY);
        UIManager.put("OptionPane.background", IVORY);
        UIManager.put("OptionPane.messageFont", sans(Font.PLAIN, 13));
        UIManager.put("OptionPane.buttonFont", sans(Font.BOLD, 12));
        UIManager.put("OptionPane.messageForeground", INK);
        UIManager.put("Label.font", sans(Font.PLAIN, 13));
        UIManager.put("Label.foreground", INK);
        UIManager.put("ComboBox.font", sans(Font.PLAIN, 13));
        UIManager.put("ComboBox.background", Color.WHITE);
        UIManager.put("Spinner.font", sans(Font.PLAIN, 13));
        UIManager.put("TextField.font", sans(Font.PLAIN, 13));
        UIManager.put("PasswordField.font", sans(Font.PLAIN, 13));
        UIManager.put("Button.font", sans(Font.BOLD, 12));
    }

    // =====================================================================
    //                        CONSTRUCTOR / MAIN
    // =====================================================================
    public StyleHubSystem() {
        setTitle(BRAND + " - " + SYSTEM);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1180, 760);
        setMinimumSize(new Dimension(980, 660));
        setLocationRelativeTo(null);
        getContentPane().setBackground(IVORY);
        if (Database.testConnection()) {
            loadFromDatabase();
        }
        showLogin();
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        applyDefaults();
        SwingUtilities.invokeLater(() -> new StyleHubSystem().setVisible(true));
    }

    // =====================================================================
    //                       AUTH SCREENS (shared frame)
    // =====================================================================
    private JPanel brandPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(INK);
        p.setBorder(new EmptyBorder(56, 56, 52, 40));
        p.setPreferredSize(new Dimension(440, 0));

        p.add(Box.createVerticalGlue());
        p.add(L(lbl(BRAND, serif(Font.PLAIN, 58), Color.WHITE)));
        p.add(vs(14));
        p.add(rule(GOLD, 56, 2));
        p.add(vs(16));
        p.add(L(lbl("CLOTHING SALES AND", tracked(sans(Font.BOLD, 11), 0.16f), GOLD)));
        p.add(vs(4));
        p.add(L(lbl("ORDERING SYSTEM", tracked(sans(Font.BOLD, 11), 0.16f), GOLD)));
        p.add(vs(22));
        p.add(L(lbl("Curated fashion, ordered with ease.", serif(Font.ITALIC, 15),
                new Color(210, 205, 196))));
        p.add(Box.createVerticalGlue());

        p.add(L(lbl("OUR BRANCHES", tracked(sans(Font.BOLD, 10), 0.18f), new Color(150, 146, 138))));
        p.add(vs(12));
        p.add(L(branchLine("Kinawe", "Main Branch")));
        p.add(vs(8));
        p.add(L(branchLine("Opol", "Branch")));
        return p;
    }

    private JPanel branchLine(String name, String tag) {
        JPanel r = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        r.setOpaque(false);
        r.add(lbl(name, serif(Font.PLAIN, 22), Color.WHITE));
        JLabel t = lbl("   " + tag, sans(Font.PLAIN, 12), GOLD);
        r.add(t);
        r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        return r;
    }

    private JPanel authCard() {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBackground(Color.WHITE);
        box.setBorder(new CompoundBorder(new LineBorder(LINE), new EmptyBorder(38, 40, 36, 40)));
        return box;
    }

    private void showAuth(JPanel card) {
        getRootPane().setDefaultButton(null);
        getContentPane().removeAll();
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(brandPanel(), BorderLayout.WEST);

        JPanel right = new JPanel(new GridBagLayout());
        right.setBackground(IVORY);
        right.add(card);
        JScrollPane sp = new JScrollPane(right);
        sp.setBorder(null);
        sp.getViewport().setBackground(IVORY);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        getContentPane().add(sp, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private void addField(JPanel box, String label, JComponent field) {
        box.add(left(capLabel(label)));
        box.add(vs(7));
        box.add(field);
        box.add(vs(16));
    }

    private JPanel labeled(String label, JComponent field) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        p.add(left(capLabel(label)));
        p.add(vs(7));
        p.add(field);
        return p;
    }

    private RoundButton wideButton(String text) {
        RoundButton b = new RoundButton(text, RoundButton.PRIMARY);
        b.setFont(sans(Font.BOLD, 13));
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.setPreferredSize(new Dimension(330, 46));
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        return b;
    }

    private void heading(JPanel box, String title, String subtitle) {
        box.add(left(lbl(title, serif(Font.PLAIN, 30), INK)));
        box.add(vs(4));
        box.add(left(lbl(subtitle, sans(Font.PLAIN, 13), MUTED)));
        box.add(vs(24));
    }

    // ---------------------------- LOGIN ----------------------------
    private void showLogin() {
        JPanel box = authCard();
        heading(box, "Welcome back", "Sign in to your " + BRAND + " account");

        Segmented role = new Segmented(new String[]{"Admin", "Customer"}, 0);
        role.setAlignmentX(Component.CENTER_ALIGNMENT);
        role.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));

        JTextField user = new JTextField();
        JPasswordField pass = new JPasswordField();
        styleField(user);
        styleField(pass);

        // Show Password: hidden by default, visible only while checked.
        char defaultEchoChar = pass.getEchoChar();
        JCheckBox showPassword = new JCheckBox("Show password");
        showPassword.setOpaque(false);
        showPassword.setFocusPainted(false);
        showPassword.setForeground(MUTED);
        showPassword.setFont(sans(Font.PLAIN, 12));
        showPassword.setAlignmentX(Component.LEFT_ALIGNMENT);
        showPassword.addActionListener(e -> {
            pass.setEchoChar(showPassword.isSelected() ? (char) 0 : defaultEchoChar);
        });

        JLabel error = lbl(" ", sans(Font.PLAIN, 12), DANGER);

        RoundButton forgot = new RoundButton("Forgot password?", RoundButton.LINK);
        forgot.addActionListener(e -> showForgotPassword());
        RoundButton register = new RoundButton("Create an account", RoundButton.LINK);
        register.addActionListener(e -> showRegister());
        JPanel links = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        links.setOpaque(false);
        links.add(forgot);
        links.add(register);
        links.setAlignmentX(Component.CENTER_ALIGNMENT);
        links.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        Runnable refreshRole = () -> {
            links.setVisible(role.getSelected() == 1);
            error.setText(" ");
            user.setText("");
            pass.setText("");
            showPassword.setSelected(false);
            pass.setEchoChar(defaultEchoChar);
        };
        role.setOnChange(i -> refreshRole.run());
        refreshRole.run();

        RoundButton loginBtn = wideButton("SIGN IN");
        loginBtn.addActionListener(e -> {
            String u = user.getText().trim();
            String p = new String(pass.getPassword());
            if (u.isEmpty() || p.isEmpty()) {
                error.setText("Please enter your username and password.");
                return;
            }
            if (role.getSelected() == 0) {
                if (Database.checkLogin(u, p, "Admin")) {
                    showDashboard();
                } else {
                    error.setText("Invalid admin username or password.");
                    pass.setText("");
                }
            } else {
                String key = u.toLowerCase();
                if (!customers.containsKey(key)) {
                    customers.putAll(Database.loadCustomers());
                }
                String[] acc = customers.get(key);
                if (acc == null) {
                    error.setText("No account found. Please register first.");
                } else if (!Database.checkLogin(u, p, "Customer")) {
                    error.setText("Incorrect password.");
                    pass.setText("");
                } else {
                    showCustomerHome(key);
                }
            }
        });

        box.add(role);
        box.add(vs(24));
        addField(box, "Username", user);
        addField(box, "Password", pass);
        box.add(showPassword);
        box.add(vs(4));
        box.add(left(error));
        box.add(vs(12));
        box.add(loginBtn);
        box.add(vs(14));
        box.add(links);

        showAuth(box);
        getRootPane().setDefaultButton(loginBtn);
        user.requestFocusInWindow();
    }

    // --------------------------- REGISTER ---------------------------
    private void showRegister() {
        JPanel box = authCard();
        heading(box, "Create account", "Join " + BRAND + " and shop at either branch");

        JTextField first = new JTextField();
        JTextField middle = new JTextField();
        JTextField last = new JTextField();
        JTextField email = new JTextField();
        JTextField user = new JTextField();
        JPasswordField pass = new JPasswordField();
        JPasswordField confirm = new JPasswordField();
        for (JTextField f : new JTextField[]{first, middle, last, email, user, pass, confirm}) {
            styleField(f);
        }
        first.setPreferredSize(new Dimension(130, 42));
        middle.setPreferredSize(new Dimension(56, 42));
        last.setPreferredSize(new Dimension(130, 42));
        JLabel error = lbl(" ", sans(Font.PLAIN, 12), DANGER);

        JPanel nameRow = new JPanel(new GridBagLayout());
        nameRow.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL;
        g.gridx = 0; g.weightx = 1; g.insets = new Insets(0, 0, 0, 10);
        nameRow.add(labeled("First name", first), g);
        g.gridx = 1; g.weightx = 0.25; g.insets = new Insets(0, 0, 0, 10);
        nameRow.add(labeled("M.I.", middle), g);
        g.gridx = 2; g.weightx = 1; g.insets = new Insets(0, 0, 0, 0);
        nameRow.add(labeled("Last name", last), g);
        nameRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        nameRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, nameRow.getPreferredSize().height));

        box.add(nameRow);
        box.add(vs(6));
        box.add(left(lbl("Middle initial is optional.", sans(Font.PLAIN, 11), MUTED)));
        box.add(vs(14));
        addField(box, "Email", email);
        addField(box, "Username", user);
        addField(box, "Password", pass);
        addField(box, "Confirm password", confirm);
        box.add(left(error));
        box.add(vs(12));

        RoundButton registerBtn = wideButton("CREATE ACCOUNT");
        registerBtn.addActionListener(e -> {
            String fn = first.getText().trim();
            String mi = middle.getText().trim().replace(".", "");
            String ln = last.getText().trim();
            String em = email.getText().trim();
            String u = user.getText().trim().toLowerCase();
            String p = new String(pass.getPassword());
            String c = new String(confirm.getPassword());

            if (fn.isEmpty() || ln.isEmpty() || em.isEmpty() || u.isEmpty()
                    || p.isEmpty() || c.isEmpty()) {
                error.setText("Please fill in all required fields.");
            } else if (!validName(fn)) {
                error.setText("First name must be 1-50 letters and may contain spaces, apostrophes or hyphens.");
            } else if (!mi.isEmpty() && !mi.matches("[A-Za-z]")) {
                error.setText("Middle initial must be a single letter.");
            } else if (!ln.matches("[A-Za-z][A-Za-z .'-]*")) {
                error.setText("Last name can only contain letters.");
            } else if (!em.matches("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$")) {
                error.setText("Please enter a valid email address.");
            } else if (u.equals(ADMIN_USER) || customers.containsKey(u) || Database.usernameExists(u)) {
                error.setText("That username is already taken.");
            } else if (!validPassword(p)) {
                error.setText("Password must be 6-128 characters.");
            } else if (!p.equals(c)) {
                error.setText("Passwords do not match.");
            } else {
                String fullName = fn + (mi.isEmpty() ? "" : " " + mi.toUpperCase() + ".") + " " + ln;
                if (!Database.registerUser(u, p, em, fn, mi, ln)) return;
                customers.put(u, new String[]{p, em, fullName, null});
                JOptionPane.showMessageDialog(this, "Account created! You can now sign in.",
                        "Welcome to " + BRAND, JOptionPane.INFORMATION_MESSAGE);
                showLogin();
            }
        });

        RoundButton back = new RoundButton("Back to sign in", RoundButton.LINK);
        back.setAlignmentX(Component.CENTER_ALIGNMENT);
        back.addActionListener(e -> showLogin());

        box.add(registerBtn);
        box.add(vs(10));
        box.add(back);

        showAuth(box);
        getRootPane().setDefaultButton(registerBtn);
        first.requestFocusInWindow();
    }

    // ------------------------ FORGOT PASSWORD ------------------------
    private void showForgotPassword() {
        JPanel box = authCard();
        heading(box, "Reset password", "Confirm your details to set a new password");

        JTextField user = new JTextField();
        JTextField email = new JTextField();
        JPasswordField newPass = new JPasswordField();
        JPasswordField confirm = new JPasswordField();
        for (JTextField f : new JTextField[]{user, email, newPass, confirm}) styleField(f);
        JLabel error = lbl(" ", sans(Font.PLAIN, 12), DANGER);

        addField(box, "Username", user);
        addField(box, "Registered email", email);
        addField(box, "New password", newPass);
        addField(box, "Confirm new password", confirm);
        box.add(left(error));
        box.add(vs(12));

        RoundButton resetBtn = wideButton("RESET PASSWORD");
        resetBtn.addActionListener(e -> {
            String u = user.getText().trim().toLowerCase();
            String em = email.getText().trim();
            String p = new String(newPass.getPassword());
            String c = new String(confirm.getPassword());
            String[] acc = customers.get(u);
            if (u.isEmpty() || em.isEmpty() || p.isEmpty() || c.isEmpty()) {
                error.setText("Please fill in all fields.");
            } else if (!validUsername(u) || !validEmail(em)) {
                error.setText("Please enter a valid username and registered email.");
            } else if (acc == null || !acc[1].equalsIgnoreCase(em)) {
                error.setText("Username and email do not match any account.");
            } else if (!validPassword(p)) {
                error.setText("Password must be 6-128 characters.");
            } else if (!p.equals(c)) {
                error.setText("Passwords do not match.");
            } else {
                Database.updatePassword(u, p);
                JOptionPane.showMessageDialog(this, "Password updated! Please sign in.",
                        "Success", JOptionPane.INFORMATION_MESSAGE);
                showLogin();
            }
        });

        RoundButton back = new RoundButton("Back to sign in", RoundButton.LINK);
        back.setAlignmentX(Component.CENTER_ALIGNMENT);
        back.addActionListener(e -> showLogin());

        box.add(resetBtn);
        box.add(vs(10));
        box.add(back);

        showAuth(box);
        getRootPane().setDefaultButton(resetBtn);
        user.requestFocusInWindow();
    }

    // =====================================================================
    //                  DASHBOARD SHELL (sidebar + content)
    // =====================================================================
    private void buildShell(String roleText, String who, String[] pages, Consumer<String> nav) {
        navButtons.clear();
        getRootPane().setDefaultButton(null);
        getContentPane().removeAll();
        getContentPane().setLayout(new BorderLayout());

        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBackground(INK);
        side.setBorder(new EmptyBorder(36, 0, 24, 0));
        side.setPreferredSize(new Dimension(320, 0));

        JLabel brand = L(lbl(BRAND, serif(Font.PLAIN, 32), Color.WHITE));
        brand.setBorder(new EmptyBorder(0, 28, 0, 28));
        JLabel sys = L(lbl("<html><div style='width:250px'>CLOTHING SALES AND ORDERING SYSTEM</div></html>",
                tracked(sans(Font.BOLD, 8), 0.06f), GOLD));
        sys.setBorder(new EmptyBorder(0, 28, 0, 28));

        side.add(brand);
        side.add(vs(8));
        side.add(sys);
        side.add(vs(26));
        side.add(sideRule());
        side.add(vs(18));

        JLabel roleLbl = L(lbl(roleText.toUpperCase(), tracked(sans(Font.BOLD, 9), 0.08f), GOLD));
        roleLbl.setBorder(new EmptyBorder(0, 0, 0, 0));
        roleLbl.setHorizontalAlignment(SwingConstants.LEFT);
        whoLabel = L(lbl(who, serif(Font.PLAIN, 16), Color.WHITE));
        whoLabel.setHorizontalAlignment(SwingConstants.LEFT);

        JPanel profileRow = new JPanel(new BorderLayout(10, 0));
        profileRow.setOpaque(false);
        
        profileRow.setBorder(new EmptyBorder(0, 104, 0, 16));
        
        profileRow.setPreferredSize(new Dimension(200, 56));
        profileRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        ProfileAvatarLabel profileIcon = new ProfileAvatarLabel(46);
        profileIcon.setPreferredSize(new Dimension(46, 46));
        profileIcon.setMinimumSize(new Dimension(46, 46));
        profileIcon.setMaximumSize(new Dimension(46, 46));
        profileIcon.setHorizontalAlignment(SwingConstants.CENTER);
        profileIcon.setVerticalAlignment(SwingConstants.CENTER);
        profileIcon.setBorder(null);
        profileIcon.setOpaque(false);
        profileIcon.setBackground(INK);
        profileIcon.setToolTipText("Choose profile photo");
        updateProfileIcon(profileIcon, currentUser, 42);
        profileIcon.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                chooseProfilePhoto(currentUser, profileIcon);
            }
        });
        JPanel profileText = new JPanel();
        profileText.setOpaque(false);
        profileText.setLayout(new BoxLayout(profileText, BoxLayout.Y_AXIS));
        profileText.add(roleLbl);
        profileText.add(vs(4));
        whoLabel.setBorder(new EmptyBorder(0, 0, 0, 0));
        profileText.add(whoLabel);
        profileRow.add(profileIcon, BorderLayout.WEST);
        profileRow.add(profileText, BorderLayout.CENTER);

        side.add(profileRow);
        side.add(vs(18));
        side.add(sideRule());
        side.add(vs(14));

        for (String pg : pages) {
            NavButton b = new NavButton(pg);
            b.addActionListener(e -> nav.accept(pg));
            navButtons.add(b);
            side.add(b);
        }

        side.add(Box.createVerticalGlue());
        NavButton out = new NavButton("Sign out");
        out.setForeground(new Color(230, 150, 140));
        out.addActionListener(e -> {
            int ok = JOptionPane.showConfirmDialog(this, "Sign out of your account?",
                    "Sign out", JOptionPane.YES_NO_OPTION);
            if (ok == JOptionPane.YES_OPTION) showLogin();
        });
        side.add(out);

        content = new JPanel(new BorderLayout());
        content.setBackground(IVORY);

        getContentPane().add(side, BorderLayout.WEST);
        getContentPane().add(content, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private JPanel sideRule() {
        JPanel r = new JPanel();
        r.setBackground(new Color(255, 255, 255, 40));
        r.setPreferredSize(new Dimension(10, 1));
        r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        r.setAlignmentX(Component.LEFT_ALIGNMENT);
        return r;
    }

    private void updateProfileIcon(JLabel label, String username, int size) {
        
        String path = username == null ? null : Database.getProfilePhoto(username);
        if (path == null || path.isBlank()) {
            label.setIcon(null);
            label.setText("👤");
            label.setForeground(GOLD);
            label.setFont(sans(Font.PLAIN, 20));
            label.repaint();
            return;
        }

        
        BufferedImage source;
        try {
            source = ImageIO.read(Paths.get(path).toFile());
        } catch (IOException ex) {
            source = null;
        }
        if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) {
            label.setIcon(null);
            label.setText("👤");
            label.setForeground(GOLD);
            label.setFont(sans(Font.PLAIN, 20));
            label.repaint();
            return;
        }

        int srcW = source.getWidth();
        int srcH = source.getHeight();
        int side = Math.min(srcW, srcH);
        int x = (srcW - side) / 2;
        int y = (srcH - side) / 2;

        BufferedImage square = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = square.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(source, 0, 0, side, side, x, y, x + side, y + side, null);
        g.dispose();

        Image scaled = square.getScaledInstance(size, size, Image.SCALE_SMOOTH);
        label.setText(null);
        label.setIcon(new ImageIcon(scaled));
        label.revalidate();
        label.repaint();
    }

    
    private static class ProfileAvatarLabel extends JLabel {
        private final int diameter;

        ProfileAvatarLabel(int diameter) {
            super();
            this.diameter = diameter;
            setPreferredSize(new Dimension(diameter, diameter));
            setMinimumSize(new Dimension(diameter, diameter));
            setMaximumSize(new Dimension(diameter, diameter));
            setHorizontalAlignment(SwingConstants.CENTER);
            setVerticalAlignment(SwingConstants.CENTER);
            setOpaque(false);
            setFocusable(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            int d = Math.min(getWidth(), getHeight());
            int x = (getWidth() - d) / 2;
            int y = (getHeight() - d) / 2;

            // Circular clipping makes the selected photo itself round.
            g.setClip(new java.awt.geom.Ellipse2D.Double(x, y, d, d));
            g.setColor(INK);
            g.fillOval(x, y, d, d);
            super.paintComponent(g);
            g.dispose();

            // Gold circular outline matching the existing StyleHub design.
            g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(GOLD);
            g.setStroke(new BasicStroke(1.2f));
            g.drawOval(x, y, d - 1, d - 1);
            g.dispose();
        }
    }

    private void chooseProfilePhoto(String username, JLabel target) {
        if (username == null || username.isBlank()) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose Profile Photo");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Image files (*.jpg, *.jpeg, *.png)", "jpg", "jpeg", "png"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            Path dir = Paths.get(System.getProperty("user.home"), ".stylehub", "profile-images");
            Files.createDirectories(dir);
            String original = chooser.getSelectedFile().getName();
            int dot = original.lastIndexOf('.');
            String ext = dot >= 0 ? original.substring(dot).toLowerCase() : ".png";
            validateImageFile(chooser.getSelectedFile());
            if (!(ext.equals(".jpg") || ext.equals(".jpeg") || ext.equals(".png"))) {
                throw new IOException("Please select a JPG, JPEG, or PNG image.");
            }
            Path targetFile = dir.resolve("profile_" + username.replaceAll("[^a-zA-Z0-9_-]", "_") + ext);
            Files.copy(chooser.getSelectedFile().toPath(), targetFile, StandardCopyOption.REPLACE_EXISTING);
            Database.updateProfilePhoto(username, targetFile.toAbsolutePath().toString());
            updateProfileIcon(target, username, 42);
            if (username.equalsIgnoreCase(currentUser)) {
                String[] acc = customers.get(username.toLowerCase());
                if (acc != null && acc.length > 3) acc[3] = targetFile.toAbsolutePath().toString();
            }
            JOptionPane.showMessageDialog(this, "Profile photo updated.", "Profile", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not save the profile photo.\n" + ex.getMessage(),
                    "Photo Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void setPage(String page, JPanel p) {
        for (NavButton b : navButtons) b.setActive(b.getText().equals(page));
        content.removeAll();
        content.add(p, BorderLayout.CENTER);
        content.revalidate();
        content.repaint();
    }

    
    private JPanel shell(String title, String subtitle) {
        JPanel page = new JPanel(new BorderLayout(0, 20));
        page.setBackground(IVORY);
        page.setBorder(new EmptyBorder(32, 40, 28, 40));

        JPanel head = new JPanel();
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.setOpaque(false);

        
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel titleLabel = L(lbl(title, serif(Font.PLAIN, 32), INK));
        titleRow.add(titleLabel, BorderLayout.WEST);

        RefreshIconButton refreshIcon = new RefreshIconButton();
        refreshIcon.setToolTipText("Refresh data");
        refreshIcon.addActionListener(e -> {
            String activePage = null;
            for (NavButton b : navButtons) {
                if (b.isActive()) { activePage = b.getText(); break; }
            }
            refreshDatabaseState();
            if (currentUser != null && currentUser.equalsIgnoreCase(ADMIN_USER)) {
                admGo(activePage == null ? "Overview" : activePage);
            } else if (currentUser != null) {
                String[] acc = customers.get(currentUser.toLowerCase());
                if (acc != null && acc.length > 2) {
                    whoLabel.setText(acc[2]);
                }
                custGo(activePage == null ? "Shop" : activePage);
            }
        });
        JPanel refreshWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        refreshWrap.setOpaque(false);
        refreshWrap.add(refreshIcon);
        titleRow.add(refreshWrap, BorderLayout.EAST);
        head.add(titleRow);

        head.add(vs(8));
        head.add(rule(GOLD, 44, 2));
        head.add(vs(10));
        head.add(L(lbl(subtitle, sans(Font.PLAIN, 13), MUTED)));
        page.add(head, BorderLayout.NORTH);
        return page;
    }

    // =====================================================================
    //                       STOCK / ORDER HELPERS
    // =====================================================================
    private static String pickupAddress(int branch) {
        return PICKUP_ADDRESSES[Math.max(0, Math.min(branch, PICKUP_ADDRESSES.length - 1))];
    }

    private static String pickupTicketCode(Order o) {
        return String.format("SH-PU-%06d", o.id);
    }

    private void restoreStock(Order o) {
        for (CartItem it : o.items) it.product.stock[o.branch] += it.qty;
        Database.restoreStock(o.id);
    }

    private void removeOrder(Order o) {
        // Archive only: do not delete the SQL row and do not change stock.
        orders.remove(o);
        Database.deleteOrder(o.id);
    }

    private void removeOrdersOf(String user) {
        for (Order o : new ArrayList<>(orders)) if (o.owner.equals(user)) removeOrder(o);
    }

    private int branchOf(String user) { return cartBranch.getOrDefault(user, 0); }

    private Order findOrderById(int orderId) {
        for (Order o : orders) if (o.id == orderId) return o;
        return null;
    }

    private Order pick(JTable table, ArrayList<Order> shown, JLabel msg) {
        int row = table.getSelectedRow();
        if (row < 0) {
            say(msg, "Please select an order first.", false);
            return null;
        }
        return shown.get(row);
    }

    private Integer askQty(String title, String label, int init, int min, int max) {
        JSpinner sp = new JSpinner(new SpinnerNumberModel(init, min, max, 1));
        Object[] form = {label, sp};
        int ok = JOptionPane.showConfirmDialog(this, form, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        return ok == JOptionPane.OK_OPTION ? (Integer) sp.getValue() : null;
    }

    private void showOrderDetails(Order o) {
        String[] acc = customers.get(o.owner);
        JDialog dialog = new JDialog(this, "Order #" + o.id + " - Order Details", true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setSize(980, 760);
        dialog.setMinimumSize(new Dimension(860, 680));
        dialog.setLocationRelativeTo(this);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(IVORY);

        JPanel header = new JPanel(new BorderLayout(20, 0));
        header.setBackground(IVORY);
        header.setBorder(new EmptyBorder(24, 28, 18, 28));
        JPanel title = new JPanel();
        title.setOpaque(false);
        title.setLayout(new BoxLayout(title, BoxLayout.Y_AXIS));
        title.add(lbl("ORDER #" + o.id, tracked(sans(Font.BOLD, 10), 0.14f), GOLD));
        title.add(vs(5));
        title.add(lbl("Order details", serif(Font.PLAIN, 28), INK));
        title.add(vs(5));
        title.add(lbl((acc != null ? acc[2] : o.owner) + "  •  " + o.date, sans(Font.PLAIN, 12), MUTED));
        header.add(title, BorderLayout.WEST);

        JPanel statusBox = new JPanel();
        statusBox.setOpaque(false);
        statusBox.setLayout(new BoxLayout(statusBox, BoxLayout.Y_AXIS));
        JLabel status = lbl(o.status.toUpperCase(), sans(Font.BOLD, 11), INK);
        status.setHorizontalAlignment(SwingConstants.CENTER);
        status.setOpaque(true);
        status.setBorder(new EmptyBorder(8, 14, 8, 14));
        status.setBackground(o.status.equals("Cancelled") ? new Color(245, 225, 221)
                : o.status.equals("Delivered") ? new Color(224, 239, 226) : new Color(239, 229, 211));
        statusBox.add(status);
        statusBox.add(vs(8));
        statusBox.add(lbl(BRANCHES[o.branch], sans(Font.BOLD, 12), INK));
        statusBox.add(vs(3));
        statusBox.add(lbl(o.method, sans(Font.PLAIN, 12), MUTED));
        header.add(statusBox, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 16));
        body.setBackground(IVORY);
        body.setBorder(new EmptyBorder(0, 28, 0, 28));

        JPanel items = new JPanel();
        items.setLayout(new BoxLayout(items, BoxLayout.Y_AXIS));
        items.setBackground(Color.WHITE);
        items.setBorder(new CompoundBorder(new LineBorder(LINE), new EmptyBorder(6, 6, 6, 6)));
        for (CartItem it : o.items) {
            JPanel card = new JPanel(new BorderLayout(22, 0));
            card.setBackground(Color.WHITE);
            card.setBorder(new CompoundBorder(new MatteBorder(0, 0, 1, 0, LINE),
                    new EmptyBorder(18, 18, 18, 18)));
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));

            JPanel photo = new JPanel(new BorderLayout());
            photo.setBackground(new Color(247, 244, 238));
            photo.setPreferredSize(new Dimension(170, 180));
            photo.setBorder(new LineBorder(new Color(225, 218, 207)));
            JLabel pic = new JLabel();
            pic.setHorizontalAlignment(SwingConstants.CENTER);
            pic.setVerticalAlignment(SwingConstants.CENTER);
            ImageIcon icon = productIcon(it.product, it.color, 150, 160);
            if (icon != null) pic.setIcon(icon); else pic.setText("No Photo");
            photo.add(pic, BorderLayout.CENTER);

            JPanel info = new JPanel();
            info.setOpaque(false);
            info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
            info.add(lbl(it.product.name, serif(Font.PLAIN, 22), INK));
            info.add(vs(7));
            info.add(lbl(it.product.category.toUpperCase(), tracked(sans(Font.BOLD, 9), 0.12f), GOLD));
            info.add(vs(13));
            JPanel details = new JPanel(new GridLayout(2, 4, 14, 7));
            details.setOpaque(false);
            details.add(lbl("COLOR", tracked(sans(Font.BOLD, 9), 0.10f), MUTED));
            details.add(lbl("SIZE", tracked(sans(Font.BOLD, 9), 0.10f), MUTED));
            details.add(lbl("QTY", tracked(sans(Font.BOLD, 9), 0.10f), MUTED));
            details.add(lbl("SUBTOTAL", tracked(sans(Font.BOLD, 9), 0.10f), MUTED));
            details.add(lbl(it.color, sans(Font.PLAIN, 14), INK));
            details.add(lbl(it.size, sans(Font.PLAIN, 14), INK));
            details.add(lbl(String.valueOf(it.qty), sans(Font.PLAIN, 14), INK));
            details.add(lbl(money(it.subtotal()), sans(Font.BOLD, 15), INK));
            info.add(details);
            info.add(vs(14));
            info.add(lbl(money(it.product.price) + " each", sans(Font.PLAIN, 12), MUTED));
            card.add(photo, BorderLayout.WEST);
            card.add(info, BorderLayout.CENTER);
            items.add(card);
        }
        JScrollPane itemScroll = new JScrollPane(items);
        itemScroll.setBorder(null);
        itemScroll.getVerticalScrollBar().setUnitIncrement(18);
        body.add(itemScroll, BorderLayout.CENTER);

        JPanel tracking = new JPanel(new BorderLayout());
        tracking.setBackground(Color.WHITE); tracking.setBorder(new CompoundBorder(new LineBorder(LINE), new EmptyBorder(14,16,14,16)));
        JPanel steps = new JPanel(new GridLayout(1, 5, 4, 0)); steps.setOpaque(false);
        String[] track = {"Placed","Processing","Shipped","Delivered","Completed"};
        int active = o.status.equals("Pending") ? 0 : o.status.equals("Processing") ? 1 : o.status.equals("Shipped") ? 2 : o.status.equals("Delivered") ? 3 : o.status.equals("Cancelled") ? -1 : 4;
        for(int i=0;i<track.length;i++){JPanel st=new JPanel();st.setOpaque(false);st.setLayout(new BoxLayout(st,BoxLayout.Y_AXIS));JLabel dot=lbl(i<=active?"●":"○",sans(Font.BOLD,18),i<=active?GOLD:MUTED);dot.setAlignmentX(Component.CENTER_ALIGNMENT);st.add(dot);st.add(vs(2));JLabel tx=lbl(track[i],sans(Font.BOLD,10),i<=active?INK:MUTED);tx.setAlignmentX(Component.CENTER_ALIGNMENT);st.add(tx);steps.add(st);}
        tracking.add(lbl("ORDER PROGRESS",tracked(sans(Font.BOLD,9),.10f),MUTED),BorderLayout.NORTH);tracking.add(steps,BorderLayout.CENTER);
        JPanel infoBar = new JPanel(new GridLayout(1, 2, 14, 0));
        infoBar.setOpaque(false);
        JPanel receive = whiteCard(16);
        receive.setLayout(new BoxLayout(receive, BoxLayout.Y_AXIS));
        receive.add(lbl("RECEIVE YOUR ORDER", tracked(sans(Font.BOLD, 9), 0.10f), MUTED));
        receive.add(vs(7));
        if ("Pick-up".equals(o.method)) {
            receive.add(lbl("Pick-up location", sans(Font.BOLD, 12), INK));
            receive.add(vs(3));
            receive.add(lbl(pickupAddress(o.branch), sans(Font.PLAIN, 12), INK));
            receive.add(vs(8));
            receive.add(lbl("Show your pickup ticket when you arrive.", sans(Font.PLAIN, 11), MUTED));
        } else {
            receive.add(lbl("Delivery address", sans(Font.BOLD, 12), INK));
            receive.add(vs(3));
            receive.add(lbl(o.address.isBlank() ? "No address provided" : o.address, sans(Font.PLAIN, 12), INK));
        }
        JPanel payment = whiteCard(16);
        payment.setLayout(new BoxLayout(payment, BoxLayout.Y_AXIS));
        payment.add(lbl("PAYMENT", tracked(sans(Font.BOLD, 9), 0.10f), MUTED));
        payment.add(vs(7));
        payment.add(lbl(o.payment, sans(Font.BOLD, 13), INK));
        payment.add(vs(5));
        payment.add(lbl(money(o.total()), serif(Font.PLAIN, 24), INK));
        payment.add(vs(2));
        payment.add(lbl(o.itemCount() + " item(s)", sans(Font.PLAIN, 11), MUTED));
        infoBar.add(receive);
        infoBar.add(payment);
        JPanel southWrap = new JPanel(new BorderLayout(0,12)); southWrap.setOpaque(false); southWrap.add(tracking,BorderLayout.NORTH); southWrap.add(infoBar,BorderLayout.CENTER);
        body.add(southWrap, BorderLayout.SOUTH);
        root.add(body, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 14));
        actions.setBackground(IVORY);
        actions.setBorder(new EmptyBorder(0, 28, 14, 28));
        if ("Pick-up".equals(o.method)) {
            RoundButton ticket = new RoundButton("VIEW PICKUP TICKET", RoundButton.GOLDV);
            ticket.addActionListener(e -> showPickupTicket(o));
            actions.add(ticket);
        }
        if ("Delivered".equals(o.status) || "Completed".equals(o.status)) {
            RoundButton review = new RoundButton("RATE PRODUCT", RoundButton.OUTLINE);
            review.addActionListener(e -> showReviewDialog(o));
            actions.add(review);
        }
        RoundButton close = new RoundButton("CLOSE", RoundButton.OUTLINE);
        close.addActionListener(e -> dialog.dispose());
        actions.add(close);
        root.add(actions, BorderLayout.SOUTH);
        dialog.setContentPane(root);
        dialog.setVisible(true);
    }

    private void showReviewDialog(Order o) {
        if (o.items.isEmpty()) return;
        JDialog d=new JDialog(this,"StyleHub - Review Your Purchase",true);d.setSize(600,520);d.setLocationRelativeTo(this);
        JPanel root=new JPanel();root.setBackground(IVORY);root.setBorder(new EmptyBorder(24,28,22,28));root.setLayout(new BoxLayout(root,BoxLayout.Y_AXIS));
        root.add(lbl("YOUR FEEDBACK",tracked(sans(Font.BOLD,10),.14f),GOLD));root.add(vs(5));root.add(lbl("How was your purchase?",serif(Font.PLAIN,26),INK));root.add(vs(16));
        JComboBox<String> product=new JComboBox<>();for(CartItem it:o.items)product.addItem(it.product.name+" • "+it.color+" • "+it.size);
        JComboBox<Integer> stars=new JComboBox<>(new Integer[]{5,4,3,2,1});JTextArea comment=new JTextArea(5,30);comment.setLineWrap(true);comment.setWrapStyleWord(true);comment.setBorder(new CompoundBorder(new LineBorder(LINE),new EmptyBorder(8,8,8,8)));JLabel msg=new JLabel(" ");
        addField(root,"Product",product);addField(root,"Rating (stars)",stars);addField(root,"Comment",new JScrollPane(comment));root.add(left(msg));root.add(vs(8));
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT));buttons.setOpaque(false);RoundButton save=new RoundButton("SUBMIT REVIEW",RoundButton.GOLDV);RoundButton close=new RoundButton("CLOSE",RoundButton.OUTLINE);close.addActionListener(e->d.dispose());save.addActionListener(e->{int idx=product.getSelectedIndex();CartItem it=o.items.get(idx);if(Database.saveReview(currentUser,o.id,it.product.id,(Integer)stars.getSelectedItem(),comment.getText().trim())){say(msg,"Thank you for your review!",true);}else say(msg,"You already reviewed this product in this order.",false);});buttons.add(close);buttons.add(save);root.add(buttons);d.setContentPane(root);d.setVisible(true);
    }

    private boolean showGcashPayment(double amount) {
        JDialog d = new JDialog(this, "GCash Payment", true);
        d.setSize(520, 520);
        d.setLocationRelativeTo(this);
        JPanel root = new JPanel();
        root.setBackground(IVORY);
        root.setBorder(new EmptyBorder(26, 28, 24, 28));
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.add(lbl("GCASH PAYMENT", tracked(sans(Font.BOLD, 10), 0.14f), GOLD));
        root.add(vs(6));
        root.add(lbl("Pay using GCash", serif(Font.PLAIN, 25), INK));
        root.add(vs(16));

        JPanel card = whiteCard(18);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.add(lbl("PAY TO", tracked(sans(Font.BOLD, 9), 0.10f), MUTED));
        card.add(vs(6));
        card.add(lbl(GCASH_ACCOUNT_NAME, sans(Font.BOLD, 15), INK));
        card.add(vs(3));
        JTextField number = new JTextField(GCASH_ACCOUNT_NUMBER);
        number.setEditable(false);
        styleField(number);
        card.add(number);
        card.add(vs(10));
        card.add(lbl("AMOUNT", tracked(sans(Font.BOLD, 9), 0.10f), MUTED));
        card.add(vs(3));
        card.add(lbl(money(amount), serif(Font.PLAIN, 27), INK));
        root.add(card);
        root.add(vs(14));
        JLabel note = lbl("Open GCash, send the exact amount to the merchant account above, then return here and continue. This desktop app cannot log in to or verify a customer's GCash account automatically.", sans(Font.PLAIN, 11), MUTED);
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(note);
        root.add(vs(14));
        final boolean[] confirmed = {false};
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        RoundButton open = new RoundButton("OPEN GCASH", RoundButton.OUTLINE);
        open.addActionListener(e -> {
            try {
                if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(new java.net.URI("https://www.gcash.com/"));
            } catch (Exception ignored) { }
        });
        RoundButton cancel = new RoundButton("CANCEL", RoundButton.OUTLINE);
        cancel.addActionListener(e -> d.dispose());
        RoundButton paid = new RoundButton("I'VE PAID - CONTINUE", RoundButton.GOLDV);
        paid.addActionListener(e -> { confirmed[0] = true; d.dispose(); });
        buttons.add(open); buttons.add(cancel); buttons.add(paid);
        root.add(buttons);
        d.setContentPane(root);
        d.setVisible(true);
        return confirmed[0];
    }

    private void showPickupTicket(Order o) {
        JDialog d = new JDialog(this, "StyleHub Pickup Ticket", true);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        d.setSize(620, 760);
        d.setMinimumSize(new Dimension(560, 700));
        d.setLocationRelativeTo(this);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(IVORY);
        root.setBorder(new EmptyBorder(22, 24, 20, 24));

        // Center the ticket itself instead of letting it hug the left side.
        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.NONE;

        JPanel ticket = new JPanel();
        ticket.setBackground(Color.WHITE);
        ticket.setBorder(new CompoundBorder(
                new LineBorder(new Color(210, 198, 177), 1, true),
                new EmptyBorder(24, 30, 24, 30)));
        ticket.setLayout(new BoxLayout(ticket, BoxLayout.Y_AXIS));
        ticket.setPreferredSize(new Dimension(455, 560));
        ticket.setMaximumSize(new Dimension(455, 560));

        JLabel brand = lbl("STYLEHUB", serif(Font.PLAIN, 29), INK);
        brand.setAlignmentX(Component.CENTER_ALIGNMENT);
        ticket.add(brand);
        ticket.add(vs(3));

        JLabel pickup = lbl("PICKUP TICKET", tracked(sans(Font.BOLD, 10), 0.18f), GOLD);
        pickup.setAlignmentX(Component.CENTER_ALIGNMENT);
        ticket.add(pickup);
        ticket.add(vs(18));

        JPanel codeBox = new JPanel();
        codeBox.setBackground(new Color(247, 242, 232));
        codeBox.setBorder(new CompoundBorder(
                new LineBorder(new Color(218, 198, 165)),
                new EmptyBorder(12, 18, 12, 18)));
        codeBox.setLayout(new BoxLayout(codeBox, BoxLayout.Y_AXIS));
        codeBox.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel codeLabel = lbl("TICKET NUMBER", tracked(sans(Font.BOLD, 8), 0.12f), MUTED);
        codeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        codeBox.add(codeLabel);
        codeBox.add(vs(3));
        JLabel code = lbl(pickupTicketCode(o), serif(Font.PLAIN, 27), INK);
        code.setAlignmentX(Component.CENTER_ALIGNMENT);
        codeBox.add(code);
        ticket.add(codeBox);
        ticket.add(vs(16));

        // Clean ticket information layout.
        JPanel orderLine = new JPanel(new GridLayout(1, 2, 16, 0));
        orderLine.setOpaque(false);
        orderLine.setAlignmentX(Component.CENTER_ALIGNMENT);
        orderLine.add(ticketInfo("ORDER", "#" + o.id));
        orderLine.add(ticketInfo("DATE", o.date));
        ticket.add(orderLine);
        ticket.add(vs(14));
        ticket.add(separator());
        ticket.add(vs(13));

        String[] acc = customers.get(o.owner);
        ticket.add(ticketInfo("CUSTOMER", acc != null ? acc[2] : o.owner));
        ticket.add(vs(11));
        ticket.add(ticketInfo("BRANCH", BRANCHES[o.branch]));
        ticket.add(vs(11));
        ticket.add(ticketInfo("PICKUP ADDRESS", pickupAddress(o.branch)));
        ticket.add(vs(11));
        ticket.add(ticketInfo("PAYMENT", o.payment));
        ticket.add(vs(11));
        ticket.add(ticketInfo("ITEMS", String.valueOf(o.itemCount())));
        ticket.add(vs(11));
        ticket.add(ticketInfo("TOTAL", money(o.total())));
        ticket.add(vs(13));
        ticket.add(separator());
        ticket.add(vs(12));

        JLabel instruction = lbl("Present this ticket when claiming your order.\n",
                sans(Font.PLAIN, 11), MUTED);
        instruction.setAlignmentX(Component.CENTER_ALIGNMENT);
        instruction.setHorizontalAlignment(SwingConstants.CENTER);
        ticket.add(instruction);
        ticket.add(vs(3));
        JLabel status = lbl(o.status.toUpperCase(), sans(Font.BOLD, 10), INK);
        status.setAlignmentX(Component.CENTER_ALIGNMENT);
        status.setHorizontalAlignment(SwingConstants.CENTER);
        status.setOpaque(true);
        status.setBackground(o.status.equals("Cancelled") ? new Color(245, 225, 221)
                : o.status.equals("Delivered") ? new Color(224, 239, 226) : new Color(239, 229, 211));
        status.setBorder(new EmptyBorder(6, 14, 6, 14));
        ticket.add(status);

        center.add(ticket, gbc);
        root.add(center, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttons.setOpaque(false);
        RoundButton print = new RoundButton("PRINT TICKET", RoundButton.GOLDV);
        print.addActionListener(e -> printPickupTicket(o));
        RoundButton close = new RoundButton("CLOSE", RoundButton.OUTLINE);
        close.addActionListener(e -> d.dispose());
        buttons.add(print);
        buttons.add(close);
        root.add(buttons, BorderLayout.SOUTH);

        d.setContentPane(root);
        d.setVisible(true);
    }

    private JPanel ticketInfo(String heading, String value) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JLabel h = lbl(heading, tracked(sans(Font.BOLD, 8), 0.10f), MUTED);
        h.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(h);
        p.add(vs(3));
        JLabel v = lbl(value, sans(Font.PLAIN, 12), INK);
        v.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(v);
        return p;
    }

    private JSeparator separator() {
        JSeparator s = new JSeparator();
        s.setForeground(new Color(225, 218, 207));
        s.setAlignmentX(Component.CENTER_ALIGNMENT);
        s.setMaximumSize(new Dimension(395, 1));
        return s;
    }

    private void printPickupTicket(Order o) {
        try {
            java.awt.print.PrinterJob job = java.awt.print.PrinterJob.getPrinterJob();
            job.setJobName("StyleHub Pickup Ticket #" + o.id);
            job.setPrintable((graphics, pageFormat, pageIndex) -> {
                if (pageIndex > 0) return java.awt.print.Printable.NO_SUCH_PAGE;
                Graphics2D g = (Graphics2D) graphics;
                g.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
                g.setFont(new Font("SansSerif", Font.BOLD, 18)); g.drawString("STYLEHUB - PICKUP TICKET", 20, 30);
                g.setFont(new Font("SansSerif", Font.PLAIN, 12));
                int y = 58;
                String customer = customers.get(o.owner) != null ? customers.get(o.owner)[2] : o.owner;
                String[] lines = {
                    "Ticket: " + pickupTicketCode(o), "Order #: " + o.id, "Date: " + o.date,
                    "Customer: " + customer, "Branch: " + BRANCHES[o.branch],
                    "Pickup address: " + pickupAddress(o.branch), "Items: " + o.itemCount(),
                    "Total: " + money(o.total()), "Payment: " + o.payment, "Status: " + o.status
                };
                for (String line : lines) { g.drawString(line, 20, y); y += 22; }
                return java.awt.print.Printable.PAGE_EXISTS;
            });
            if (job.printDialog()) job.print();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not print the pickup ticket.\n" + ex.getMessage(),
                    "Print Ticket", JOptionPane.ERROR_MESSAGE);
        }
    }

    // =====================================================================
    //                          DATE FILTERS
    // =====================================================================
    private static final String[] DATE_FILTERS = {
        "All Time", "Today", "Yesterday", "This Week", "This Month", "Last Month", "This Year"
    };

    private boolean matchesDateFilter(String dateText, String filter) {
        if ("All Time".equals(filter)) return true;
        try {
            LocalDate d = LocalDate.parse(dateText, java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy"));
            LocalDate now = LocalDate.now();
            switch (filter) {
                case "Today": return d.equals(now);
                case "Yesterday": return d.equals(now.minusDays(1));
                case "This Week":
                    LocalDate start = now.with(DayOfWeek.MONDAY);
                    LocalDate end = start.plusDays(6);
                    return !d.isBefore(start) && !d.isAfter(end);
                case "This Month":
                    return d.getYear() == now.getYear() && d.getMonthValue() == now.getMonthValue();
                case "Last Month":
                    LocalDate lm = now.minusMonths(1);
                    return d.getYear() == lm.getYear() && d.getMonthValue() == lm.getMonthValue();
                case "This Year":
                    return d.getYear() == now.getYear();
                default: return true;
            }
        } catch (Exception ex) {
            return true;
        }
    }

    private JComboBox<String> dateFilterBox() {
        JComboBox<String> box = new JComboBox<>(DATE_FILTERS);
        box.setSelectedItem(adminDateFilter);
        return box;
    }

    private void refreshDatabaseState() {
        customers.clear();
        catalog.clear();
        carts.clear();
        cartBranch.clear();
        orders.clear();
        wishlist.clear();
        loadFromDatabase();
    }

    // =====================================================================
    //                          ADMIN DASHBOARD
    // =====================================================================
    private void showDashboard() {
        currentUser = ADMIN_USER;
        buildShell("Administrator", "Store Manager",
                new String[]{"Overview", "Products", "Inventory", "Orders", "Sales Report", "Order Report", "Customers", "Reviews & Feedback", "Archive"}, this::admGo);
        admGo("Overview");
    }

    private void admGo(String page) {
        JPanel p;
        switch (page) {
            case "Products":  p = admProducts();  break;
            case "Inventory": p = admInventory(); break;
            case "Orders":    p = admOrders();    break;
            case "Sales Report": p = admSalesReport(); break;
            case "Order Report": p = admOrderReport(); break;
            case "Archive": p = admArchive(); break;
            case "Customers": p = admCustomers(); break;
            case "Reviews & Feedback": p = admReviews(); break;
            default:          p = admOverview();
        }
        setPage(page, p);
    }

    // ---------------------------- OVERVIEW ----------------------------
    private JPanel statCard(String label, String value, String note) {
        JPanel p = whiteCard(0);
        p.setBorder(new CompoundBorder(new LineBorder(LINE), new EmptyBorder(16, 20, 16, 20)));
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.add(L(capLabel(label)));
        p.add(vs(8));
        p.add(L(lbl(value, serif(Font.PLAIN, 26), INK)));
        p.add(vs(4));
        p.add(L(lbl(note, sans(Font.PLAIN, 11), MUTED)));
        return p;
    }

    private JPanel metric(String label, String value) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        p.add(L(capLabel(label)));
        p.add(vs(5));
        p.add(L(lbl(value, serif(Font.PLAIN, 17), INK)));
        return p;
    }

    private JPanel branchCard(int b, double sales, double totalSales, int ordCount, int pending, int units) {
        JPanel p = whiteCard(20);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(lbl(BRANCH_SHORT[b], serif(Font.PLAIN, 22), INK), BorderLayout.WEST);
        top.add(lbl(b == 0 ? "MAIN BRANCH" : "BRANCH", tracked(sans(Font.BOLD, 10), 0.14f), GOLD),
                BorderLayout.EAST);
        top.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        JPanel grid = new JPanel(new GridLayout(1, 4, 12, 0));
        grid.setOpaque(false);
        grid.add(metric("Sales", money(sales)));
        grid.add(metric("Orders", String.valueOf(ordCount)));
        grid.add(metric("Pending", String.valueOf(pending)));
        grid.add(metric("Units in stock", String.valueOf(units)));
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

        double share = totalSales > 0 ? sales / totalSales : 0;
        p.add(top);
        p.add(vs(16));
        p.add(grid);
        p.add(vs(16));
        p.add(L(lbl("Share of total sales: " + Math.round(share * 100) + "%",
                sans(Font.PLAIN, 11), MUTED)));
        p.add(vs(6));
        p.add(L(new ShareBar(share)));
        return p;
    }

    private JPanel admOverview() {
        JPanel page = shell("Overview", "Sales and stock at a glance, across both branches.");

        JComboBox<String> dateFilter = dateFilterBox();
        double[] sales = new double[2];
        int[] ords = new int[2], pend = new int[2], units = new int[2];
        double totalSales = 0;
        int totalOrders = 0;
        for (Order o : orders) {
            if (!matchesDateFilter(o.date, (String) dateFilter.getSelectedItem())) continue;
            if (o.status.equals("Cancelled")) continue;
            sales[o.branch] += o.total();
            ords[o.branch]++;
            totalSales += o.total();
            totalOrders++;
            if (o.status.equals("Pending")) pend[o.branch]++;
        }
        int lowCount = 0;
        DefaultTableModel lowModel = newModel("Product", "Branch", "Left");
        for (Product p : catalog) {
            for (int b = 0; b < 2; b++) {
                units[b] += p.stock[b];
                if (p.stock[b] <= LOW_STOCK) {
                    lowCount++;
                    lowModel.addRow(new Object[]{p.name, BRANCH_SHORT[b], p.stock[b]});
                }
            }
        }

        JPanel stats = new JPanel(new GridLayout(1, 4, 16, 0));
        stats.setOpaque(false);
        stats.add(statCard("Total sales", money(totalSales), "Excludes cancelled orders"));
        stats.add(statCard("Orders", String.valueOf(totalOrders), "Both branches"));
        stats.add(statCard("Customers", String.valueOf(customers.size()), "Registered accounts"));
        stats.add(statCard("Low stock", String.valueOf(lowCount), "Items at " + LOW_STOCK + " or fewer"));

        JPanel branches = new JPanel(new GridLayout(2, 1, 0, 16));
        branches.setOpaque(false);
        for (int b = 0; b < 2; b++) {
            branches.add(branchCard(b, sales[b], totalSales, ords[b], pend[b], units[b]));
        }

        JPanel lowCard = whiteCard(18);
        lowCard.setLayout(new BorderLayout(0, 12));
        lowCard.add(capLabel("Low stock alerts"), BorderLayout.NORTH);
        JTable lowTable = newTable(lowModel);
        lowCard.add(scroll(lowTable), BorderLayout.CENTER);

        JPanel row = new JPanel(new GridLayout(1, 2, 16, 0));
        row.setOpaque(false);
        row.add(branches);
        row.add(lowCard);

        JPanel body = new JPanel(new BorderLayout(0, 16));
        body.setOpaque(false);
        body.add(stats, BorderLayout.NORTH);
        body.add(row, BorderLayout.CENTER);

        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        filterBar.setOpaque(false);
        filterBar.add(capLabel("Date range"));
        filterBar.add(dateFilter);
        RoundButton refresh = new RoundButton("REFRESH", RoundButton.OUTLINE);
        refresh.addActionListener(e -> admGo("Overview"));
        filterBar.add(refresh);

        JPanel wrapped = new JPanel(new BorderLayout(0, 12));
        wrapped.setOpaque(false);
        wrapped.add(filterBar, BorderLayout.NORTH);
        wrapped.add(body, BorderLayout.CENTER);
        page.add(wrapped, BorderLayout.CENTER);
        dateFilter.addActionListener(e -> {
            adminDateFilter = (String) dateFilter.getSelectedItem();
            admGo("Overview");
        });
        return page;
    }

    // ----------------------- PRODUCTS (CRUD) -----------------------
    private JPanel admProducts() {
        JPanel page = shell("Products", "Create, edit and remove items. Set the opening stock for each branch.");

        DefaultTableModel model = newModel("Photo", "ID", "Product", "Category", "Color", "Availability", "Price", "Kinawe", "Opol");
        JTable table = newTable(model);
        installProductPhotoRenderer(table, 0, 48);
        JLabel msg = new JLabel(" ");
        table.setRowHeight(58);
        table.getColumnModel().getColumn(0).setPreferredWidth(72);

        Runnable refresh = () -> {
            model.setRowCount(0);
            for (Product p : catalog) {
                model.addRow(new Object[]{p, p.id, p.name, p.category, firstColor(p), p.availability, money(p.price), p.stock[0], p.stock[1]});
            }
        };

        RoundButton addBtn = new RoundButton("ADD PRODUCT", RoundButton.PRIMARY);
        addBtn.addActionListener(e -> {
            Object[] v = productForm("Add Product", null);
            if (v == null) return;
            int id = Database.addProduct((String) v[0], (String) v[1], (Double) v[2],
                    (Integer) v[3], (Integer) v[4], (String) v[5], (String) v[6], (String) v[7], (String) v[8]);
            if (id < 0) { say(msg, "Could not save the product to the database.", false); return; }
            catalog.add(new Product(id, (String) v[0], (String) v[1], (Double) v[2],
                    (Integer) v[3], (Integer) v[4], (String) v[5], (String) v[6], (String) v[7], (String) v[8]));
            refresh.run();
            say(msg, "Product added.", true);
        });

        RoundButton editBtn = new RoundButton("EDIT PRODUCT", RoundButton.OUTLINE);
        editBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { say(msg, "Select a product to edit.", false); return; }
            int modelRow = table.convertRowIndexToModel(row);
            int productId = ((Number) model.getValueAt(modelRow, 1)).intValue();
            Product p = findProduct(productId);
            if (p == null) { say(msg, "Product could not be found.", false); return; }
            Object[] v = productForm("Edit Product #" + p.id, p);
            if (v == null) return;
            p.name = (String) v[0];
            p.category = (String) v[1];
            p.price = (Double) v[2];
            p.stock[0] = (Integer) v[3];
            p.stock[1] = (Integer) v[4];
            p.imagePath = (String) v[5];
            p.colors = (String) v[6];
            p.availability = (String) v[7];
            p.colorImages = (String) v[8];
            Database.updateProduct(p.id, p.name, p.category, p.price, p.stock[0], p.stock[1],
                    p.imagePath, p.colors, p.availability, p.colorImages);
            refresh.run();
            say(msg, "Product updated.", true);
        });

        RoundButton delBtn = new RoundButton("ARCHIVE PRODUCT", RoundButton.DANGERV);
        delBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { say(msg, "Select a product to delete.", false); return; }
            int modelRow = table.convertRowIndexToModel(row);
            int productId = ((Number) model.getValueAt(modelRow, 1)).intValue();
            Product p = findProduct(productId);
            if (p == null) return;
            int ok = JOptionPane.showConfirmDialog(this,
                    "Archive \"" + p.name + "\"? It will be hidden from the active catalog but kept in SQL.",
                    "Archive Product", JOptionPane.YES_NO_OPTION);
            if (ok == JOptionPane.YES_OPTION) {
                catalog.remove(p);
                for (ArrayList<CartItem> cart : carts.values()) cart.removeIf(it -> it.product == p);
                Database.deleteProduct(p.id);
                refresh.run();
                say(msg, "Product archived. The SQL record was kept.", true);
            }
        });

        page.add(scroll(table), BorderLayout.CENTER);
        page.add(actionBar(flow(addBtn, editBtn, delBtn), msg), BorderLayout.SOUTH);
        refresh.run();
        return page;
    }

    /** Add/edit form. Returns {name, category, price, kinaweStock, opolStock, imagePath} or null if cancelled. */
    private Object[] productForm(String title, Product p) {
        JTextField n = dlgField(p == null ? "" : p.name, 20);
        JComboBox<String> c = new JComboBox<>(CATEGORIES);
        if (p != null) c.setSelectedItem(p.category);
        JTextField pr = dlgField(p == null ? "" : String.valueOf(p.price), 10);
        JTextField colors = dlgField(p == null ? "Black, White, Beige" : p.colors, 20);
        JComboBox<String> availability = new JComboBox<>(new String[]{"Available", "Unavailable"});
        availability.setSelectedItem(p == null ? "Available" : p.availability);
        JSpinner sk = new JSpinner(new SpinnerNumberModel(p == null ? 0 : p.stock[0], 0, 9999, 1));
        JSpinner so = new JSpinner(new SpinnerNumberModel(p == null ? 0 : p.stock[1], 0, 9999, 1));

        final String[] selectedImage = {p == null ? null : p.imagePath};
        JLabel photoPreview = new JLabel();
        photoPreview.setPreferredSize(new Dimension(110, 110));
        photoPreview.setHorizontalAlignment(SwingConstants.CENTER);
        photoPreview.setVerticalAlignment(SwingConstants.CENTER);
        photoPreview.setBorder(BorderFactory.createLineBorder(LINE));
        photoPreview.setOpaque(true);
        photoPreview.setBackground(SAND);
        updatePhotoPreview(photoPreview, selectedImage[0], 100, 100);

        JButton choosePhoto = new JButton(p == null ? "Choose Photo" : "Change Photo");
        choosePhoto.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Choose Product Photo");
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    "Image files (*.jpg, *.jpeg, *.png)", "jpg", "jpeg", "png"));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    selectedImage[0] = saveProductImage(chooser.getSelectedFile(), p == null ? 0 : p.id);
                    updatePhotoPreview(photoPreview, selectedImage[0], 100, 100);
                    choosePhoto.setText("Change Photo");
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Could not save the selected photo.\n" + ex.getMessage(),
                            "Photo Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        Map<String, String> colorPhotos = decodeColorImages(p == null ? "" : p.colorImages);
        JButton manageColorPhotos = new JButton("Set Photo for Each Color");
        manageColorPhotos.addActionListener(e -> {
            String[] opts = colors.getText().split(",");
            for (String raw : opts) {
                String colorName = raw.trim(); if (colorName.isEmpty()) continue;
                int choice = JOptionPane.showConfirmDialog(this,
                    "Choose a photo for color: " + colorName + "?\nChoose Yes to select/update, No to keep current, Cancel to stop.",
                    "Color Photo", JOptionPane.YES_NO_CANCEL_OPTION);
                if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) break;
                if (choice != JOptionPane.YES_OPTION) continue;
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("Choose " + colorName + " Product Photo");
                chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Image files (*.jpg, *.jpeg, *.png)", "jpg", "jpeg", "png"));
                if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    try { colorPhotos.put(colorName, saveProductImage(chooser.getSelectedFile(), p == null ? 0 : p.id)); }
                    catch (IOException ex) { JOptionPane.showMessageDialog(this, "Could not save photo: " + ex.getMessage()); }
                }
            }
            manageColorPhotos.setText("Color Photos Set: " + colorPhotos.size());
        });
        manageColorPhotos.setToolTipText("Upload a separate real product photo for each listed color");
        JPanel photoPanel = new JPanel(new GridLayout(3, 1, 4, 4));
        photoPanel.setOpaque(false);
        photoPanel.add(photoPreview);
        photoPanel.add(choosePhoto);
        photoPanel.add(manageColorPhotos);
        JLabel err = lbl(" ", sans(Font.PLAIN, 12), DANGER);

        while (true) {
            Object[] form = {"Product name", n, "Category", c, "Color(s) (comma separated)", colors,
                    "Availability", availability, "Price (₱)", pr,
                    "Stock at " + BRANCHES[0], sk, "Stock at " + BRANCHES[1], so,
                    "Main product photo", photoPanel, err};
            int ok = JOptionPane.showConfirmDialog(this, form, title,
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (ok != JOptionPane.OK_OPTION) return null;

            String nm = cleanText(n.getText(), 150);
            if (nm.isEmpty()) { err.setText("Product name is required."); continue; }
            if (nm.length() > 150) { err.setText("Product name must be 150 characters or fewer."); continue; }
            if (!nm.matches("[A-Za-z][A-Za-z &'().,_/-]*")) { err.setText("Product name must contain letters only; numbers are not allowed."); continue; }
            double v;
            try {
                v = Double.parseDouble(pr.getText().trim());
            } catch (NumberFormatException ex) {
                err.setText("Price must be a number.");
                continue;
            }
            if (!Double.isFinite(v) || v <= 0 || v > 99999999.99) { err.setText("Price must be greater than 0 and within the allowed range."); continue; }
            if (selectedImage[0] == null || selectedImage[0].isBlank()) {
                err.setText("Please choose a product photo.");
                continue;
            }
            String colorText = cleanText(colors.getText(), 500);
            if (colorText.isEmpty()) { err.setText("Please enter at least one color."); continue; }
            if (!colorText.matches("[A-Za-z ,&()/_-]{1,500}")) { err.setText("Colors must contain letters and supported separators only; numbers are not allowed."); continue; }
            return new Object[]{nm, (String) c.getSelectedItem(), v,
                    (Integer) sk.getValue(), (Integer) so.getValue(), selectedImage[0],
                    colorText, (String) availability.getSelectedItem(), encodeColorImages(colorPhotos)};
        }
    }

    private String saveProductImage(File source, int productId) throws IOException {
        validateImageFile(source);
        String original = source.getName();
        int dot = original.lastIndexOf('.');
        String ext = dot >= 0 ? original.substring(dot).toLowerCase() : "";
        if (!(ext.equals(".jpg") || ext.equals(".jpeg") || ext.equals(".png"))) {
            throw new IOException("Please select a JPG, JPEG, or PNG image.");
        }
        Path dir = Paths.get(System.getProperty("user.home"), ".stylehub", "product-images");
        Files.createDirectories(dir);
        String safeBase = original.substring(0, dot >= 0 ? dot : original.length())
                .replaceAll("[^a-zA-Z0-9_-]", "_");
        if (safeBase.length() > 60) safeBase = safeBase.substring(0, 60);
        String filename = (productId > 0 ? "product_" + productId + "_" : "new_")
                + safeBase + "_" + System.currentTimeMillis() + ext;
        Path target = dir.resolve(filename);
        Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
        return target.toAbsolutePath().toString();
    }

    private static void validateImageFile(File file) throws IOException {
        if (file == null || !file.isFile()) throw new IOException("Please choose a valid image file.");
        final long MAX_IMAGE_BYTES = 5L * 1024L * 1024L;
        if (file.length() <= 0 || file.length() > MAX_IMAGE_BYTES) {
            throw new IOException("Image must be larger than 0 bytes and no bigger than 5 MB.");
        }
        String name = file.getName().toLowerCase();
        if (!(name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png"))) {
            throw new IOException("Only JPG, JPEG, and PNG images are accepted.");
        }
        BufferedImage image = ImageIO.read(file);
        if (image == null) throw new IOException("The selected file is not a valid readable image.");
        if (image.getWidth() < 20 || image.getHeight() < 20) throw new IOException("Image is too small.");
        if (image.getWidth() > 6000 || image.getHeight() > 6000) throw new IOException("Image dimensions cannot exceed 6000 x 6000 pixels.");
    }

    private void updatePhotoPreview(JLabel label, String path, int w, int h) {
        if (path == null || path.isBlank()) {
            label.setIcon(null);
            label.setText("No Photo");
            return;
        }
        ImageIcon icon = new ImageIcon(path);
        if (icon.getIconWidth() <= 0) {
            label.setIcon(null);
            label.setText("Photo unavailable");
            return;
        }
        Image img = icon.getImage();
        double scale = Math.min((double) w / icon.getIconWidth(), (double) h / icon.getIconHeight());
        int nw = Math.max(1, (int) (icon.getIconWidth() * scale));
        int nh = Math.max(1, (int) (icon.getIconHeight() * scale));
        label.setText(null);
        label.setIcon(new ImageIcon(img.getScaledInstance(nw, nh, Image.SCALE_SMOOTH)));
    }

    /**
     * Renders the actual product image in the Photo column.
     * The table stores the Product object instead of an ImageIcon/String,
     * which prevents values such as "javax.swing.ImageIcon@..." from
     * appearing in the table.
     */
    private void installProductPhotoRenderer(JTable table, int column, int size) {
        table.setRowHeight(Math.max(table.getRowHeight(), size + 12));
        table.getColumnModel().getColumn(column).setPreferredWidth(size + 28);
        table.getColumnModel().getColumn(column).setMinWidth(size + 20);
        table.getColumnModel().getColumn(column).setMaxWidth(size + 70);

        table.getColumnModel().getColumn(column).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean selected, boolean focused, int row, int col) {
                JLabel label = new JLabel();
                label.setHorizontalAlignment(SwingConstants.CENTER);
                label.setVerticalAlignment(SwingConstants.CENTER);
                label.setOpaque(true);
                label.setBackground(selected ? new Color(240, 230, 213) : Color.WHITE);
                label.setBorder(new EmptyBorder(4, 6, 4, 6));

                if (value instanceof CartItem) {
                    CartItem item = (CartItem) value;
                    ImageIcon icon = productIcon(item.product, item.color, size, size);
                    if (icon != null) label.setIcon(icon);
                    else { label.setText("No Photo"); label.setFont(sans(Font.PLAIN, 10)); label.setForeground(MUTED); }
                } else if (value instanceof Product) {
                    ImageIcon icon = productIcon((Product) value, size, size);
                    if (icon != null) {
                        label.setIcon(icon);
                    } else {
                        label.setText("No Photo");
                        label.setFont(sans(Font.PLAIN, 10));
                        label.setForeground(MUTED);
                    }
                } else {
                    label.setText("No Photo");
                    label.setFont(sans(Font.PLAIN, 10));
                    label.setForeground(MUTED);
                }
                return label;
            }
        });
    }

    private ImageIcon productIcon(Product p, int w, int h) { return productIcon(p, null, w, h); }

    private ImageIcon productIcon(Product p, String color, int w, int h) {
        if (p == null) return null;

        ImageIcon icon = null;

        // Prefer a color-specific image when one has been uploaded by the admin.
        String path = p.imagePath == null ? "" : p.imagePath.trim();
        if (color != null && !color.isBlank()) {
            String specific = decodeColorImages(p.colorImages).get(color);
            if (specific != null && new File(specific).isFile()) path = specific;
        }
        if (!path.isBlank() && !path.startsWith("javax.swing.") && !path.startsWith("java.awt.")) {
            File imageFile = new File(path);
            if (imageFile.isFile()) {
                ImageIcon saved = new ImageIcon(path);
                if (saved.getIconWidth() > 0 && saved.getIconHeight() > 0) icon = saved;
            }
        }

        // 2) If an old database row has a broken path, use the same bundled
        // product image used by the Products screen. This keeps Products,
        // Inventory, Cart and Orders visually consistent.
        if (icon == null) icon = bundledProductIcon(p.name);
        if (icon == null) return null;

        double scale = Math.min((double) w / icon.getIconWidth(), (double) h / icon.getIconHeight());
        int nw = Math.max(1, (int) (icon.getIconWidth() * scale));
        int nh = Math.max(1, (int) (icon.getIconHeight() * scale));
        return new ImageIcon(icon.getImage().getScaledInstance(nw, nh, Image.SCALE_SMOOTH));
    }

    private ImageIcon bundledProductIcon(String productName) {
        if (productName == null || productName.isBlank()) return null;
        String key = productName.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");

        // Exact product-name matches first.
        String[] candidates = {
            key + ".png",
            key.replace("_tee", "_white_tee") + ".png"
        };
        for (String file : candidates) {
            java.net.URL url = getClass().getResource("/com/mycompany/mavenproject7/images/" + file);
            if (url != null) return new ImageIcon(url);
        }

        // Common legacy/product-name aliases.
        Map<String, String> aliases = new HashMap<>();
        aliases.put("classic_tee", "classic_white_tee.png");
        aliases.put("classic_white_t_shirt", "classic_white_tee.png");
        aliases.put("white_shirt", "classic_white_tee.png");
        aliases.put("white_tee", "classic_white_tee.png");
        aliases.put("graphic_tee", "oversized_graphic_tee.png");
        aliases.put("oversized_tee", "oversized_graphic_tee.png");
        aliases.put("trousers", "bottoms.png");
        aliases.put("skirt", "pleated_midi_skirt.png");
        aliases.put("jeans", "slim_fit_jeans.png");
        aliases.put("jacket", "denim_jacket.png");
        aliases.put("sweatshirt", "hooded_sweatshirt.png");
        aliases.put("dress", "dresses.png");
        String file = aliases.get(key);
        if (file != null) {
            java.net.URL url = getClass().getResource("/com/mycompany/mavenproject7/images/" + file);
            if (url != null) return new ImageIcon(url);
        }
        return null;
    }

    // ----------------------- INVENTORY (per branch) -----------------------
    private JPanel admInventory() {
        JPanel page = shell("Inventory", "Manage stock for each branch, or transfer items between them.");

        Segmented branchSel = new Segmented(BRANCHES, 0);
        DefaultTableModel model = newModel("Photo", "ID", "Product", "Category", "Color", "Availability", "Price", "In Stock", "Other Branch");
        JTable table = newTable(model);
        installProductPhotoRenderer(table, 0, 42);
        JLabel msg = new JLabel(" ");

        Runnable refresh = () -> {
            int b = branchSel.getSelected();
            // Do not reset the model columns here. setColumnIdentifiers() fires a
            // structure change and replaces the JTable columns, which removes the
            // custom product-photo renderer. The renderer installed above must stay
            // attached so Inventory displays the same actual photo as Products.
            model.setRowCount(0);
            for (Product p : catalog) {
                model.addRow(new Object[]{p, p.id, p.name, p.category, firstColor(p), p.availability, money(p.price), p.stock[b], p.stock[1 - b]});
            }
        };
        branchSel.setOnChange(i -> { refresh.run(); msg.setText(" "); });

        RoundButton restockBtn = new RoundButton("RESTOCK (+)", RoundButton.PRIMARY);
        restockBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { say(msg, "Select a product first.", false); return; }
            int b = branchSel.getSelected();
            Product p = catalog.get(row);
            Integer q = askQty("Restock " + BRANCH_SHORT[b], "Units to add to " + p.name + ":", 10, 1, 9999);
            if (q == null) return;
            p.stock[b] += q;
            Database.setStock(p.id, b, p.stock[b]);
            refresh.run();
            table.setRowSelectionInterval(row, row);
            say(msg, q + " units added to " + BRANCHES[b] + ".", true);
        });

        RoundButton setBtn = new RoundButton("SET STOCK", RoundButton.OUTLINE);
        setBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { say(msg, "Select a product first.", false); return; }
            int b = branchSel.getSelected();
            Product p = catalog.get(row);
            Integer q = askQty("Set stock", "Stock of " + p.name + " at " + BRANCH_SHORT[b] + ":",
                    p.stock[b], 0, 9999);
            if (q == null) return;
            p.stock[b] = q;
            Database.setStock(p.id, b, q);
            refresh.run();
            table.setRowSelectionInterval(row, row);
            say(msg, "Stock updated.", true);
        });

        RoundButton transferBtn = new RoundButton("TRANSFER TO OTHER BRANCH", RoundButton.GOLDV);
        transferBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { say(msg, "Select a product first.", false); return; }
            int b = branchSel.getSelected();
            Product p = catalog.get(row);
            if (p.stock[b] <= 0) {
                say(msg, "No stock available at " + BRANCH_SHORT[b] + " to transfer.", false);
                return;
            }
            Integer q = askQty("Transfer stock",
                    "Move how many " + p.name + " from " + BRANCH_SHORT[b] + " to " + BRANCH_SHORT[1 - b] + "?",
                    1, 1, p.stock[b]);
            if (q == null) return;
            p.stock[b] -= q;
            p.stock[1 - b] += q;
            Database.setStock(p.id, b, p.stock[b]);
            Database.setStock(p.id, 1 - b, p.stock[1 - b]);
            refresh.run();
            table.setRowSelectionInterval(row, row);
            say(msg, q + " units moved from " + BRANCH_SHORT[b] + " to " + BRANCH_SHORT[1 - b] + ".", true);
        });

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        top.setOpaque(false);
        top.add(capLabel("Viewing"));
        top.add(branchSel);

        JPanel center = new JPanel(new BorderLayout(0, 14));
        center.setOpaque(false);
        center.add(top, BorderLayout.NORTH);
        center.add(scroll(table), BorderLayout.CENTER);

        page.add(center, BorderLayout.CENTER);
        page.add(actionBar(flow(restockBtn, setBtn, transferBtn), msg), BorderLayout.SOUTH);
        refresh.run();
        return page;
    }

    // ------------------------- ORDERS -------------------------
    private JPanel admOrders() {
        JPanel page = shell("Orders", "Every customer order, filtered by branch. Update the status as they progress.");

        Segmented filter = new Segmented(new String[]{"All branches", "Kinawe", "Opol"}, 0);
        JComboBox<String> dateFilter = dateFilterBox();
        DefaultTableModel model = newModel("PHOTO", "Order #", "Customer", "Branch", "Method", "Date",
                "Items", "Total", "Status");
        JTable table = newTable(model);
        ArrayList<Order> shown = new ArrayList<>();
        JLabel msg = new JLabel(" ");

        Runnable refresh = () -> {
            model.setRowCount(0);
            shown.clear();
            int f = filter.getSelected(); // 0 = all, 1 = Kinawe, 2 = Opol
            for (Order o : orders) {
                if (f != 0 && o.branch != f - 1) continue;
                if (!matchesDateFilter(o.date, (String) dateFilter.getSelectedItem())) continue;
                shown.add(o);
                String[] acc = customers.get(o.owner);
                model.addRow(new Object[]{o.items.isEmpty() ? null : o.items.get(0).product, "#" + o.id,
                        acc != null ? acc[2] : o.owner, BRANCH_SHORT[o.branch], o.method, o.date, o.itemCount(),
                        money(o.total()), o.status});
            }
            if (shown.isEmpty()) {
                msg.setFont(sans(Font.PLAIN, 12));
                msg.setForeground(MUTED);
                msg.setText("No orders to show.");
            } else {
                msg.setText(" ");
            }
        };
        filter.setOnChange(i -> refresh.run());
        dateFilter.addActionListener(e -> {
            adminDateFilter = (String) dateFilter.getSelectedItem();
            refresh.run();
        });

        RoundButton viewBtn = new RoundButton("VIEW DETAILS", RoundButton.PRIMARY);
        viewBtn.addActionListener(e -> {
            Order o = pick(table, shown, msg);
            if (o != null) showOrderDetails(o);
        });

        RoundButton statusBtn = new RoundButton("UPDATE STATUS", RoundButton.OUTLINE);
        statusBtn.addActionListener(e -> {
            Order o = pick(table, shown, msg);
            if (o == null) return;
            if (o.status.equals("Cancelled")) {
                say(msg, "Cancelled orders can't be changed.", false);
                return;
            }
            Object choice = JOptionPane.showInputDialog(this, "New status for order #" + o.id + ":",
                    "Update Status", JOptionPane.QUESTION_MESSAGE, null, STATUSES, o.status);
            if (choice == null) return;
            String ns = (String) choice;
            if (ns.equals("Cancelled")) restoreStock(o);
            o.status = ns;
            Database.updateOrderStatus(o.id, ns);
            refresh.run();
            say(msg, "Order #" + o.id + " is now " + ns + ".", true);
        });

        RoundButton delBtn = new RoundButton("ARCHIVE ORDER", RoundButton.DANGERV);
        delBtn.addActionListener(e -> {
            Order o = pick(table, shown, msg);
            if (o == null) return;
            int ok = JOptionPane.showConfirmDialog(this, "Archive order #" + o.id + "? It will stay in SQL and can be restored from Archive."
                    + (o.live() ? "\nIts items will be returned to " + BRANCH_SHORT[o.branch] + " stock." : ""),
                    "Archive Order", JOptionPane.YES_NO_OPTION);
            if (ok == JOptionPane.YES_OPTION) {
                removeOrder(o);
                refresh.run();
                say(msg, "Order archived. The SQL record was kept.", true);
            }
        });

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        top.setOpaque(false);
        top.add(capLabel("Branch"));
        top.add(filter);
        top.add(capLabel("Date range"));
        top.add(dateFilter);

        JPanel center = new JPanel(new BorderLayout(0, 14));
        center.setOpaque(false);
        center.add(top, BorderLayout.NORTH);
        center.add(scroll(table), BorderLayout.CENTER);
        installProductPhotoRenderer(table, 0, 58);

        page.add(center, BorderLayout.CENTER);
        page.add(actionBar(flow(viewBtn, statusBtn, delBtn), msg), BorderLayout.SOUTH);
        refresh.run();
        return page;
    }

    // ------------------------- REPORTS -------------------------
    private String reportDate(String text) { return text == null ? "" : text.trim(); }

    private String[] reportRange(JComboBox<String> preset, JTextField from, JTextField to) {
        String f=(String)preset.getSelectedItem();
        LocalDate now=LocalDate.now(), a=null, b=null;
        if("Today".equals(f)){a=now;b=now;} else if("Yesterday".equals(f)){a=now.minusDays(1);b=a;}
        else if("This Week".equals(f)){a=now.with(DayOfWeek.MONDAY);b=a.plusDays(6);} else if("This Month".equals(f)){a=now.withDayOfMonth(1);b=now.withDayOfMonth(now.lengthOfMonth());}
        else if("Last Month".equals(f)){LocalDate lm=now.minusMonths(1);a=lm.withDayOfMonth(1);b=lm.withDayOfMonth(lm.lengthOfMonth());}
        else if("This Year".equals(f)){a=now.withDayOfYear(1);b=now.withDayOfYear(now.lengthOfYear());}
        else if("Custom Range".equals(f)){a=parseReportDate(from.getText());b=parseReportDate(to.getText());}
        return new String[]{a==null?"":a.toString(),b==null?"":b.toString()};
    }
    private LocalDate parseReportDate(String s){try{return LocalDate.parse(s.trim());}catch(Exception e){return null;}}

    private JPanel reportFilterBar(JComboBox<String> range, JTextField from, JTextField to,
                                   JComboBox<String> category, Runnable refresh){
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);

        JPanel filterCard = new JPanel(new GridBagLayout());
        filterCard.setBackground(Color.WHITE);
        filterCard.setBorder(new CompoundBorder(new LineBorder(LINE), new EmptyBorder(14,16,14,16)));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5,7,5,7);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;

        JLabel filterTitle = lbl("FILTER REPORT", tracked(sans(Font.BOLD,11), .12f), MUTED);
        g.gridx=0; g.gridy=0; g.gridwidth=6; g.weightx=1;
        filterCard.add(filterTitle,g);

        range.setPreferredSize(new Dimension(155,38));
        from.setPreferredSize(new Dimension(125,38));
        to.setPreferredSize(new Dimension(125,38));
        category.setPreferredSize(new Dimension(180,38));
        category.setFont(sans(Font.PLAIN,14));

        g.gridy=1; g.gridwidth=1; g.weightx=0;
        g.gridx=0; filterCard.add(capLabel("DATE RANGE"),g);
        g.gridx=1; filterCard.add(range,g);
        g.gridx=2; filterCard.add(capLabel("FROM"),g);
        g.gridx=3; filterCard.add(from,g);
        g.gridx=4; filterCard.add(capLabel("TO"),g);
        g.gridx=5; filterCard.add(to,g);

        g.gridy=2; g.gridx=0; filterCard.add(capLabel("CATEGORY"),g);
        g.gridx=1; g.gridwidth=2; filterCard.add(category,g);
        g.gridwidth=1;

        RoundButton reset = new RoundButton("RESET", RoundButton.OUTLINE);
        reset.setPreferredSize(new Dimension(95,38));
        g.gridx=3; filterCard.add(reset,g);

        JLabel hint = lbl("Select a category to filter the table.", sans(Font.PLAIN,12), MUTED);
        g.gridx=4; g.gridwidth=2; g.weightx=1; filterCard.add(hint,g);

        wrap.add(filterCard, BorderLayout.CENTER);

        range.addActionListener(e -> {
            boolean custom = "Custom Range".equals(range.getSelectedItem());
            from.setEnabled(custom); to.setEnabled(custom);
            if (!custom) refresh.run();
        });
        category.addActionListener(e -> refresh.run());
        reset.addActionListener(e -> {
            range.setSelectedItem("This Month");
            from.setText(LocalDate.now().withDayOfMonth(1).toString());
            to.setText(LocalDate.now().toString());
            category.setSelectedItem("All Categories");
            from.setEnabled(false); to.setEnabled(false);
            refresh.run();
        });
        return wrap;
    }

    private JPanel reportKpiCard(String label, JLabel value) {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setBackground(Color.WHITE);
        card.setBorder(new CompoundBorder(new LineBorder(LINE), new EmptyBorder(13, 16, 13, 16)));
        card.add(capLabel(label), BorderLayout.NORTH);
        value.setFont(sans(Font.BOLD, 21));
        value.setForeground(INK);
        card.add(value, BorderLayout.CENTER);
        return card;
    }

    private JPanel reportKpiRow(JLabel a, JLabel b, JLabel c, JLabel d, String l1, String l2, String l3, String l4) {
        JPanel row = new JPanel(new GridLayout(1,4,12,0));
        row.setOpaque(false);
        row.add(reportKpiCard(l1,a));
        row.add(reportKpiCard(l2,b));
        row.add(reportKpiCard(l3,c));
        row.add(reportKpiCard(l4,d));
        return row;
    }

    private void exportReport(String title, String[] headers, ArrayList<Object[]> rows, int[] fields, int moneyField, JLabel msg){
        JFileChooser fc=new JFileChooser();fc.setDialogTitle("Export "+title);fc.setSelectedFile(new File(title.toLowerCase().replace(" ","_")+".csv"));
        if(fc.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        StringBuilder out=new StringBuilder();for(int i=0;i<headers.length;i++){if(i>0)out.append(',');out.append(csv(headers[i]));}out.append('\n');
        double total=0;for(Object[] r:rows){for(int i=0;i<fields.length;i++){if(i>0)out.append(',');Object v=r[fields[i]];if(moneyField==fields[i]&&v instanceof Number){out.append(String.format(java.util.Locale.US,"%.2f",((Number)v).doubleValue()));}else out.append(csv(String.valueOf(v==null?"":v)));}out.append('\n');if(moneyField>=0&&r[moneyField] instanceof Number)total+=((Number)r[moneyField]).doubleValue();}
        try{Files.writeString(fc.getSelectedFile().toPath(),out.toString());say(msg,"Exported successfully: "+fc.getSelectedFile().getName(),true);}catch(IOException ex){say(msg,"Could not export report: "+ex.getMessage(),false);}
    }
    private static String csv(String s){String x=s==null?"":s.replace("\"","\"\"");return "\""+x+"\"";}

    private JPanel admSalesReport(){
        JPanel page=shell("Sales Report","View sales transactions by date and product category.");
        JComboBox<String> range=new JComboBox<>(new String[]{"All Time","Today","Yesterday","This Week","This Month","Last Month","This Year","Custom Range"});
        range.setSelectedItem("This Month");
        JTextField from=dlgField(LocalDate.now().withDayOfMonth(1).toString(),10),
                   to=dlgField(LocalDate.now().toString(),10);
        from.setEnabled(false); to.setEnabled(false);
        JComboBox<String> cat=new JComboBox<>(new String[]{"All Categories","Tops","Bottoms","Outerwear","Dresses","Accessories"});

        DefaultTableModel model=newModel("Order #","Date","Customer","Branch","Product","Category","Qty","Unit Price","Subtotal","Status");
        JTable table=newTable(model);
        styleReportTable(table,new int[]{90,125,150,125,235,135,70,115,125,120});
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);

        JLabel totalSales=lbl("₱0.00",sans(Font.BOLD,21),INK),
               transactions=lbl("0",sans(Font.BOLD,21),INK),
               units=lbl("0",sans(Font.BOLD,21),INK),
               average=lbl("₱0.00",sans(Font.BOLD,21),INK);
        JLabel total=lbl("",sans(Font.BOLD,14),INK),msg=new JLabel(" ");
        ArrayList<Object[]> rows=new ArrayList<>();

        Runnable refresh=()->{
            model.setRowCount(0); rows.clear();
            String[] rr=reportRange(range,from,to);
            // Category is intentionally passed directly to the database query.
            rows.addAll(Database.loadSalesReport(rr[0],rr[1],"",(String)cat.getSelectedItem()));
            double sum=0; int qty=0;
            for(Object[] r:rows){
                model.addRow(new Object[]{"#"+r[0],r[1],r[2],r[3],r[4],r[5],r[6],
                        money(((Number)r[7]).doubleValue()),money(((Number)r[8]).doubleValue()),r[9]});
                sum+=((Number)r[8]).doubleValue(); qty+=((Number)r[6]).intValue();
            }
            totalSales.setText(money(sum));
            transactions.setText(String.valueOf(rows.size()));
            units.setText(String.valueOf(qty));
            average.setText(rows.isEmpty()?money(0):money(sum/rows.size()));
            total.setText("TOTAL SALES  "+money(sum)+"    •    "+rows.size()+" transaction(s)");
        };

        RoundButton export=new RoundButton("EXPORT",RoundButton.OUTLINE);
        export.setPreferredSize(new Dimension(105,38));
        export.addActionListener(e->exportReport("sales_report",
                new String[]{"Order #","Date","Customer","Branch","Product","Category","Qty","Unit Price","Subtotal","Status"},
                rows,new int[]{0,1,2,3,4,5,6,7,8,9},8,msg));

        JPanel filter=reportFilterBar(range,from,to,cat,refresh);
        JPanel titleRow=new JPanel(new BorderLayout()); titleRow.setOpaque(false);
        titleRow.add(lbl("SALES TRANSACTIONS",tracked(sans(Font.BOLD,11),.12f),MUTED),BorderLayout.WEST);
        titleRow.add(export,BorderLayout.EAST);

        JPanel center=new JPanel(new BorderLayout(0,12)); center.setOpaque(false);
        JPanel reportTop = new JPanel(new BorderLayout(0,12)); reportTop.setOpaque(false);
        reportTop.add(reportKpiRow(totalSales,transactions,units,average,"TOTAL SALES","TRANSACTIONS","UNITS SOLD","AVERAGE SALE"),BorderLayout.NORTH);
        reportTop.add(filter,BorderLayout.CENTER);
        center.add(reportTop,BorderLayout.NORTH);
        center.add(titleRow,BorderLayout.SOUTH);

        page.add(center,BorderLayout.NORTH);
        page.add(new JScrollPane(table),BorderLayout.CENTER);
        JPanel bottom=new JPanel(new BorderLayout(0,8)); bottom.setOpaque(false);
        bottom.add(total,BorderLayout.EAST); bottom.add(msg,BorderLayout.CENTER);
        bottom.setBorder(new EmptyBorder(8,0,0,0)); page.add(bottom,BorderLayout.SOUTH);
        refresh.run(); return page;
    }

    private JPanel admOrderReport(){
        JPanel page=shell("Order Report","View customer orders by date, category, branch and status.");
        JComboBox<String> range=new JComboBox<>(new String[]{"All Time","Today","Yesterday","This Week","This Month","Last Month","This Year","Custom Range"});
        range.setSelectedItem("This Month");
        JTextField from=dlgField(LocalDate.now().withDayOfMonth(1).toString(),10),
                   to=dlgField(LocalDate.now().toString(),10);
        from.setEnabled(false); to.setEnabled(false);
        JComboBox<String> cat=new JComboBox<>(new String[]{"All Categories","Tops","Bottoms","Outerwear","Dresses","Accessories"});

        DefaultTableModel model=newModel("Order #","Date","Customer","Branch","Method","Payment","Status","Items","Total","Categories");
        JTable table=newTable(model);
        styleReportTable(table,new int[]{90,125,150,125,120,120,120,75,130,205});
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);

        JLabel orderCount=lbl("0",sans(Font.BOLD,21),INK),
               revenue=lbl("₱0.00",sans(Font.BOLD,21),INK),
               pending=lbl("0",sans(Font.BOLD,21),INK),
               delivered=lbl("0",sans(Font.BOLD,21),INK);
        JLabel total=lbl("",sans(Font.BOLD,14),INK),msg=new JLabel(" ");
        ArrayList<Object[]> rows=new ArrayList<>();

        Runnable refresh=()->{
            model.setRowCount(0); rows.clear();
            String[] rr=reportRange(range,from,to);
            rows.addAll(Database.loadOrderReport(rr[0],rr[1],"",(String)cat.getSelectedItem()));
            double sum=0; int p=0,d=0;
            for(Object[] r:rows){
                model.addRow(new Object[]{"#"+r[0],r[1],r[2],r[3],r[4],r[5],r[6],r[7],
                        money(((Number)r[8]).doubleValue()),r[9]});
                sum+=((Number)r[8]).doubleValue();
                if("Pending".equalsIgnoreCase(String.valueOf(r[6])))p++;
                if("Delivered".equalsIgnoreCase(String.valueOf(r[6])))d++;
            }
            orderCount.setText(String.valueOf(rows.size())); revenue.setText(money(sum));
            pending.setText(String.valueOf(p)); delivered.setText(String.valueOf(d));
            total.setText("TOTAL ORDERS  "+rows.size()+"    •    TOTAL AMOUNT  "+money(sum));
        };

        RoundButton export=new RoundButton("EXPORT",RoundButton.OUTLINE);
        export.setPreferredSize(new Dimension(105,38));
        export.addActionListener(e->exportReport("order_report",
                new String[]{"Order #","Date","Customer","Branch","Method","Payment","Status","Items","Total","Categories"},
                rows,new int[]{0,1,2,3,4,5,6,7,8,9},8,msg));

        JPanel filter=reportFilterBar(range,from,to,cat,refresh);
        JPanel titleRow=new JPanel(new BorderLayout()); titleRow.setOpaque(false);
        titleRow.add(lbl("ORDER TRANSACTIONS",tracked(sans(Font.BOLD,11),.12f),MUTED),BorderLayout.WEST);
        titleRow.add(export,BorderLayout.EAST);

        JPanel center=new JPanel(new BorderLayout(0,12)); center.setOpaque(false);
        JPanel reportTop = new JPanel(new BorderLayout(0,12)); reportTop.setOpaque(false);
        reportTop.add(reportKpiRow(orderCount,revenue,pending,delivered,"TOTAL ORDERS","ORDER VALUE","PENDING","DELIVERED"),BorderLayout.NORTH);
        reportTop.add(filter,BorderLayout.CENTER);
        center.add(reportTop,BorderLayout.NORTH);
        center.add(titleRow,BorderLayout.SOUTH);

        page.add(center,BorderLayout.NORTH);
        page.add(new JScrollPane(table),BorderLayout.CENTER);
        JPanel bottom=new JPanel(new BorderLayout(0,8)); bottom.setOpaque(false);
        bottom.add(total,BorderLayout.EAST); bottom.add(msg,BorderLayout.CENTER);
        bottom.setBorder(new EmptyBorder(8,0,0,0)); page.add(bottom,BorderLayout.SOUTH);
        refresh.run(); return page;
    }

    private JPanel admArchive(){
        JPanel page=shell("Archive","Archived records are hidden from the active system but remain stored in MySQL. Restore them when needed.");
        JTabbedPane tabs=new JTabbedPane();
        tabs.addTab("Products",archiveProducts());tabs.addTab("Orders",archiveOrders());tabs.addTab("Customers",archiveCustomers());
        page.add(tabs,BorderLayout.CENTER);return page;
    }
    private JPanel archiveProducts(){JPanel p=new JPanel(new BorderLayout(0,10));p.setOpaque(false);DefaultTableModel m=newModel("ID","Product","Category","Price");JTable t=newTable(m);JLabel msg=new JLabel(" ");Runnable r=()->{m.setRowCount(0);for(Object[] x:Database.loadArchivedProducts())m.addRow(new Object[]{x[0],x[1],x[2],money(((Number)x[3]).doubleValue())});};RoundButton restore=new RoundButton("RESTORE",RoundButton.PRIMARY);restore.addActionListener(e->{int i=t.getSelectedRow();if(i<0){say(msg,"Select a product first.",false);return;}Database.restoreProduct(((Number)m.getValueAt(i,0)).intValue());r.run();refreshDatabaseState();say(msg,"Product restored.",true);});p.add(scroll(t),BorderLayout.CENTER);p.add(actionBar(flow(restore),msg),BorderLayout.SOUTH);r.run();return p;}
    private JPanel archiveOrders(){JPanel p=new JPanel(new BorderLayout(0,10));p.setOpaque(false);DefaultTableModel m=newModel("Order #","Customer","Date","Status","Total");JTable t=newTable(m);JLabel msg=new JLabel(" ");Runnable r=()->{m.setRowCount(0);for(Object[] x:Database.loadArchivedOrders())m.addRow(new Object[]{"#"+x[0],x[1],x[2],x[3],money(((Number)x[4]).doubleValue())});};RoundButton restore=new RoundButton("RESTORE",RoundButton.PRIMARY);restore.addActionListener(e->{int i=t.getSelectedRow();if(i<0){say(msg,"Select an order first.",false);return;}Database.restoreOrder(((Number)m.getValueAt(i,0)).intValue());r.run();refreshDatabaseState();say(msg,"Order restored.",true);});p.add(scroll(t),BorderLayout.CENTER);p.add(actionBar(flow(restore),msg),BorderLayout.SOUTH);r.run();return p;}
    private JPanel archiveCustomers(){JPanel p=new JPanel(new BorderLayout(0,10));p.setOpaque(false);DefaultTableModel m=newModel("Username","Full Name","Email");JTable t=newTable(m);JLabel msg=new JLabel(" ");Runnable r=()->{m.setRowCount(0);for(Object[] x:Database.loadArchivedUsers())m.addRow(x);};RoundButton restore=new RoundButton("RESTORE",RoundButton.PRIMARY);restore.addActionListener(e->{int i=t.getSelectedRow();if(i<0){say(msg,"Select a customer first.",false);return;}Database.restoreUser(String.valueOf(m.getValueAt(i,0)));r.run();refreshDatabaseState();say(msg,"Customer restored.",true);});p.add(scroll(t),BorderLayout.CENTER);p.add(actionBar(flow(restore),msg),BorderLayout.SOUTH);r.run();return p;}

    // ------------------------- REVIEWS & FEEDBACK -------------------------
    private JPanel admReviews() {
        JPanel page = shell("Reviews & Feedback", "Customer ratings and comments received after completed orders. Admin can view feedback but cannot rate products.");

        ArrayList<Object[]> allReviews = new ArrayList<>();
        DefaultTableModel model = newModel("Rating", "Product", "Customer", "Order #", "Comment", "Date");
        JTable table = newTable(model);
        JLabel msg = new JLabel(" ");
        JLabel summary = lbl(" ", sans(Font.PLAIN, 12), MUTED);

        Runnable refresh = () -> {
            model.setRowCount(0);
            allReviews.clear();
            allReviews.addAll(Database.loadReviews());
            double sum = 0;
            for (Object[] r : allReviews) {
                int rating = ((Number) r[5]).intValue();
                sum += rating;
                String stars = "★".repeat(Math.max(0, rating)) + "☆".repeat(Math.max(0, 5 - rating));
                String comment = r[6] == null ? "" : String.valueOf(r[6]);
                if (comment.length() > 65) comment = comment.substring(0, 62) + "...";
                model.addRow(new Object[]{stars, r[4], r[3], "#" + r[1], comment, r[7]});
            }
            double avg = allReviews.isEmpty() ? 0 : sum / allReviews.size();
            summary.setText(allReviews.size() + " customer review(s)   •   Average rating: " + String.format("%.1f / 5", avg));
            msg.setText(allReviews.isEmpty() ? "No customer feedback has been submitted yet." : "Select a review and click VIEW FEEDBACK to read the complete comment.");
        };

        RoundButton view = new RoundButton("VIEW FEEDBACK", RoundButton.GOLDV);
        view.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { say(msg, "Select a customer review first.", false); return; }
            Object[] r = allReviews.get(row);
            int rating = ((Number) r[5]).intValue();
            String stars = "★".repeat(Math.max(0, rating)) + "☆".repeat(Math.max(0, 5 - rating));
            JDialog d = new JDialog(this, "StyleHub - Customer Feedback", true);
            d.setSize(560, 430); d.setLocationRelativeTo(this);
            JPanel root = new JPanel(); root.setBackground(IVORY); root.setBorder(new EmptyBorder(26, 30, 24, 30));
            root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
            root.add(lbl("CUSTOMER FEEDBACK", tracked(sans(Font.BOLD, 10), .14f), GOLD));
            root.add(vs(6));
            root.add(lbl(String.valueOf(r[4]), serif(Font.PLAIN, 25), INK));
            root.add(vs(10));
            root.add(lbl(stars + "   " + rating + "/5", sans(Font.BOLD, 18), GOLD));
            root.add(vs(14));
            JPanel meta = whiteCard(14); meta.setLayout(new GridLayout(2, 2, 12, 8));
            meta.add(lbl("CUSTOMER", tracked(sans(Font.BOLD, 9), .10f), MUTED));
            meta.add(lbl("ORDER", tracked(sans(Font.BOLD, 9), .10f), MUTED));
            meta.add(lbl(String.valueOf(r[3]), sans(Font.PLAIN, 13), INK));
            meta.add(lbl("#" + r[1], sans(Font.PLAIN, 13), INK));
            root.add(meta); root.add(vs(14));
            root.add(lbl("COMMENT / FEEDBACK", tracked(sans(Font.BOLD, 9), .10f), MUTED));
            root.add(vs(6));
            JTextArea comment = new JTextArea(String.valueOf(r[6] == null ? "No written comment." : r[6]));
            comment.setLineWrap(true); comment.setWrapStyleWord(true); comment.setEditable(false);
            comment.setFont(sans(Font.PLAIN, 14)); comment.setForeground(INK); comment.setBackground(Color.WHITE);
            comment.setBorder(new EmptyBorder(12,12,12,12));
            JScrollPane cp = new JScrollPane(comment); cp.setBorder(new LineBorder(LINE)); cp.setPreferredSize(new Dimension(490, 110));
            root.add(cp); root.add(vs(14));
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT)); buttons.setOpaque(false);
            RoundButton close = new RoundButton("CLOSE", RoundButton.OUTLINE); close.addActionListener(x -> d.dispose()); buttons.add(close);
            root.add(buttons); d.setContentPane(root); d.setVisible(true);
        });

        JPanel header = new JPanel(new BorderLayout()); header.setOpaque(false);
        header.add(summary, BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)); actions.setOpaque(false);
        RoundButton refreshBtn = new RoundButton("↻", RoundButton.OUTLINE);
        refreshBtn.setToolTipText("Refresh customer feedback"); refreshBtn.addActionListener(e -> refresh.run());
        actions.add(refreshBtn); actions.add(view); header.add(actions, BorderLayout.EAST);
        page.add(header, BorderLayout.NORTH);
        page.add(scroll(table), BorderLayout.CENTER);
        page.add(actionBar(flow(), msg), BorderLayout.SOUTH);
        refresh.run();
        return page;
    }

    // ------------------------- CUSTOMERS -------------------------
    private JPanel admCustomers() {
        JPanel page = shell("Customers", "Registered accounts and how many orders each has placed.");

        DefaultTableModel model = newModel("Username", "Full Name", "Email", "Orders");
        JTable table = newTable(model);
        ArrayList<String> keys = new ArrayList<>();
        JLabel msg = new JLabel(" ");

        Runnable refresh = () -> {
            model.setRowCount(0);
            keys.clear();
            for (Map.Entry<String, String[]> en : customers.entrySet()) {
                int count = 0;
                for (Order o : orders) if (o.owner.equals(en.getKey())) count++;
                keys.add(en.getKey());
                model.addRow(new Object[]{en.getKey(), en.getValue()[2], en.getValue()[1], count});
            }
            if (keys.isEmpty()) {
                msg.setFont(sans(Font.PLAIN, 12));
                msg.setForeground(MUTED);
                msg.setText("No customers have registered yet.");
            } else {
                msg.setText(" ");
            }
        };

        RoundButton delBtn = new RoundButton("ARCHIVE CUSTOMER", RoundButton.DANGERV);
        delBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { say(msg, "Select a customer first.", false); return; }
            String key = keys.get(row);
            int ok = JOptionPane.showConfirmDialog(this,
                    "Archive account \"" + key + "\"? The customer and orders will remain in SQL.",
                    "Archive Customer", JOptionPane.YES_NO_OPTION);
            if (ok == JOptionPane.YES_OPTION) {
                customers.remove(key);
                carts.remove(key);
                cartBranch.remove(key);
                Database.deleteUser(key);
                refresh.run();
                say(msg, "Customer archived. The SQL record was kept.", true);
            }
        });

        page.add(scroll(table), BorderLayout.CENTER);
        page.add(actionBar(flow(delBtn), msg), BorderLayout.SOUTH);
        refresh.run();
        return page;
    }

    // =====================================================================
    //                        CUSTOMER DASHBOARD
    // =====================================================================
    private void showCustomerHome(String username) {
        currentUser = username;
        carts.putIfAbsent(username, new ArrayList<>());
        cartBranch.putIfAbsent(username, 0);
        wishlist.clear();
        wishlist.addAll(Database.loadWishlist(username));
        buildShell("Customer", customers.get(username)[2],
                new String[]{"Shop", "Wishlist", "My Cart", "My Orders", "Track My Orders", "Notifications", "Archive", "My Profile"}, this::custGo);
        custGo("Shop");
    }

    private void custGo(String page) {
        JPanel p;
        switch (page) {
            case "Wishlist":   p = custWishlist(); break;
            case "My Cart":    p = custCart();    break;
            case "My Orders":  p = custOrders();  break;
            case "Track My Orders": p = custTrackOrders(); break;
            case "Notifications": p = custNotifications(); break;
            case "Archive": p = custArchive(); break;
            case "My Profile": p = custProfile(); break;
            default:           p = custShop();
        }
        setPage(page, p);
    }

    // --------------------------- SHOP ---------------------------
    private JPanel custShop() {
        int b = branchOf(currentUser);
        JPanel page = shell("Shop", "Discover clothing, choose your color and size, and add your favorites to your wishlist.");

        Segmented branchSel = new Segmented(new String[]{"Kinawe (Main Branch)", "Opol Branch"}, b);
        branchSel.setOnChange(i -> {
            ArrayList<CartItem> cart = carts.get(currentUser);
            if (i != branchOf(currentUser) && !cart.isEmpty()) {
                int ok = JOptionPane.showConfirmDialog(this,
                        "Each order is prepared by one branch, so switching will empty your cart. Continue?",
                        "Switch Branch", JOptionPane.YES_NO_OPTION);
                if (ok != JOptionPane.YES_OPTION) { custGo("Shop"); return; }
                cart.clear(); Database.clearCart(currentUser);
            }
            cartBranch.put(currentUser, i);
            custGo("Shop");
        });

        JTextField search = dlgField("", 16);
        JComboBox<String> category = new JComboBox<>(new String[]{"All", "Tops", "Bottoms", "Outerwear", "Dresses", "Accessories"});
        JComboBox<String> colorFilter = new JComboBox<>(new String[]{"All Colors", "Black", "White", "Blue", "Brown", "Beige", "Pink", "Red", "Green"});
        JComboBox<String> priceFilter = new JComboBox<>(new String[]{"Any Price", "Under ₱500", "₱500–₱999", "₱1,000–₱1,499", "₱1,500+"});

        DefaultTableModel model = newModel("Photo", "Product", "Category", "Color", "Availability", "Price", "In Stock", "Favorite");
        JTable table = newTable(model);
        installProductPhotoRenderer(table, 0, 70);
        table.setRowHeight(82);
        ArrayList<Product> shown = new ArrayList<>();

        Runnable refresh = () -> {
            model.setRowCount(0); shown.clear();
            String q = search.getText().trim().toLowerCase();
            String cat = (String) category.getSelectedItem();
            String cf = (String) colorFilter.getSelectedItem();
            String pf = (String) priceFilter.getSelectedItem();
            for (Product p : catalog) {
                boolean okCat = "All".equals(cat) || p.category.equals(cat);
                boolean okText = q.isEmpty() || p.name.toLowerCase().contains(q);
                boolean okColor = "All Colors".equals(cf) || colorOptions(p).length == 0 || java.util.Arrays.stream(colorOptions(p)).anyMatch(x -> x.equalsIgnoreCase(cf));
                boolean okPrice = true;
                if ("Under ₱500".equals(pf)) okPrice = p.price < 500;
                else if ("₱500–₱999".equals(pf)) okPrice = p.price >= 500 && p.price < 1000;
                else if ("₱1,000–₱1,499".equals(pf)) okPrice = p.price >= 1000 && p.price < 1500;
                else if ("₱1,500+".equals(pf)) okPrice = p.price >= 1500;
                if (okCat && okText && okColor && okPrice) {
                    shown.add(p);
                    model.addRow(new Object[]{p, p.name, p.category, firstColor(p), p.availability, money(p.price), p.stock[b], wishlist.contains(p.id) ? "♥" : "♡"});
                }
            }
        };
        search.addKeyListener(new KeyAdapter(){ @Override public void keyReleased(KeyEvent e){ refresh.run(); }});
        category.addActionListener(e -> refresh.run()); colorFilter.addActionListener(e -> refresh.run()); priceFilter.addActionListener(e -> refresh.run());

        JLabel msg = new JLabel(" ");
        RoundButton details = new RoundButton("VIEW PRODUCT", RoundButton.PRIMARY);
        details.addActionListener(e -> { int row=table.getSelectedRow(); if(row>=0 && row<shown.size()) showProductDetails(shown.get(row), b); else say(msg,"Select a product first.",false); });
        RoundButton wish = new RoundButton("♡ WISHLIST", RoundButton.OUTLINE);
        wish.addActionListener(e -> {
            int row=table.getSelectedRow(); if(row<0 || row>=shown.size()){say(msg,"Select a product first.",false);return;}
            Product p=shown.get(row);
            if(wishlist.contains(p.id)){ wishlist.remove(p.id); Database.removeWishlist(currentUser,p.id); say(msg,"Removed from wishlist.",true); }
            else { wishlist.add(p.id); Database.addWishlist(currentUser,p.id); say(msg,"Added to wishlist.",true); }
            refresh.run();
        });
        table.addMouseListener(new MouseAdapter(){ @Override public void mouseClicked(MouseEvent e){ if(e.getClickCount()==2){int row=table.rowAtPoint(e.getPoint()); if(row>=0&&row<shown.size()) showProductDetails(shown.get(row),b);}}});

        JComboBox<String> size = new JComboBox<>(new String[]{"XS","S","M","L","XL"});
        JComboBox<String> color = new JComboBox<>(new String[]{"Select a product first"}); color.setEnabled(false);
        JLabel colorPreview = new JLabel(" Select a product color "); colorPreview.setPreferredSize(new Dimension(58, 54));
        colorPreview.setHorizontalAlignment(SwingConstants.CENTER); colorPreview.setBorder(new LineBorder(LINE)); colorPreview.setOpaque(true); colorPreview.setBackground(Color.WHITE);
        Runnable updateColorPreview = () -> { int r=table.getSelectedRow(); if(r>=0&&r<shown.size()&&color.isEnabled()){ImageIcon ci=productIcon(shown.get(r),(String)color.getSelectedItem(),54,48);colorPreview.setIcon(ci);colorPreview.setText(ci==null?"No Photo":"");}else{colorPreview.setIcon(null);colorPreview.setText("Select color");} };
        color.addActionListener(e -> updateColorPreview.run());
        table.getSelectionModel().addListSelectionListener(e -> { int row=table.getSelectedRow(); if(!e.getValueIsAdjusting()&&row>=0&&row<shown.size()){color.setModel(new DefaultComboBoxModel<>(colorOptions(shown.get(row)))); color.setEnabled(true); updateColorPreview.run();} });
        JSpinner qty = new JSpinner(new SpinnerNumberModel(1,1,20,1));
        RoundButton addBtn = new RoundButton("ADD TO CART", RoundButton.GOLDV);
        addBtn.addActionListener(e -> {
            int row=table.getSelectedRow(); if(row<0){say(msg,"Please select a product first.",false);return;}
            Product p=shown.get(row); String sz=(String)size.getSelectedItem(); String chosenColor=color.isEnabled()?(String)color.getSelectedItem():null; int q=(Integer)qty.getValue();
            ArrayList<CartItem> cart=carts.get(currentUser);
            if(!"Available".equalsIgnoreCase(p.availability)){say(msg,p.name+" is currently unavailable.",false);return;}
            if(chosenColor==null||chosenColor.isBlank()||chosenColor.startsWith("Select")){say(msg,"Please choose a color first.",false);return;}
            int inCart=0; for(CartItem it:cart) if(it.product==p) inCart+=it.qty;
            if(p.stock[b]<=0){say(msg,p.name+" is out of stock at "+BRANCH_SHORT[b]+".",false);return;}
            if(inCart+q>p.stock[b]){say(msg,"Only "+p.stock[b]+" available at "+BRANCH_SHORT[b]+".",false);return;}
            boolean merged=false; int newQty=q; for(CartItem it:cart) if(it.product==p&&it.size.equals(sz)&&it.color.equalsIgnoreCase(chosenColor)){it.qty+=q;newQty=it.qty;merged=true;break;}
            if(!merged) cart.add(new CartItem(p,sz,chosenColor,q));
            Database.saveCartItem(currentUser,p.id,b,sz,chosenColor,newQty); say(msg,p.name+" added to your cart.",true);
        });

        JPanel rowBranch=new JPanel(new FlowLayout(FlowLayout.LEFT,12,0)); rowBranch.setOpaque(false); rowBranch.add(capLabel("Shop at")); rowBranch.add(branchSel);
        JPanel rowFilter=new JPanel(new FlowLayout(FlowLayout.LEFT,10,0)); rowFilter.setOpaque(false); rowFilter.add(capLabel("Search"));rowFilter.add(search);rowFilter.add(capLabel("Category"));rowFilter.add(category);rowFilter.add(capLabel("Color"));rowFilter.add(colorFilter);rowFilter.add(capLabel("Price"));rowFilter.add(priceFilter);
        JPanel top=new JPanel(new GridLayout(2,1,0,10));top.setOpaque(false);top.add(rowBranch);top.add(rowFilter);
        JPanel center=new JPanel(new BorderLayout(0,12));center.setOpaque(false);center.add(top,BorderLayout.NORTH);center.add(scroll(table),BorderLayout.CENTER);
        JPanel bar=new JPanel(new FlowLayout(FlowLayout.LEFT,10,0));bar.setOpaque(false);bar.add(capLabel("Color"));bar.add(color);bar.add(colorPreview);bar.add(capLabel("Size"));bar.add(size);bar.add(capLabel("Qty"));bar.add(qty);bar.add(addBtn);bar.add(details);bar.add(wish);bar.add(msg);
        page.add(center,BorderLayout.CENTER); page.add(bar,BorderLayout.SOUTH); refresh.run(); return page;
    }

    // ------------------------- PRODUCT DETAILS -------------------------
    private void showProductDetails(Product p, int branch) {
        JDialog d=new JDialog(this,"StyleHub - "+p.name,true); d.setSize(920,620); d.setMinimumSize(new Dimension(820,560)); d.setLocationRelativeTo(this);
        JPanel root=new JPanel(new BorderLayout(22,0)); root.setBackground(IVORY); root.setBorder(new EmptyBorder(24,26,22,26));
        JPanel photo=new JPanel(new BorderLayout()); photo.setBackground(Color.WHITE); photo.setBorder(new LineBorder(LINE));
        JLabel pic=new JLabel(); pic.setHorizontalAlignment(SwingConstants.CENTER); pic.setVerticalAlignment(SwingConstants.CENTER); ImageIcon icon=productIcon(p,360,390); if(icon!=null)pic.setIcon(icon);else pic.setText("No Photo"); photo.add(pic,BorderLayout.CENTER);
        root.add(photo,BorderLayout.WEST);
        JPanel info=new JPanel(); info.setOpaque(false); info.setLayout(new BoxLayout(info,BoxLayout.Y_AXIS));
        info.add(lbl(p.category.toUpperCase(),tracked(sans(Font.BOLD,9),.12f),GOLD)); info.add(vs(6)); info.add(lbl(p.name,serif(Font.PLAIN,30),INK)); info.add(vs(8)); info.add(lbl(money(p.price),serif(Font.PLAIN,24),INK)); info.add(vs(10));
        info.add(lbl("AVAILABLE AT "+BRANCH_SHORT[branch].toUpperCase(),tracked(sans(Font.BOLD,9),.1f),MUTED)); info.add(vs(5)); info.add(lbl(p.stock[branch]+" in stock",sans(Font.BOLD,13),p.stock[branch]<=LOW_STOCK?DANGER:OK)); info.add(vs(18));
        JComboBox<String> colors=new JComboBox<>(colorOptions(p)); colors.addActionListener(ev -> { ImageIcon selectedIcon = productIcon(p, (String) colors.getSelectedItem(), 360, 390); pic.setIcon(selectedIcon); pic.setText(selectedIcon == null ? "No Photo" : ""); }); JComboBox<String> sizes=new JComboBox<>(new String[]{"XS","S","M","L","XL"}); JSpinner q=new JSpinner(new SpinnerNumberModel(1,1,Math.max(1,Math.min(20,p.stock[branch])),1));
        addField(info,"Color",colors); addField(info,"Size",sizes); addField(info,"Quantity",q);
        JLabel m=new JLabel(" "); info.add(left(m)); info.add(vs(8));
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));actions.setOpaque(false);
        RoundButton add=new RoundButton("ADD TO CART",RoundButton.GOLDV); RoundButton buy=new RoundButton("BUY NOW",RoundButton.PRIMARY); RoundButton fav=new RoundButton(wishlist.contains(p.id)?"♥ SAVED":"♡ WISHLIST",RoundButton.OUTLINE);
        fav.addActionListener(e->{if(wishlist.contains(p.id)){wishlist.remove(p.id);Database.removeWishlist(currentUser,p.id);fav.setText("♡ WISHLIST");}else{wishlist.add(p.id);Database.addWishlist(currentUser,p.id);fav.setText("♥ SAVED");}});
        ActionListener addAction=e->{if(p.stock[branch]<=0){say(m,"This product is out of stock.",false);return;}int qq=(Integer)q.getValue();String sz=(String)sizes.getSelectedItem(),co=(String)colors.getSelectedItem();ArrayList<CartItem> cart=carts.get(currentUser);int existing=0;for(CartItem it:cart)if(it.product==p)existing+=it.qty;if(existing+qq>p.stock[branch]){say(m,"Not enough stock for this quantity.",false);return;}boolean merged=false;for(CartItem it:cart)if(it.product==p&&it.size.equals(sz)&&it.color.equals(co)){it.qty+=qq;Database.saveCartItem(currentUser,p.id,branch,sz,co,it.qty);merged=true;break;}if(!merged){cart.add(new CartItem(p,sz,co,qq));Database.saveCartItem(currentUser,p.id,branch,sz,co,qq);}say(m,"Added to cart.",true);};
        add.addActionListener(addAction); buy.addActionListener(e->{addAction.actionPerformed(e); if(!carts.get(currentUser).isEmpty()){d.dispose();custGo("My Cart");}}); actions.add(add);actions.add(buy);actions.add(fav); info.add(actions);
        root.add(info,BorderLayout.CENTER); JPanel bottom=new JPanel(new FlowLayout(FlowLayout.RIGHT));bottom.setOpaque(false);RoundButton close=new RoundButton("CLOSE",RoundButton.OUTLINE);close.addActionListener(e->d.dispose());bottom.add(close);root.add(bottom,BorderLayout.SOUTH);d.setContentPane(root);d.setVisible(true);
    }

    // --------------------------- WISHLIST ---------------------------
    private JPanel custWishlist(){
        JPanel page=shell("Wishlist","Your saved StyleHub pieces, ready whenever you are.");
        DefaultTableModel model=newModel("Photo","Product","Category","Color","Price","Stock","Availability"); JTable table=newTable(model);installProductPhotoRenderer(table,0,76);table.setRowHeight(88);ArrayList<Product> shown=new ArrayList<>();JLabel msg=new JLabel(" ");
        Runnable refresh=()->{model.setRowCount(0);shown.clear();for(Product p:catalog)if(wishlist.contains(p.id)){shown.add(p);int b=branchOf(currentUser);model.addRow(new Object[]{p,p.name,p.category,firstColor(p),money(p.price),p.stock[b],p.availability});}};
        RoundButton view=new RoundButton("VIEW PRODUCT",RoundButton.PRIMARY);view.addActionListener(e->{int r=table.getSelectedRow();if(r>=0)showProductDetails(shown.get(r),branchOf(currentUser));else say(msg,"Select a product first.",false);});
        RoundButton remove=new RoundButton("REMOVE",RoundButton.OUTLINE);remove.addActionListener(e->{int r=table.getSelectedRow();if(r<0){say(msg,"Select a product first.",false);return;}Product p=shown.get(r);wishlist.remove(p.id);Database.removeWishlist(currentUser,p.id);refresh.run();say(msg,"Removed from wishlist.",true);});
        page.add(scroll(table),BorderLayout.CENTER);page.add(actionBar(flow(view,remove),msg),BorderLayout.SOUTH);refresh.run();return page;
    }

    // ------------------------ NOTIFICATIONS ------------------------
    private JPanel custNotifications(){
        JPanel page=shell("Notifications","Updates about your orders, pickup tickets, and stock.");
        JPanel list=new JPanel();list.setOpaque(false);list.setLayout(new BoxLayout(list,BoxLayout.Y_AXIS));int count=0;
        for(Order o:orders) if(o.owner.equals(currentUser)){String text="Order #"+o.id+" is "+o.status.toLowerCase()+"."; if("Pick-up".equals(o.method)&&"Processing".equals(o.status))text+=" Your pickup order is being prepared."; if("Pick-up".equals(o.method)&&"Delivered".equals(o.status))text+=" You can claim it at the branch."; JPanel card=whiteCard(16);card.setLayout(new BorderLayout(10,0));card.add(lbl("ORDER UPDATE",tracked(sans(Font.BOLD,9),.1f),GOLD),BorderLayout.WEST);card.add(lbl(text,sans(Font.PLAIN,13),INK),BorderLayout.CENTER);list.add(card);list.add(vs(8));count++;}
        if(count==0)list.add(lbl("You're all caught up. New order updates will appear here.",sans(Font.PLAIN,13),MUTED));
        JPanel holder=new JPanel(new BorderLayout());holder.setOpaque(false);holder.add(list,BorderLayout.NORTH);page.add(new JScrollPane(holder),BorderLayout.CENTER);return page;
    }

    // --------------------------- CART ---------------------------
    private JPanel custCart() {
        int b = branchOf(currentUser);
        ArrayList<CartItem> cart = carts.get(currentUser);
        JPanel page = shell("My Cart", "Your order will be prepared by " + BRANCHES[b] + ".");

        DefaultTableModel model = newModel("Photo", "Product", "Color", "Size", "Qty", "Price", "Subtotal");
        JTable table = newTable(model);
        installProductPhotoRenderer(table, 0, 44);
        JLabel total = lbl("", serif(Font.PLAIN, 22), INK);
        JLabel msg = new JLabel(" ");

        Runnable refresh = () -> {
            model.setRowCount(0);
            double sum = 0;
            for (CartItem it : cart) {
                model.addRow(new Object[]{it, it.product.name, it.color, it.size, it.qty,
                        money(it.product.price), money(it.subtotal())});
                sum += it.subtotal();
            }
            total.setText("Total  " + money(sum));
        };

        JSpinner newQty = new JSpinner(new SpinnerNumberModel(1, 1, 20, 1));
        table.getSelectionModel().addListSelectionListener(e -> {
            int row = table.getSelectedRow();
            if (!e.getValueIsAdjusting() && row >= 0 && row < cart.size()) {
                newQty.setValue(cart.get(row).qty);
            }
        });

        RoundButton updateBtn = new RoundButton("UPDATE QUANTITY", RoundButton.PRIMARY);
        updateBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { say(msg, "Select an item to update.", false); return; }
            CartItem it = cart.get(row);
            int q = (Integer) newQty.getValue();
            int others = 0;
            for (CartItem x : cart) if (x != it && x.product == it.product) others += x.qty;
            if (others + q > it.product.stock[b]) {
                say(msg, "Only " + it.product.stock[b] + " available at " + BRANCH_SHORT[b] + ".", false);
                return;
            }
            it.qty = q;
            Database.saveCartItem(currentUser, it.product.id, b, it.size, it.color, q);
            refresh.run();
            table.setRowSelectionInterval(row, row);
            say(msg, "Quantity updated.", true);
        });

        RoundButton removeBtn = new RoundButton("REMOVE ITEM", RoundButton.OUTLINE);
        removeBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { say(msg, "Select an item to remove.", false); return; }
            CartItem removed = cart.remove(row);
            Database.removeCartItem(currentUser, removed.product.id, removed.size, removed.color);
            refresh.run();
            say(msg, "Item removed.", true);
        });

        RoundButton clearBtn = new RoundButton("CLEAR CART", RoundButton.OUTLINE);
        clearBtn.addActionListener(e -> {
            if (cart.isEmpty()) { say(msg, "Your cart is already empty.", false); return; }
            int ok = JOptionPane.showConfirmDialog(this, "Remove all items from your cart?",
                    "Clear Cart", JOptionPane.YES_NO_OPTION);
            if (ok == JOptionPane.YES_OPTION) {
                cart.clear();
                Database.clearCart(currentUser);
                refresh.run();
                say(msg, "Cart cleared.", true);
            }
        });

        RoundButton checkoutBtn = new RoundButton("CHECKOUT", RoundButton.GOLDV);
        checkoutBtn.addActionListener(e -> {
            if (cart.isEmpty()) { say(msg, "Your cart is empty.", false); return; }

            JComboBox<String> method = new JComboBox<>(METHODS);
            JTextField address = dlgField("", 24);
            address.setEnabled(false);
            JLabel pickupInfo = new JLabel("Pickup: " + pickupAddress(b));
            pickupInfo.setFont(sans(Font.PLAIN, 11));
            pickupInfo.setForeground(MUTED);
            method.addActionListener(ev -> {
                boolean deliveryMode = method.getSelectedIndex() == 1;
                address.setEnabled(deliveryMode);
                pickupInfo.setVisible(!deliveryMode);
            });
            JComboBox<String> pay = new JComboBox<>(PAYMENTS);
            double sum = 0;
            for (CartItem it : cart) sum += it.subtotal();
            Object[] form = {"Branch: " + BRANCHES[b], "Receive your order by", method,
                    pickupInfo, "Delivery address (for delivery only)", address, "Payment method", pay,
                    "Total: " + money(sum)};
            int ok = JOptionPane.showConfirmDialog(this, form, "Checkout",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (ok != JOptionPane.OK_OPTION) return;

            boolean delivery = method.getSelectedIndex() == 1;
            String deliveryAddress = cleanText(address.getText(), 255);
            if (delivery && (deliveryAddress.isEmpty() || deliveryAddress.length() < 5)) {
                say(msg, "Please enter a valid delivery address (at least 5 characters).", false);
                return;
            }
            if (delivery && deliveryAddress.length() > 255) {
                say(msg, "Delivery address must be 255 characters or fewer.", false);
                return;
            }
            String selectedPayment = (String) pay.getSelectedItem();
            if ("GCash".equals(selectedPayment) && !showGcashPayment(sum)) return;
            // Re-check stock at the branch right before placing the order
            for (CartItem it : cart) {
                int need = 0;
                for (CartItem x : cart) if (x.product == it.product) need += x.qty;
                if (need > it.product.stock[b]) {
                    say(msg, "Sorry, only " + it.product.stock[b] + " of " + it.product.name
                            + " left at " + BRANCH_SHORT[b] + ".", false);
                    return;
                }
            }
            ArrayList<CartItem> copy = new ArrayList<>();
            ArrayList<Object[]> dbItems = new ArrayList<>();
            for (CartItem it : cart) {
                copy.add(new CartItem(it.product, it.size, it.color, it.qty));
                dbItems.add(new Object[]{it.product.id, it.size, it.qty, it.product.price, it.color});
            }
            int orderId = Database.placeOrder(currentUser, b, METHODS[method.getSelectedIndex()],
                    delivery ? address.getText().trim() : "", selectedPayment,
                    sum, dbItems);
            if (orderId < 0) { say(msg, "The order could not be saved to the database.", false); return; }
            for (CartItem it : cart) it.product.stock[b] -= it.qty;
            Order order = new Order(orderId, currentUser, today(), b, copy,
                    METHODS[method.getSelectedIndex()], delivery ? address.getText().trim() : "",
                    selectedPayment);
            orders.add(order);
            cart.clear();
            Database.clearCart(currentUser);
            if ("Pick-up".equals(order.method)) {
                showPickupTicket(order);
            } else {
                JOptionPane.showMessageDialog(this, "Order #" + order.id + " placed with " + BRANCHES[b] + "!",
                        "Order placed", JOptionPane.INFORMATION_MESSAGE);
            }
            custGo("My Orders");
        });

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 0));
        right.setOpaque(false);
        right.add(total);
        right.add(checkoutBtn);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(msg, BorderLayout.WEST);
        footer.add(right, BorderLayout.EAST);

        JPanel buttons = flow(capLabel("New qty"), newQty, updateBtn, removeBtn, clearBtn);

        JPanel south = new JPanel(new BorderLayout(0, 12));
        south.setOpaque(false);
        south.add(buttons, BorderLayout.NORTH);
        south.add(footer, BorderLayout.CENTER);

        page.add(scroll(table), BorderLayout.CENTER);
        page.add(south, BorderLayout.SOUTH);
        refresh.run();
        return page;
    }

    // ------------------------- MY ORDERS -------------------------
    private JPanel custOrders() {
        JPanel page = shell("My Orders", "Track your orders, change their details, or cancel while still pending.");

        DefaultTableModel model = newModel("PHOTO", "Order #", "Date", "Branch", "Method", "Items",
                "Total", "Payment", "Status");
        JTable table = newTable(model);
        ArrayList<Order> shown = new ArrayList<>();
        JLabel msg = new JLabel(" ");

        Runnable refresh = () -> {
            model.setRowCount(0);
            shown.clear();
            for (Order o : orders) {
                if (!o.owner.equals(currentUser)) continue;
                shown.add(o);
                model.addRow(new Object[]{o.items.isEmpty() ? null : o.items.get(0).product, "#" + o.id, o.date, BRANCH_SHORT[o.branch], o.method,
                        o.itemCount(), money(o.total()), o.payment, o.status});
            }
            if (shown.isEmpty()) {
                msg.setFont(sans(Font.PLAIN, 12));
                msg.setForeground(MUTED);
                msg.setText("You have no orders yet. Add items to your cart and check out.");
            } else {
                msg.setText(" ");
            }
        };

        RoundButton viewBtn = new RoundButton("VIEW DETAILS", RoundButton.PRIMARY);
        viewBtn.addActionListener(e -> {
            Order o = pick(table, shown, msg);
            if (o != null) showOrderDetails(o);
        });

        RoundButton editBtn = new RoundButton("EDIT ORDER", RoundButton.OUTLINE);
        editBtn.addActionListener(e -> {
            Order o = pick(table, shown, msg);
            if (o == null) return;
            if (!o.status.equals("Pending")) {
                say(msg, "Only Pending orders can be edited.", false);
                return;
            }
            JComboBox<String> method = new JComboBox<>(METHODS);
            method.setSelectedItem(o.method);
            JTextField address = dlgField(o.address, 24);
            address.setEnabled(o.method.equals("Delivery"));
            method.addActionListener(ev -> address.setEnabled(method.getSelectedIndex() == 1));
            JComboBox<String> pay = new JComboBox<>(PAYMENTS);
            pay.setSelectedItem(o.payment);
            Object[] form = {"Branch: " + BRANCHES[o.branch], "Receive your order by", method,
                    "Delivery address", address, "Payment method", pay};
            int ok = JOptionPane.showConfirmDialog(this, form, "Edit Order #" + o.id,
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (ok != JOptionPane.OK_OPTION) return;
            boolean delivery = method.getSelectedIndex() == 1;
            if (delivery && address.getText().trim().isEmpty()) {
                say(msg, "A delivery address is required for delivery.", false);
                return;
            }
            o.method = METHODS[method.getSelectedIndex()];
            String deliveryAddress = cleanText(address.getText(), 255);
            if (delivery && (deliveryAddress.isEmpty() || deliveryAddress.length() < 5 || deliveryAddress.length() > 255)) {
                say(msg, "Please enter a valid delivery address (5-255 characters).", false);
                return;
            }
            o.address = delivery ? deliveryAddress : "";
            o.payment = (String) pay.getSelectedItem();
            Database.updateOrderDetails(o.id, o.method, o.address, o.payment);
            refresh.run();
            say(msg, "Order #" + o.id + " updated.", true);
        });

        RoundButton cancelBtn = new RoundButton("CANCEL ORDER", RoundButton.OUTLINE);
        cancelBtn.addActionListener(e -> {
            Order o = pick(table, shown, msg);
            if (o == null) return;
            if (!o.status.equals("Pending")) {
                say(msg, "Only Pending orders can be cancelled.", false);
                return;
            }
            int ok = JOptionPane.showConfirmDialog(this, "Cancel order #" + o.id + "?",
                    "Cancel Order", JOptionPane.YES_NO_OPTION);
            if (ok == JOptionPane.YES_OPTION) {
                restoreStock(o);
                o.status = "Cancelled";
                Database.updateOrderStatus(o.id, "Cancelled");
                refresh.run();
                say(msg, "Order #" + o.id + " cancelled.", true);
            }
        });

        RoundButton delBtn = new RoundButton("ARCHIVE ORDER", RoundButton.DANGERV);
        delBtn.addActionListener(e -> {
            Order o = pick(table, shown, msg);
            if (o == null) return;
            if (!o.status.equals("Cancelled")) {
                say(msg, "Cancel the order first, then you can delete it.", false);
                return;
            }
            int ok = JOptionPane.showConfirmDialog(this,
                    "Permanently delete order #" + o.id + " from your history?",
                    "Archive Order", JOptionPane.YES_NO_OPTION);
            if (ok == JOptionPane.YES_OPTION) {
                removeOrder(o);
                refresh.run();
                say(msg, "Order archived. The SQL record was kept.", true);
            }
        });

        page.add(scroll(table), BorderLayout.CENTER);
        installProductPhotoRenderer(table, 0, 58);
        RoundButton ticketBtn = new RoundButton("PICKUP TICKET", RoundButton.GOLDV);
        ticketBtn.addActionListener(e -> {
            Order o = pick(table, shown, msg);
            if (o != null) {
                if (!"Pick-up".equals(o.method)) say(msg, "This order is for delivery, so it has no pickup ticket.", false);
                else showPickupTicket(o);
            }
        });
        page.add(actionBar(flow(viewBtn, ticketBtn, editBtn, cancelBtn, delBtn), msg), BorderLayout.SOUTH);
        refresh.run();
        return page;
    }

    // --------------------------- ARCHIVE ---------------------------
    private JPanel custArchive() {
        JPanel page = shell("Archive", "Your archived orders are kept safely in the database and can be restored here.");

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel title = lbl("MY ARCHIVED ORDERS", tracked(sans(Font.BOLD, 10), .10f), GOLD);
        JLabel note = lbl("Archived orders are hidden from My Orders but are NOT deleted from SQL.", sans(Font.PLAIN, 12), MUTED);
        JPanel words = new JPanel();
        words.setOpaque(false);
        words.setLayout(new BoxLayout(words, BoxLayout.Y_AXIS));
        words.add(title);
        words.add(vs(6));
        words.add(note);
        top.add(words, BorderLayout.WEST);

        DefaultTableModel model = newModel("Order #", "Date", "Status", "Total");
        JTable table = newTable(model);
        table.setRowHeight(36);
        JLabel msg = new JLabel(" ");
        Runnable refresh = () -> {
            model.setRowCount(0);
            for (Object[] x : Database.loadArchivedOrdersForUser(currentUser)) {
                model.addRow(new Object[]{"#" + x[0], x[1], x[2], money(((Number)x[3]).doubleValue())});
            }
            msg.setText(model.getRowCount() == 0 ? "No archived orders." : model.getRowCount() + " archived order(s)");
        };

        RoundButton restore = new RoundButton("RESTORE ORDER", RoundButton.PRIMARY);
        restore.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { say(msg, "Select an archived order first.", false); return; }
            String value = String.valueOf(model.getValueAt(row, 0)).replace("#", "").trim();
            int orderId;
            try { orderId = Integer.parseInt(value); }
            catch (NumberFormatException ex) { say(msg, "Invalid order number.", false); return; }
            int ok = JOptionPane.showConfirmDialog(this,
                    "Restore order #" + orderId + " to My Orders?",
                    "Restore Order", JOptionPane.YES_NO_OPTION);
            if (ok != JOptionPane.YES_OPTION) return;
            if (Database.restoreOrderForUser(orderId, currentUser)) {
                refreshDatabaseState();
                refresh.run();
                say(msg, "Order #" + orderId + " restored.", true);
            } else {
                say(msg, "That archived order could not be restored.", false);
            }
        });

        page.add(top, BorderLayout.NORTH);
        page.add(scroll(table), BorderLayout.CENTER);
        page.add(actionBar(flow(restore), msg), BorderLayout.SOUTH);
        refresh.run();
        return page;
    }

    // ------------------------ TRACK MY ORDERS ------------------------
    private JPanel custTrackOrders() {
        JPanel page = shell("Track My Orders", "Check the latest status of your orders and follow their progress from placement to delivery.");

        ArrayList<Order> mine = new ArrayList<>();
        JComboBox<String> orderSelect = new JComboBox<>();
        orderSelect.setFont(sans(Font.PLAIN, 13));
        orderSelect.setPreferredSize(new Dimension(260, 38));
        JLabel msg = new JLabel(" ");

        JPanel selector = whiteCard(16);
        selector.setLayout(new BorderLayout(14, 0));
        selector.add(capLabel("SELECT ORDER"), BorderLayout.WEST);
        selector.add(orderSelect, BorderLayout.CENTER);

        JPanel trackerCard = whiteCard(22);
        trackerCard.setLayout(new BorderLayout(0, 18));

        Runnable render = () -> {
            trackerCard.removeAll();
            int idx = orderSelect.getSelectedIndex();
            if (idx < 0 || idx >= mine.size()) {
                trackerCard.add(lbl("No orders to track yet.", serif(Font.PLAIN, 22), MUTED), BorderLayout.CENTER);
                trackerCard.revalidate(); trackerCard.repaint();
                return;
            }

            Order o = mine.get(idx);
            JPanel head = new JPanel(new BorderLayout(15, 0));
            head.setOpaque(false);
            JPanel left = new JPanel(); left.setOpaque(false);
            left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
            left.add(lbl("ORDER #" + o.id, tracked(sans(Font.BOLD, 10), .14f), GOLD));
            left.add(vs(5));
            left.add(lbl(o.date, serif(Font.PLAIN, 24), INK));
            left.add(vs(4));
            left.add(lbl(BRANCH_SHORT[o.branch] + "  •  " + o.method + "  •  " + o.itemCount() + " item(s)", sans(Font.PLAIN, 12), MUTED));
            head.add(left, BorderLayout.WEST);

            JLabel status = lbl(o.status.toUpperCase(), sans(Font.BOLD, 11), INK);
            status.setHorizontalAlignment(SwingConstants.CENTER);
            status.setOpaque(true);
            status.setBorder(new EmptyBorder(9, 16, 9, 16));
            status.setBackground(o.status.equals("Cancelled") ? new Color(245,225,221)
                    : (o.status.equals("Delivered") || o.status.equals("Completed")) ? new Color(224,239,226) : new Color(239,229,211));
            head.add(status, BorderLayout.EAST);
            trackerCard.add(head, BorderLayout.NORTH);

            if ("Cancelled".equals(o.status)) {
                JPanel cancelled = whiteCard(14);
                cancelled.setBackground(new Color(252, 242, 239));
                cancelled.add(lbl("This order was cancelled.", sans(Font.BOLD, 14), DANGER));
                trackerCard.add(cancelled, BorderLayout.CENTER);
            } else {
                JPanel steps = new JPanel(new GridLayout(1, 5, 10, 0));
                steps.setOpaque(false);
                String[] names = {"Order Placed", "Processing", "Shipped", "Delivered", "Completed"};
                String[] hints = {"Your order was received", "Preparing your items", "Order is on the way", "Customer received order", "Order completed"};
                int active = "Pending".equals(o.status) ? 0 : "Processing".equals(o.status) ? 1 : "Shipped".equals(o.status) ? 2 : "Delivered".equals(o.status) ? 3 : 4;
                for (int i = 0; i < names.length; i++) {
                    JPanel step = new JPanel();
                    step.setOpaque(false);
                    step.setLayout(new BoxLayout(step, BoxLayout.Y_AXIS));
                    JLabel dot = lbl(i <= active ? "●" : "○", sans(Font.BOLD, 24), i <= active ? GOLD : MUTED);
                    dot.setAlignmentX(Component.CENTER_ALIGNMENT);
                    JLabel name = lbl(names[i], sans(Font.BOLD, 12), i <= active ? INK : MUTED);
                    name.setAlignmentX(Component.CENTER_ALIGNMENT);
                    JLabel hint = lbl(hints[i], sans(Font.PLAIN, 10), MUTED);
                    hint.setAlignmentX(Component.CENTER_ALIGNMENT);
                    step.add(dot); step.add(vs(5)); step.add(name); step.add(vs(4)); step.add(hint);
                    steps.add(step);
                }
                trackerCard.add(steps, BorderLayout.CENTER);
            }

            JPanel details = new JPanel(new GridLayout(1, 3, 12, 0));
            details.setOpaque(false);
            JPanel receive = whiteCard(14);
            receive.setLayout(new BoxLayout(receive, BoxLayout.Y_AXIS));
            receive.add(capLabel("RECEIVE YOUR ORDER")); receive.add(vs(6));
            if ("Pick-up".equals(o.method)) {
                receive.add(lbl("Pick-up", sans(Font.BOLD, 13), INK));
                receive.add(vs(3));
                receive.add(lbl(pickupAddress(o.branch), sans(Font.PLAIN, 11), MUTED));
            } else {
                receive.add(lbl("Delivery", sans(Font.BOLD, 13), INK));
                receive.add(vs(3));
                receive.add(lbl(o.address == null || o.address.isBlank() ? "No address provided" : o.address, sans(Font.PLAIN, 11), MUTED));
            }
            JPanel payment = whiteCard(14);
            payment.setLayout(new BoxLayout(payment, BoxLayout.Y_AXIS));
            payment.add(capLabel("PAYMENT")); payment.add(vs(6));
            payment.add(lbl(o.payment, sans(Font.BOLD, 13), INK)); payment.add(vs(3));
            payment.add(lbl(money(o.total()), serif(Font.PLAIN, 20), INK));
            JPanel items = whiteCard(14);
            items.setLayout(new BoxLayout(items, BoxLayout.Y_AXIS));
            items.add(capLabel("ORDER ITEMS")); items.add(vs(6));
            for (CartItem it : o.items) {
                items.add(lbl(it.product.name + "  ×" + it.qty, sans(Font.PLAIN, 11), INK));
                items.add(vs(3));
            }
            details.add(receive); details.add(payment); details.add(items);
            trackerCard.add(details, BorderLayout.SOUTH);
            trackerCard.revalidate(); trackerCard.repaint();
        };

        Runnable reload = () -> {
            mine.clear();
            orderSelect.removeAllItems();
            for (Order o : orders) {
                if (!o.owner.equalsIgnoreCase(currentUser)) continue;
                mine.add(o);
                orderSelect.addItem("#" + o.id + "  •  " + o.status + "  •  " + o.date);
            }
            if (mine.isEmpty()) {
                msg.setText("You have no orders to track yet. Place an order first.");
                trackerCard.removeAll();
                trackerCard.add(lbl("No orders to track yet.", serif(Font.PLAIN, 22), MUTED), BorderLayout.CENTER);
                trackerCard.revalidate(); trackerCard.repaint();
            } else {
                msg.setText(mine.size() + " order(s) available for tracking.");
                orderSelect.setSelectedIndex(0);
                render.run();
            }
        };
        orderSelect.addActionListener(e -> render.run());

        RoundButton detailsBtn = new RoundButton("VIEW FULL ORDER", RoundButton.PRIMARY);
        detailsBtn.addActionListener(e -> {
            int idx = orderSelect.getSelectedIndex();
            if (idx >= 0 && idx < mine.size()) showOrderDetails(mine.get(idx));
            else say(msg, "Select an order first.", false);
        });
        RoundButton refreshBtn = new RoundButton("REFRESH STATUS", RoundButton.OUTLINE);
        refreshBtn.addActionListener(e -> { refreshDatabaseState(); custGo("Track My Orders"); });

        JPanel top = new JPanel(new BorderLayout(0, 12));
        top.setOpaque(false);
        top.add(selector, BorderLayout.CENTER);
        top.add(msg, BorderLayout.SOUTH);
        page.add(top, BorderLayout.NORTH);
        page.add(scroll(trackerCard), BorderLayout.CENTER);
        page.add(actionBar(flow(detailsBtn, refreshBtn), msg), BorderLayout.SOUTH);
        reload.run();
        return page;
    }

    // --------------------------- PROFILE ---------------------------
    private JPanel custProfile() {
        JPanel page = shell("My Profile", "View and update your account details.");
        String[] acc = customers.get(currentUser);

        JPanel form = whiteCard(30);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        JTextField username = new JTextField(currentUser);
        username.setEditable(false);
        JTextField name = new JTextField(acc[2]);
        JTextField email = new JTextField(acc[1]);
        styleField(username);
        styleField(name);
        styleField(email);
        username.setBackground(SAND);
        JLabel msg1 = new JLabel(" ");

        form.add(left(lbl("Personal information", serif(Font.PLAIN, 20), INK)));
        form.add(vs(16));
        addField(form, "Username (cannot be changed)", username);
        addField(form, "Full name", name);
        addField(form, "Email", email);
        form.add(left(msg1));
        form.add(vs(8));

        RoundButton saveBtn = new RoundButton("SAVE CHANGES", RoundButton.PRIMARY);
        saveBtn.addActionListener(e -> {
            String nm = name.getText().trim();
            String em = email.getText().trim();
            if (nm.isEmpty() || em.isEmpty()) {
                say(msg1, "Name and email cannot be empty.", false);
            } else if (!nm.matches("[A-Za-z][A-Za-z .'-]*")) {
                say(msg1, "Name can only contain letters.", false);
            } else if (!em.matches("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$")) {
                say(msg1, "Please enter a valid email address.", false);
            } else {
                acc[2] = nm;
                acc[1] = em;
                Database.updateProfile(currentUser, nm, em);
                whoLabel.setText(nm);
                say(msg1, "Profile updated.", true);
            }
        });
        form.add(left(saveBtn));
        form.add(vs(30));
        form.add(rule(LINE, 100, 1));
        form.add(vs(26));

        JPasswordField oldPass = new JPasswordField();
        JPasswordField newPass = new JPasswordField();
        JPasswordField confirm = new JPasswordField();
        styleField(oldPass);
        styleField(newPass);
        styleField(confirm);
        JLabel msg2 = new JLabel(" ");

        form.add(left(lbl("Change password", serif(Font.PLAIN, 20), INK)));
        form.add(vs(16));
        addField(form, "Current password", oldPass);
        addField(form, "New password", newPass);
        addField(form, "Confirm new password", confirm);
        form.add(left(msg2));
        form.add(vs(8));

        RoundButton passBtn = new RoundButton("UPDATE PASSWORD", RoundButton.PRIMARY);
        passBtn.addActionListener(e -> {
            String o = new String(oldPass.getPassword());
            String n = new String(newPass.getPassword());
            String c = new String(confirm.getPassword());
            if (o.isEmpty() || n.isEmpty() || c.isEmpty()) {
                say(msg2, "Please fill in all password fields.", false);
            } else if (!Database.verifyUserPassword(currentUser, o)) {
                say(msg2, "Current password is incorrect.", false);
            } else if (!validPassword(n)) {
                say(msg2, "New password must be 6-128 characters.", false);
            } else if (!n.equals(c)) {
                say(msg2, "New passwords do not match.", false);
            } else {
                Database.updatePassword(currentUser, n);
                oldPass.setText("");
                newPass.setText("");
                confirm.setText("");
                say(msg2, "Password updated.", true);
            }
        });
        form.add(left(passBtn));
        form.add(vs(30));
        form.add(rule(LINE, 100, 1));
        form.add(vs(26));

        form.add(left(lbl("Archive account", serif(Font.PLAIN, 20), DANGER)));
        form.add(vs(6));
        form.add(left(lbl("Your account and orders will be moved to Archive and kept in the database.",
                sans(Font.PLAIN, 12), MUTED)));
        form.add(vs(14));
        RoundButton deleteBtn = new RoundButton("ARCHIVE MY ACCOUNT", RoundButton.DANGERV);
        deleteBtn.addActionListener(e -> {
            JPasswordField pw = new JPasswordField(20);
            Object[] dlg = {"This archives your account and orders. Your SQL data will not be deleted.",
                    "Enter your password to confirm:", pw};
            int ok = JOptionPane.showConfirmDialog(this, dlg, "Archive Account",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (ok != JOptionPane.OK_OPTION) return;
            if (!Database.verifyUserPassword(currentUser, new String(pw.getPassword()))) {
                JOptionPane.showMessageDialog(this, "Incorrect password. Account was not archived.",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            final String me = currentUser;
            customers.remove(me);
            carts.remove(me);
            cartBranch.remove(me);
            Database.deleteUser(me);
            JOptionPane.showMessageDialog(this, "Your account has been archived. Your SQL data was kept.",
                    "Account archived", JOptionPane.INFORMATION_MESSAGE);
            showLogin();
        });
        form.add(left(deleteBtn));

        JPanel holder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        holder.setOpaque(false);
        holder.add(form);
        JScrollPane sp = new JScrollPane(holder);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        page.add(sp, BorderLayout.CENTER);
        return page;
    }
}
