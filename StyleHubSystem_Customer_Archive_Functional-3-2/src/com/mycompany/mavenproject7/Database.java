package com.mycompany.mavenproject7;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JOptionPane;

/**
 * Database layer for StyleHub.
 *
 * Connects the app to the MySQL database "stylehubsystem" (see stylehubsystem.sql).
 * Every change made in the app (accounts, products, stock, carts, orders)
 * is written to the database through this class, and everything is loaded
 * back from the database when the app starts.
 *
 * If your MySQL password is not blank, change PASS below.
 */
public class Database {

    private static final String PORT = "3306";
    private static final String DB   = "stylehubsystem";
    private static final String USER = "root";
    private static final String PASS = "root";          

    private static final String URL =
            "jdbc:mysql://localhost:3306/stylehubsystem?useSSL=false&serverTimezone=UTC";

    

    // Password security: PBKDF2-HMAC-SHA256 with a unique random salt.
    private static final String HASH_PREFIX = "pbkdf2_sha256";
    private static final int PBKDF2_ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;

    private static String hashPassword(String password) {
        if (password == null) throw new IllegalArgumentException("Password is required.");
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] hash = pbkdf2(password.toCharArray(), salt, PBKDF2_ITERATIONS, HASH_BYTES);
        return HASH_PREFIX + "$" + PBKDF2_ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(hash);
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int bytes) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, bytes * 8);
            try {
                return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            } finally { spec.clearPassword(); }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Password hashing is unavailable.", e);
        }
    }

    private static boolean isPasswordHash(String value) {
        return value != null && value.startsWith(HASH_PREFIX + "$");
    }

    private static boolean verifyPassword(String password, String stored) {
        if (password == null || stored == null) return false;
        if (!isPasswordHash(stored)) return password.equals(stored); // legacy account; upgraded after login
        try {
            String[] parts = stored.split("\\$", -1);
            if (parts.length != 4 || !HASH_PREFIX.equals(parts[0])) return false;
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = pbkdf2(password.toCharArray(), salt, iterations, expected.length);
            return MessageDigest.isEqual(actual, expected);
        } catch (RuntimeException e) { return false; }
    }

    private static boolean isLegacyPassword(String stored) { return stored != null && !isPasswordHash(stored); }

    public static Connection getConnection() throws SQLException {
        return get();
    }


    
    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            JOptionPane.showMessageDialog(null,
                    "MySQL Connector/J was not found.\n"
                    + "Make sure mysql-connector-j.jar is in the project's lib folder\n"
                    + "and added to the project libraries in NetBeans.",
                    "Missing Database Driver", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static Connection get() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }

    /** Call once at startup. Returns false (and shows a friendly message) if MySQL is unreachable. */
    public static boolean testConnection() {
        try (Connection c = get()) {
            ensureProductImageColumn(c);
            ensureStyleHubUpgradeColumns(c);
            ensureCompletedOrderStatus(c);
            ensureRealWebsiteTables(c);
            cleanInvalidProductImagePaths(c);
            return true;
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null,
                    "Could not connect to the database \"" + DB + "\".\n\n"
                    + "Please check:\n"
                    + "  1. MySQL is running (e.g. started in the XAMPP Control Panel).\n"
                    + "  2. You imported stylehubsystem.sql into phpMyAdmin.\n"
                    + "  3. The username/password in Database.java match your MySQL login.\n\n"
                    + "Details: " + e.getMessage(),
                    "Database Connection Failed", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    // ==================================================================
    //                            USERS
    // ==================================================================

    private static void ensureProductImageColumn(Connection c) throws SQLException {
        String check = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products' AND COLUMN_NAME = 'image_path'";
        try (PreparedStatement ps = c.prepareStatement(check); ResultSet rs = ps.executeQuery()) {
            rs.next();
            if (rs.getInt(1) == 0) {
                try (Statement st = c.createStatement()) {
                    st.executeUpdate("ALTER TABLE products ADD COLUMN image_path VARCHAR(500)");
                }
            }
        }
    }


    /** Adds Completed to existing databases without requiring a manual SQL migration. */
    private static void ensureCompletedOrderStatus(Connection c) throws SQLException {
        // Keep both historical spellings because existing installations may use either.
        try (Statement st = c.createStatement()) {
            st.executeUpdate("ALTER TABLE orders MODIFY status "
                    + "ENUM('Pending','Processing','Shipped','Delivery','Delivered','Completed','Cancelled') "
                    + "DEFAULT 'Pending'");
        }
    }

    private static void ensureStyleHubUpgradeColumns(Connection c) throws SQLException {
        addColumnIfMissing(c, "users", "profile_photo", "VARCHAR(500)");
        addColumnIfMissing(c, "products", "colors", "VARCHAR(500) NOT NULL DEFAULT 'Black'");
        addColumnIfMissing(c, "products", "availability", "VARCHAR(30) NOT NULL DEFAULT 'Available'");
        addColumnIfMissing(c, "cart_items", "color", "VARCHAR(50) NOT NULL DEFAULT 'Black'");
        addColumnIfMissing(c, "users", "is_archived", "TINYINT(1) NOT NULL DEFAULT 0");
        addColumnIfMissing(c, "products", "is_archived", "TINYINT(1) NOT NULL DEFAULT 0");
        addColumnIfMissing(c, "orders", "is_archived", "TINYINT(1) NOT NULL DEFAULT 0");
        addColumnIfMissing(c, "order_items", "color", "VARCHAR(50) NOT NULL DEFAULT 'Black'");
        ensureCartUniqueIndex(c);
    }

    /** Extra tables used by the richer customer experience. Existing data is preserved. */
    private static void ensureRealWebsiteTables(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS wishlist (" +
                    "wishlist_id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "user_id INT NOT NULL, product_id INT NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "UNIQUE KEY uq_wishlist (user_id, product_id))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS product_reviews (" +
                    "review_id INT AUTO_INCREMENT PRIMARY KEY, order_id INT NOT NULL, user_id INT NOT NULL, " +
                    "product_id INT NOT NULL, rating TINYINT NOT NULL, review_text VARCHAR(1000), " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY uq_review (order_id, user_id, product_id))");
        }
    }

    private static void ensureCartUniqueIndex(Connection c) throws SQLException {
        String check = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cart_items' "
                + "AND INDEX_NAME = 'uq_cart_item'";
        try (PreparedStatement ps = c.prepareStatement(check); ResultSet rs = ps.executeQuery()) {
            rs.next();
            if (rs.getInt(1) == 0) {
                try (Statement st = c.createStatement()) {
                    st.executeUpdate("ALTER TABLE cart_items ADD UNIQUE KEY uq_cart_item "
                            + "(user_id, product_id, branch_id, size, color)");
                }
            }
        }
    }

    private static void addColumnIfMissing(Connection c, String table, String column, String definition) throws SQLException {
        // This helper is intentionally restricted to schema upgrades defined by StyleHub.
        // Never pass user input to table/column identifiers.
        String key = table + "." + column;
        Map<String,String> allowed = new HashMap<>();
        allowed.put("users.profile_photo", "VARCHAR(500)");
        allowed.put("users.is_archived", "TINYINT(1) NOT NULL DEFAULT 0");
        allowed.put("products.colors", "VARCHAR(500) NOT NULL DEFAULT 'Black'");
        allowed.put("products.availability", "VARCHAR(30) NOT NULL DEFAULT 'Available'");
        allowed.put("products.is_archived", "TINYINT(1) NOT NULL DEFAULT 0");
        allowed.put("cart_items.color", "VARCHAR(50) NOT NULL DEFAULT 'Black'");
        allowed.put("orders.is_archived", "TINYINT(1) NOT NULL DEFAULT 0");
        allowed.put("order_items.color", "VARCHAR(50) NOT NULL DEFAULT 'Black'");
        String safeDefinition = allowed.get(key);
        if (safeDefinition == null || !safeDefinition.equals(definition)) {
            throw new SQLException("Blocked unexpected schema upgrade: " + key);
        }
        String check = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement ps = c.prepareStatement(check)) {
            ps.setString(1, table); ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                if (rs.getInt(1) == 0) {
                    try (Statement st = c.createStatement()) {
                        st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + safeDefinition);
                    }
                }
            }
        }
    }

    public static String getProfilePhoto(String username) {
        String sql = "SELECT profile_photo FROM users WHERE LOWER(username)=LOWER(?) AND is_archived = 0";
        try (Connection c = get()) {
            ensureStyleHubUpgradeColumns(c);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getString(1) : null; }
            }
        } catch (SQLException e) { showError("loading the profile photo", e); return null; }
    }

    public static void updateProfilePhoto(String username, String path) {
        String sql = "UPDATE users SET profile_photo=? WHERE LOWER(username)=LOWER(?)";
        try (Connection c = get()) {
            ensureStyleHubUpgradeColumns(c);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, path); ps.setString(2, username); ps.executeUpdate();
            }
        } catch (SQLException e) { showError("saving the profile photo", e); }
    }

    /** username (lowercase) -> {password, email, fullName} for all Customer accounts. */

    /**
     * Removes old values that were accidentally saved as Swing object text
     * (for example "javax.swing.ImageIcon@..."). A real file path is kept.
     */
    private static void cleanInvalidProductImagePaths(Connection c) throws SQLException {
        String sql = "UPDATE products SET image_path = NULL "
                   + "WHERE image_path IS NOT NULL "
                   + "AND (image_path LIKE 'javax.swing.%' OR image_path LIKE 'java.awt.%')";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }

    public static Map<String, String[]> loadCustomers() {
        Map<String, String[]> map = new LinkedHashMap<>();
        String sql = "SELECT username, password, email, first_name, middle_initial, last_name, profile_photo "
                   + "FROM users WHERE role = 'Customer' AND is_archived = 0 "
                   + "ORDER BY created_at DESC, user_id DESC";
        try (Connection c = get()) {
            // Make sure newer columns exist even when this method is called
            // before the main dashboard startup/refresh sequence.
            ensureStyleHubUpgradeColumns(c);
            try (PreparedStatement ps = c.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String mi = rs.getString("middle_initial");
                String full = rs.getString("first_name")
                        + (mi == null || mi.isBlank() ? "" : " " + mi.toUpperCase() + ".")
                        + " " + rs.getString("last_name");
                map.put(rs.getString("username").toLowerCase(),
                        new String[]{rs.getString("password"), rs.getString("email"), full,
                                rs.getString("profile_photo")});
            }
            }
        } catch (SQLException e) {
            showError("loading customers", e);
        }
        return map;
    }

    /** Checks credentials. Legacy plaintext passwords are upgraded after a successful login. */
    public static boolean checkLogin(String username, String password, String role) {
        String sql = "SELECT user_id, password FROM users WHERE LOWER(username)=LOWER(?) AND role=? AND is_archived=0";
        try (Connection c=get(); PreparedStatement ps=c.prepareStatement(sql)) {
            ps.setString(1, username); ps.setString(2, role);
            try (ResultSet rs=ps.executeQuery()) {
                if (!rs.next()) return false;
                String stored=rs.getString("password");
                if (!verifyPassword(password, stored)) return false;
                if (isLegacyPassword(stored)) {
                    try (PreparedStatement up=c.prepareStatement("UPDATE users SET password=? WHERE user_id=?")) {
                        up.setString(1, hashPassword(password)); up.setInt(2, rs.getInt("user_id")); up.executeUpdate();
                    }
                }
                return true;
            }
        } catch (SQLException e) { showError("signing in", e); return false; }
    }

    /** Verifies the current password without exposing the stored hash to the UI. */
    public static boolean verifyUserPassword(String username, String password) {
        String sql="SELECT user_id,password FROM users WHERE LOWER(username)=LOWER(?) AND is_archived=0";
        try(Connection c=get();PreparedStatement ps=c.prepareStatement(sql)){
            ps.setString(1,username);
            try(ResultSet rs=ps.executeQuery()){
                if(!rs.next() || !verifyPassword(password,rs.getString("password"))) return false;
                String stored=rs.getString("password");
                if(isLegacyPassword(stored)){
                    try(PreparedStatement up=c.prepareStatement("UPDATE users SET password=? WHERE user_id=?")){
                        up.setString(1,hashPassword(password)); up.setInt(2,rs.getInt("user_id")); up.executeUpdate();
                    }
                }
                return true;
            }
        }catch(SQLException e){showError("verifying the password",e);return false;}
    }

    public static boolean usernameExists(String username) {
        String sql = "SELECT 1 FROM users WHERE LOWER(username)=LOWER(?)";
        try (Connection c=get();PreparedStatement ps=c.prepareStatement(sql)) { ps.setString(1,username); try(ResultSet rs=ps.executeQuery()){return rs.next();} }
        catch(SQLException e){showError("checking the username",e);return false;}
    }

    public static boolean registerUser(String username, String password, String email,
                                       String first, String middleInitial, String last) {
        String sql="INSERT INTO users (username,password,email,first_name,middle_initial,last_name,role) VALUES (?,?,?,?,?,?,'Customer')";
        try(Connection c=get();PreparedStatement ps=c.prepareStatement(sql)){
            ps.setString(1,username); ps.setString(2,hashPassword(password)); ps.setString(3,email);
            ps.setString(4,first); ps.setString(5,middleInitial.isEmpty()?null:middleInitial); ps.setString(6,last); ps.executeUpdate(); return true;
        }catch(SQLException e){showError("creating the account",e);return false;}
    }

    public static void updatePassword(String username, String newPassword) {
        String sql="UPDATE users SET password=? WHERE LOWER(username)=LOWER(?)";
        try(Connection c=get();PreparedStatement ps=c.prepareStatement(sql)){ps.setString(1,hashPassword(newPassword));ps.setString(2,username);ps.executeUpdate();}
        catch(SQLException e){showError("updating the password",e);}
    }

    /** Updates email and the displayed full name (stored in first_name). */
    public static void updateProfile(String username, String fullName, String email) {
        String sql = "UPDATE users SET first_name = ?, middle_initial = NULL, last_name = '',"
                   + " email = ? WHERE LOWER(username) = LOWER(?)";
        try (Connection c = get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, fullName);
            ps.setString(2, email);
            ps.setString(3, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            showError("saving the profile", e);
        }
    }

    /** Archives a user. Nothing is physically deleted from MySQL. */
    public static void deleteUser(String username) {
        archiveUser(username);
    }

    public static void archiveUser(String username) {
        String sql = "UPDATE users SET is_archived=1 WHERE LOWER(username)=LOWER(?)";
        String ordersSql = "UPDATE orders o JOIN users u ON u.user_id=o.user_id SET o.is_archived=1 WHERE LOWER(u.username)=LOWER(?)";
        try (Connection c = get()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(sql); PreparedStatement po = c.prepareStatement(ordersSql)) {
                ps.setString(1, username); ps.executeUpdate();
                po.setString(1, username); po.executeUpdate();
                c.commit();
            } catch (SQLException e) { c.rollback(); throw e; } finally { c.setAutoCommit(true); }
        } catch (SQLException e) { showError("archiving the account", e); }
    }

    public static void restoreUser(String username) {
        String sql = "UPDATE users SET is_archived=0 WHERE LOWER(username)=LOWER(?)";
        String ordersSql = "UPDATE orders o JOIN users u ON u.user_id=o.user_id SET o.is_archived=0 WHERE LOWER(u.username)=LOWER(?)";
        try (Connection c=get()) { c.setAutoCommit(false); try (PreparedStatement ps=c.prepareStatement(sql); PreparedStatement po=c.prepareStatement(ordersSql)) { ps.setString(1, username); ps.executeUpdate(); po.setString(1, username); po.executeUpdate(); c.commit(); } catch(SQLException e){c.rollback();throw e;} finally{c.setAutoCommit(true);} }
        catch(SQLException e){ showError("restoring the account", e); }
    }

    public static ArrayList<Object[]> loadArchivedUsers() {
        ArrayList<Object[]> list=new ArrayList<>();
        String sql="SELECT username, first_name, middle_initial, last_name, email FROM users WHERE role='Customer' AND is_archived=1 ORDER BY created_at DESC, user_id DESC";
        try(Connection c=get(); PreparedStatement ps=c.prepareStatement(sql); ResultSet rs=ps.executeQuery()){
            while(rs.next()){ String mi=rs.getString("middle_initial"); String full=rs.getString("first_name")+(mi==null||mi.isBlank()?"":" "+mi.toUpperCase()+".")+" "+rs.getString("last_name"); list.add(new Object[]{rs.getString(1),full,rs.getString(5)}); }
        }catch(SQLException e){showError("loading archived customers",e);} return list;
    }
    

    // ==================================================================
    //                       PRODUCTS & INVENTORY
    // ==================================================================

    /**
     * Loads all products with their stock per branch.
     * Row layout per entry: {productId, name, category, price, kinaweStock, opolStock}
     */
    public static ArrayList<Object[]> loadProducts() {
        ArrayList<Object[]> list = new ArrayList<>();
        String sql = "SELECT p.product_id, p.product_name, p.category, p.price, p.image_path, p.colors, p.availability, p.color_images, "
                   + "COALESCE(MAX(CASE WHEN b.branch_name LIKE 'Kinawe%' THEN i.stock_quantity END), 0) AS kinawe, "
                   + "COALESCE(MAX(CASE WHEN b.branch_name LIKE 'Opol%' THEN i.stock_quantity END), 0) AS opol "
                   + "FROM products p "
                   + "LEFT JOIN inventory i ON i.product_id = p.product_id "
                   + "LEFT JOIN branches b ON b.branch_id = i.branch_id "
                   + "WHERE p.is_archived = 0 "
                   + "GROUP BY p.product_id, p.product_name, p.category, p.price, p.image_path, p.colors, p.availability, p.color_images "
                   + "ORDER BY p.created_at DESC, p.product_id DESC";
        try (Connection c = get();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Object[]{
                        rs.getInt("product_id"),
                        rs.getString("product_name"),
                        rs.getString("category"),
                        rs.getDouble("price"),
                        rs.getInt("kinawe"),
                        rs.getInt("opol"),
                        rs.getString("image_path"),
                        rs.getString("colors"),
                        rs.getString("availability"),
                        rs.getString("color_images")});
            }
        } catch (SQLException e) {
            showError("loading products", e);
        }
        return list;
    }

    /** Inserts a product plus its opening stock at both branches. Returns the new product id, or -1. */
    public static int addProduct(String name, String category, double price, int kinawe, int opol) {
        return addProduct(name, category, price, kinawe, opol, null, "Black", "Available");
    }

    public static int addProduct(String name, String category, double price, int kinawe, int opol,
                                 String imagePath) {
        return addProduct(name, category, price, kinawe, opol, imagePath, "Black", "Available");
    }

    public static int addProduct(String name, String category, double price, int kinawe, int opol,
                                 String imagePath, String colors, String availability) {
        return addProduct(name, category, price, kinawe, opol, imagePath, colors, availability, "");
    }
    public static int addProduct(String name, String category, double price, int kinawe, int opol,
                                 String imagePath, String colors, String availability, String colorImages) {
        String insProduct = "INSERT INTO products (product_name, category, price, image_path, colors, availability, color_images) VALUES (?,?,?,?,?,?,?)";
        String insStock = "INSERT INTO inventory (product_id, branch_id, stock_quantity) VALUES (?,?,?)";
        try (Connection c = get()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(insProduct, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name);
                ps.setString(2, category);
                ps.setDouble(3, price);
                ps.setString(4, imagePath);
                ps.setString(5, colors);
                ps.setString(6, availability);
                ps.setString(7, colorImages == null ? "" : colorImages);
                ps.executeUpdate();
                int id;
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    id = keys.getInt(1);
                }
                try (PreparedStatement ps2 = c.prepareStatement(insStock)) {
                    ps2.setInt(1, id);
                    ps2.setInt(2, 1);      // Kinawe (Main Branch)
                    ps2.setInt(3, kinawe);
                    ps2.executeUpdate();
                    ps2.setInt(1, id);
                    ps2.setInt(2, 2);      // Opol Branch
                    ps2.setInt(3, opol);
                    ps2.executeUpdate();
                }
                c.commit();
                return id;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            showError("adding the product", e);
            return -1;
        }
    }

    public static void updateProduct(int productId, String name, String category, double price,
                                     int kinawe, int opol) {
        updateProduct(productId, name, category, price, kinawe, opol, null, "Black", "Available");
    }

    public static void updateProduct(int productId, String name, String category, double price,
                                     int kinawe, int opol, String imagePath) {
        updateProduct(productId, name, category, price, kinawe, opol, imagePath, "Black", "Available");
    }

    public static void updateProduct(int productId, String name, String category, double price,
                                     int kinawe, int opol, String imagePath, String colors, String availability) {
        updateProduct(productId, name, category, price, kinawe, opol, imagePath, colors, availability, "");
    }
    public static void updateProduct(int productId, String name, String category, double price,
                                     int kinawe, int opol, String imagePath, String colors, String availability, String colorImages) {
        String upd = "UPDATE products SET product_name = ?, category = ?, price = ?, image_path = ?, colors = ?, availability = ?, color_images = ? WHERE product_id = ?";
        try (Connection c = get()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(upd)) {
                ps.setString(1, name);
                ps.setString(2, category);
                ps.setDouble(3, price);
                ps.setString(4, imagePath);
                ps.setString(5, colors);
                ps.setString(6, availability);
                ps.setString(7, colorImages == null ? "" : colorImages);
                ps.setInt(8, productId);
                ps.executeUpdate();
                setStock(c, productId, 1, kinawe);
                setStock(c, productId, 2, opol);
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            showError("updating the product", e);
        }
    }

    /** Archives a product. The SQL row and inventory remain intact. */
    public static void deleteProduct(int productId) { archiveProduct(productId); }

    public static void archiveProduct(int productId) {
        String sql="UPDATE products SET is_archived=1 WHERE product_id=?";
        try(Connection c=get(); PreparedStatement ps=c.prepareStatement(sql)){ps.setInt(1,productId);ps.executeUpdate();}
        catch(SQLException e){showError("archiving the product",e);}
    }

    public static void restoreProduct(int productId) {
        String sql="UPDATE products SET is_archived=0 WHERE product_id=?";
        try(Connection c=get(); PreparedStatement ps=c.prepareStatement(sql)){ps.setInt(1,productId);ps.executeUpdate();}
        catch(SQLException e){showError("restoring the product",e);}
    }

    public static ArrayList<Object[]> loadArchivedProducts() {
        ArrayList<Object[]> list=new ArrayList<>();
        String sql="SELECT product_id,product_name,category,price FROM products WHERE is_archived=1 ORDER BY product_id DESC";
        try(Connection c=get();PreparedStatement ps=c.prepareStatement(sql);ResultSet rs=ps.executeQuery()){while(rs.next())list.add(new Object[]{rs.getInt(1),rs.getString(2),rs.getString(3),rs.getDouble(4)});}catch(SQLException e){showError("loading archived products",e);}return list;
    }

    /** Sets the exact stock of a product at a branch (branchIndex 0 = Kinawe, 1 = Opol). */
    public static void setStock(int productId, int branchIndex, int qty) {
        try (Connection c = get()) {
            setStock(c, productId, branchIndex + 1, qty);
        } catch (SQLException e) {
            showError("updating the stock", e);
        }
    }

    private static void setStock(Connection c, int productId, int branchId, int qty) throws SQLException {
        String sql = "INSERT INTO inventory (product_id, branch_id, stock_quantity) VALUES (?,?,?) "
                   + "ON DUPLICATE KEY UPDATE stock_quantity = VALUES(stock_quantity)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setInt(2, branchId);
            ps.setInt(3, qty);
            ps.executeUpdate();
        }
    }

    // ==================================================================
    //                              CART
    // ==================================================================

    /** username -> rows of {productId, branchIndex, size, quantity}. */
    public static Map<String, ArrayList<Object[]>> loadCarts() {
        Map<String, ArrayList<Object[]>> map = new HashMap<>();
        String sql = "SELECT u.username, ci.product_id, ci.branch_id, ci.size, ci.quantity, ci.color "
                   + "FROM cart_items ci JOIN users u ON u.user_id = ci.user_id "
                   + "ORDER BY ci.cart_item_id DESC";
        try (Connection c = get();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String user = rs.getString("username").toLowerCase();
                map.computeIfAbsent(user, k -> new ArrayList<>()).add(new Object[]{
                        rs.getInt("product_id"),
                        rs.getInt("branch_id") - 1,
                        rs.getString("size"),
                        rs.getInt("quantity"),
                        rs.getString("color")});
            }
        } catch (SQLException e) {
            showError("loading carts", e);
        }
        return map;
    }

    public static void saveCartItem(String username, int productId, int branchIndex,
                                    String size, int qty) {
        saveCartItem(username, productId, branchIndex, size, "Black", qty);
    }

    public static void saveCartItem(String username, int productId, int branchIndex,
                                    String size, String color, int qty) {
        String sql = "INSERT INTO cart_items (user_id, product_id, branch_id, size, color, quantity) "
                   + "SELECT u.user_id, ?, ?, ?, ?, ? FROM users u WHERE LOWER(u.username) = LOWER(?) "
                   + "ON DUPLICATE KEY UPDATE quantity = VALUES(quantity), color = VALUES(color)";
        try (Connection c = get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setInt(2, branchIndex + 1);
            ps.setString(3, size);
            ps.setString(4, color);
            ps.setInt(5, qty);
            ps.setString(6, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            showError("saving the cart", e);
        }
    }

    public static void removeCartItem(String username, int productId, String size) {
        removeCartItem(username, productId, size, null);
    }

    public static void removeCartItem(String username, int productId, String size, String color) {
        String sql = "DELETE ci FROM cart_items ci JOIN users u ON u.user_id = ci.user_id "
                   + "WHERE LOWER(u.username) = LOWER(?) AND ci.product_id = ? AND ci.size = ? "
                   + "AND (? IS NULL OR ci.color = ?)";
        try (Connection c = get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setInt(2, productId);
            ps.setString(3, size);
            ps.setString(4, color);
            ps.setString(5, color);
            ps.executeUpdate();
        } catch (SQLException e) {
            showError("removing the cart item", e);
        }
    }

    public static void clearCart(String username) {
        String sql = "DELETE ci FROM cart_items ci JOIN users u ON u.user_id = ci.user_id "
                   + "WHERE LOWER(u.username) = LOWER(?)";
        try (Connection c = get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            showError("clearing the cart", e);
        }
    }

    // ==================================================================
    //                       WISHLIST & REVIEWS
    // ==================================================================
    public static ArrayList<Integer> loadWishlist(String username) {
        ArrayList<Integer> list=new ArrayList<>(); if(username==null||username.isBlank()) return list;
        String sql="SELECT w.product_id FROM wishlist w JOIN users u ON u.user_id=w.user_id WHERE LOWER(u.username)=LOWER(?) ORDER BY w.created_at DESC";
        try(Connection c=get()){ensureRealWebsiteTables(c);try(PreparedStatement ps=c.prepareStatement(sql)){ps.setString(1,username);try(ResultSet rs=ps.executeQuery()){while(rs.next())list.add(rs.getInt(1));}}}catch(SQLException e){showError("loading the wishlist",e);}return list;
    }
    public static boolean addWishlist(String username,int productId){
        String sql="INSERT IGNORE INTO wishlist(user_id,product_id) SELECT user_id,? FROM users WHERE LOWER(username)=LOWER(?)";
        try(Connection c=get()){ensureRealWebsiteTables(c);try(PreparedStatement ps=c.prepareStatement(sql)){ps.setInt(1,productId);ps.setString(2,username);return ps.executeUpdate()>0;}}catch(SQLException e){showError("saving the wishlist",e);return false;}
    }
    public static void removeWishlist(String username,int productId){
        String sql="DELETE w FROM wishlist w JOIN users u ON u.user_id=w.user_id WHERE LOWER(u.username)=LOWER(?) AND w.product_id=?";
        try(Connection c=get()){ensureRealWebsiteTables(c);try(PreparedStatement ps=c.prepareStatement(sql)){ps.setString(1,username);ps.setInt(2,productId);ps.executeUpdate();}}catch(SQLException e){showError("removing the wishlist item",e);}
    }
    public static boolean saveReview(String username,int orderId,int productId,int rating,String text){
        String sql="INSERT INTO product_reviews(order_id,user_id,product_id,rating,review_text) "
                + "SELECT ?,u.user_id,?,?,? FROM users u "
                + "JOIN orders o ON o.order_id=? AND o.user_id=u.user_id AND o.status IN ('Delivered','Completed') "
                + "JOIN order_items oi ON oi.order_id=o.order_id AND oi.product_id=? "
                + "WHERE LOWER(u.username)=LOWER(?) AND u.role='Customer'";
        try(Connection c=get()){
            ensureRealWebsiteTables(c);
            try(PreparedStatement ps=c.prepareStatement(sql)){
                ps.setInt(1,orderId); ps.setInt(2,productId); ps.setInt(3,rating);
                ps.setString(4,text); ps.setInt(5,orderId); ps.setInt(6,productId); ps.setString(7,username);
                return ps.executeUpdate()>0;
            }
        }catch(SQLException e){return false;}
    }

    /** Loads customer ratings/feedback for the admin review center. */
    public static ArrayList<Object[]> loadReviews(){
        ArrayList<Object[]> list=new ArrayList<>();
        String sql="SELECT r.review_id,r.order_id,u.username, "
                + "TRIM(CONCAT(COALESCE(u.first_name,''),' ',COALESCE(u.last_name,''))) AS customer, "
                + "p.product_name AS product,r.rating,COALESCE(r.review_text,'') AS review_text,r.created_at "
                + "FROM product_reviews r "
                + "JOIN users u ON u.user_id=r.user_id "
                + "JOIN products p ON p.product_id=r.product_id "
                + "ORDER BY r.created_at DESC,r.review_id DESC";
        try(Connection c=get()){
            ensureRealWebsiteTables(c);
            try(PreparedStatement ps=c.prepareStatement(sql);ResultSet rs=ps.executeQuery()){
                while(rs.next()) list.add(new Object[]{rs.getInt("review_id"),rs.getInt("order_id"),
                    rs.getString("username"),rs.getString("customer").trim(),rs.getString("product"),
                    rs.getInt("rating"),rs.getString("review_text"),rs.getTimestamp("created_at")});
            }
        }catch(SQLException e){showError("loading customer reviews",e);}
        return list;
    }

    // ==================================================================
    //                             ORDERS
    // ==================================================================

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMM dd, yyyy");

    /**
     * Loads all orders with their items.
     * Row layout: {orderId, username, branchIndex, date, method, address, payment, status,
     *              items: ArrayList of {productId, size, qty, unitPrice}}
     */
    @SuppressWarnings("unchecked")
    public static ArrayList<Object[]> loadOrders() {
        ArrayList<Object[]> list = new ArrayList<>();
        String orderSql = "SELECT o.order_id, u.username, o.branch_id, o.order_date, o.order_method, "
                        + "o.delivery_address, o.payment_method, o.status "
                        + "FROM orders o JOIN users u ON u.user_id = o.user_id "
                        + "WHERE o.is_archived = 0 AND u.is_archived = 0 "
                        + "ORDER BY o.order_id DESC";
        String itemSql = "SELECT product_id, size, quantity, unit_price, color FROM order_items WHERE order_id = ?";
        try (Connection c = get();
             PreparedStatement ps = c.prepareStatement(orderSql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int orderId = rs.getInt("order_id");
                ArrayList<Object[]> items = new ArrayList<>();
                try (PreparedStatement ps2 = c.prepareStatement(itemSql)) {
                    ps2.setInt(1, orderId);
                    try (ResultSet ri = ps2.executeQuery()) {
                        while (ri.next()) {
                            items.add(new Object[]{
                                    ri.getInt("product_id"),
                                    ri.getString("size"),
                                    ri.getInt("quantity"),
                                    ri.getDouble("unit_price"),
                                    ri.getString("color")});
                        }
                    }
                }
                Timestamp ts = rs.getTimestamp("order_date");
                LocalDateTime dt = ts != null ? ts.toLocalDateTime() : LocalDateTime.now();
                String address = rs.getString("delivery_address");
                list.add(new Object[]{
                        orderId,
                        rs.getString("username").toLowerCase(),
                        rs.getInt("branch_id") - 1,
                        dt.format(DATE_FMT),
                        rs.getString("order_method"),
                        address == null ? "" : address,
                        rs.getString("payment_method"),
                        rs.getString("status"),
                        items});
            }
        } catch (SQLException e) {
            showError("loading orders", e);
        }
        return list;
    }

    /**
     * Places an order: inserts the order and its items and deducts the stock,
     * all in one transaction. Returns the new order id, or -1 on failure.
     *
     * items rows: {productId, size, qty, unitPrice}
     */
    public static int placeOrder(String username, int branchIndex, String method, String address,
                                 String payment, double total, ArrayList<Object[]> items) {
        String insOrder = "INSERT INTO orders (user_id, branch_id, order_method, delivery_address,"
                        + " payment_method, status, total_amount) "
                        + "SELECT u.user_id, ?, ?, ?, ?, 'Pending', ? FROM users u "
                        + "WHERE LOWER(u.username) = LOWER(?)";
        String insItem = "INSERT INTO order_items (order_id, product_id, size, color, quantity, unit_price,"
                       + " subtotal) VALUES (?,?,?,?,?,?,?)";
        String decStock = "UPDATE inventory SET stock_quantity = stock_quantity - ? "
                        + "WHERE product_id = ? AND branch_id = ?";
        try (Connection c = get()) {
            c.setAutoCommit(false);
            try {
                int orderId;
                try (PreparedStatement ps = c.prepareStatement(insOrder, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, branchIndex + 1);
                    ps.setString(2, method);
                    ps.setString(3, address.isEmpty() ? null : address);
                    ps.setString(4, payment);
                    ps.setDouble(5, total);
                    ps.setString(6, username);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        orderId = keys.getInt(1);
                    }
                }
                try (PreparedStatement psi = c.prepareStatement(insItem);
                     PreparedStatement pss = c.prepareStatement(decStock)) {
                    for (Object[] it : items) {
                        int productId = (Integer) it[0];
                        int qty = (Integer) it[2];
                        double unit = (Double) it[3];
                        psi.setInt(1, orderId);
                        psi.setInt(2, productId);
                        psi.setString(3, (String) it[1]);
                        psi.setString(4, it.length > 4 ? (String) it[4] : "Black");
                        psi.setInt(5, qty);
                        psi.setDouble(6, unit);
                        psi.setDouble(7, unit * qty);
                        psi.executeUpdate();
                        pss.setInt(1, qty);
                        pss.setInt(2, productId);
                        pss.setInt(3, branchIndex + 1);
                        pss.executeUpdate();
                    }
                }
                c.commit();
                return orderId;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            showError("placing the order", e);
            return -1;
        }
    }

    public static void updateOrderStatus(int orderId, String status) {
        String sql = "UPDATE orders SET status = ? WHERE order_id = ?";
        try (Connection c = get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, orderId);
            ps.executeUpdate();
        } catch (SQLException e) {
            showError("updating the order status", e);
        }
    }

    public static void updateOrderDetails(int orderId, String method, String address, String payment) {
        String sql = "UPDATE orders SET order_method = ?, delivery_address = ?, payment_method = ? "
                   + "WHERE order_id = ?";
        try (Connection c = get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, method);
            ps.setString(2, address.isEmpty() ? null : address);
            ps.setString(3, payment);
            ps.setInt(4, orderId);
            ps.executeUpdate();
        } catch (SQLException e) {
            showError("updating the order", e);
        }
    }

    /** Restores the stock of a live order's items at its branch. */
    public static void restoreStock(int orderId) {
        String sql = "UPDATE inventory i JOIN order_items oi ON oi.product_id = i.product_id "
                   + "JOIN orders o ON o.order_id = oi.order_id AND o.branch_id = i.branch_id "
                   + "SET i.stock_quantity = i.stock_quantity + oi.quantity "
                   + "WHERE oi.order_id = ?";
        try (Connection c = get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.executeUpdate();
        } catch (SQLException e) {
            showError("restoring the stock", e);
        }
    }

    public static void deleteOrder(int orderId) { archiveOrder(orderId); }

    public static void archiveOrder(int orderId) {
        String sql="UPDATE orders SET is_archived=1 WHERE order_id=?";
        try(Connection c=get();PreparedStatement ps=c.prepareStatement(sql)){ps.setInt(1,orderId);ps.executeUpdate();}
        catch(SQLException e){showError("archiving the order",e);}
    }

    public static void restoreOrder(int orderId) {
        String sql="UPDATE orders SET is_archived=0 WHERE order_id=?";
        try(Connection c=get();PreparedStatement ps=c.prepareStatement(sql)){ps.setInt(1,orderId);ps.executeUpdate();}
        catch(SQLException e){showError("restoring the order",e);}
    }

    public static ArrayList<Object[]> loadArchivedOrders() {
        ArrayList<Object[]> list=new ArrayList<>();
        String sql="SELECT o.order_id,u.username,o.order_date,o.status,o.total_amount FROM orders o JOIN users u ON u.user_id=o.user_id WHERE o.is_archived=1 ORDER BY o.order_id DESC";
        try(Connection c=get();PreparedStatement ps=c.prepareStatement(sql);ResultSet rs=ps.executeQuery()){while(rs.next())list.add(new Object[]{rs.getInt(1),rs.getString(2),rs.getTimestamp(3),rs.getString(4),rs.getDouble(5)});}catch(SQLException e){showError("loading archived orders",e);}return list;
    }

    /** Loads only the signed-in customer's archived orders. */
    public static ArrayList<Object[]> loadArchivedOrdersForUser(String username) {
        ArrayList<Object[]> list=new ArrayList<>();
        String sql="SELECT o.order_id,o.order_date,o.status,o.total_amount "
                + "FROM orders o JOIN users u ON u.user_id=o.user_id "
                + "WHERE o.is_archived=1 AND u.is_archived=0 AND LOWER(u.username)=LOWER(?) "
                + "ORDER BY o.order_id DESC";
        try(Connection c=get();PreparedStatement ps=c.prepareStatement(sql)){
            ps.setString(1, username);
            try(ResultSet rs=ps.executeQuery()){
                while(rs.next()) list.add(new Object[]{rs.getInt(1),rs.getTimestamp(2),rs.getString(3),rs.getDouble(4)});
            }
        }catch(SQLException e){showError("loading your archived orders",e);}
        return list;
    }

    /** Restores an archived order only when it belongs to the signed-in customer. */
    public static boolean restoreOrderForUser(int orderId, String username) {
        String sql="UPDATE orders o JOIN users u ON u.user_id=o.user_id "
                + "SET o.is_archived=0 WHERE o.order_id=? AND LOWER(u.username)=LOWER(?)";
        try(Connection c=get();PreparedStatement ps=c.prepareStatement(sql)){
            ps.setInt(1, orderId);
            ps.setString(2, username);
            return ps.executeUpdate()>0;
        }catch(SQLException e){showError("restoring your archived order",e);return false;}
    }

    /** Report rows: orderId,date,customer,branch,product,category,qty,unitPrice,subtotal,status. */
    public static ArrayList<Object[]> loadSalesReport(String from, String to, String search, String category) {
        ArrayList<Object[]> list=new ArrayList<>();
        StringBuilder sql=new StringBuilder("SELECT o.order_id,o.order_date,u.username,b.branch_name,p.product_name,p.category,oi.quantity,oi.unit_price,oi.subtotal,o.status FROM orders o JOIN users u ON u.user_id=o.user_id JOIN branches b ON b.branch_id=o.branch_id JOIN order_items oi ON oi.order_id=o.order_id JOIN products p ON p.product_id=oi.product_id WHERE o.is_archived=0 AND u.is_archived=0 AND o.status<>'Cancelled'");
        ArrayList<Object> args=new ArrayList<>();
        if(from!=null&&!from.isBlank()){sql.append(" AND DATE(o.order_date)>=?");args.add(from);}
        if(to!=null&&!to.isBlank()){sql.append(" AND DATE(o.order_date)<=?");args.add(to);}
        if(category!=null&&!category.equals("All Categories")){sql.append(" AND LOWER(REPLACE(TRIM(p.category),' ',''))=LOWER(REPLACE(TRIM(?),' ',''))");args.add(category);}
        if(search!=null&&!search.isBlank()){sql.append(" AND (CAST(o.order_id AS CHAR) LIKE ? OR LOWER(u.username) LIKE ? OR LOWER(p.product_name) LIKE ? OR LOWER(p.category) LIKE ?)");String q="%"+search.toLowerCase()+"%";args.add(q);args.add(q);args.add(q);args.add(q);}
        sql.append(" ORDER BY o.order_date DESC,o.order_id DESC");
        try(Connection c=get();PreparedStatement ps=c.prepareStatement(sql.toString())){for(int i=0;i<args.size();i++)ps.setObject(i+1,args.get(i));try(ResultSet rs=ps.executeQuery()){while(rs.next())list.add(new Object[]{rs.getInt(1),rs.getTimestamp(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getInt(7),rs.getDouble(8),rs.getDouble(9),rs.getString(10)});}}catch(SQLException e){showError("loading the sales report",e);}return list;
    }

    /** Report rows: orderId,date,customer,branch,method,payment,status,itemCount,total,categoryText. */
    public static ArrayList<Object[]> loadOrderReport(String from, String to, String search, String category) {
        ArrayList<Object[]> list=new ArrayList<>();
        StringBuilder sql=new StringBuilder("SELECT o.order_id,o.order_date,u.username,b.branch_name,o.order_method,o.payment_method,o.status,COALESCE(SUM(oi.quantity),0),o.total_amount,COALESCE(GROUP_CONCAT(DISTINCT p.category ORDER BY p.category SEPARATOR ', '),'') FROM orders o JOIN users u ON u.user_id=o.user_id JOIN branches b ON b.branch_id=o.branch_id LEFT JOIN order_items oi ON oi.order_id=o.order_id LEFT JOIN products p ON p.product_id=oi.product_id WHERE o.is_archived=0 AND u.is_archived=0");
        ArrayList<Object> args=new ArrayList<>();
        if(from!=null&&!from.isBlank()){sql.append(" AND DATE(o.order_date)>=?");args.add(from);}
        if(to!=null&&!to.isBlank()){sql.append(" AND DATE(o.order_date)<=?");args.add(to);}
        if(category!=null&&!category.equals("All Categories")){sql.append(" AND EXISTS (SELECT 1 FROM order_items xoi JOIN products xp ON xp.product_id=xoi.product_id WHERE xoi.order_id=o.order_id AND LOWER(REPLACE(TRIM(xp.category),' ',''))=LOWER(REPLACE(TRIM(?),' ','')))");args.add(category);}
        if(search!=null&&!search.isBlank()){sql.append(" AND (CAST(o.order_id AS CHAR) LIKE ? OR LOWER(u.username) LIKE ? OR EXISTS (SELECT 1 FROM order_items soi JOIN products sp ON sp.product_id=soi.product_id WHERE soi.order_id=o.order_id AND LOWER(sp.product_name) LIKE ?))");String q="%"+search.toLowerCase()+"%";args.add(q);args.add(q);args.add(q);}
        sql.append(" GROUP BY o.order_id,o.order_date,u.username,b.branch_name,o.order_method,o.payment_method,o.status,o.total_amount ORDER BY o.order_date DESC,o.order_id DESC");
        try(Connection c=get();PreparedStatement ps=c.prepareStatement(sql.toString())){for(int i=0;i<args.size();i++)ps.setObject(i+1,args.get(i));try(ResultSet rs=ps.executeQuery()){while(rs.next())list.add(new Object[]{rs.getInt(1),rs.getTimestamp(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getInt(8),rs.getDouble(9),rs.getString(10)});}}catch(SQLException e){showError("loading the order report",e);}return list;
    }

    // ==================================================================

    private static void showError(String action, SQLException e) {
        e.printStackTrace();
        JOptionPane.showMessageDialog(null,
                "A database problem occurred while " + action + ".\n\n" + e.getMessage(),
                "Database Error", JOptionPane.ERROR_MESSAGE);
    }
}
