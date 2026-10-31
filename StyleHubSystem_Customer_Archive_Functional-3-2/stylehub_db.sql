CREATE DATABASE IF NOT EXISTS stylehub_db;
USE stylehub_db;

CREATE TABLE IF NOT EXISTS users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100) UNIQUE,
    profile_photo VARCHAR(500),
    first_name VARCHAR(50),
    middle_initial VARCHAR(5),
    last_name VARCHAR(50),
    role ENUM('Admin','Customer') NOT NULL DEFAULT 'Customer',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_archived TINYINT(1) NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS branches (
    branch_id INT AUTO_INCREMENT PRIMARY KEY,
    branch_name VARCHAR(100) NOT NULL UNIQUE,
    branch_type VARCHAR(50),
    address VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS products (
    product_id INT AUTO_INCREMENT PRIMARY KEY,
    product_name VARCHAR(150) NOT NULL,
    category VARCHAR(50) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    image_path VARCHAR(500),
    colors VARCHAR(500) NOT NULL DEFAULT 'Black',
    color_images TEXT,
    availability VARCHAR(30) NOT NULL DEFAULT 'Available',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_archived TINYINT(1) NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS inventory (
    inventory_id INT AUTO_INCREMENT PRIMARY KEY,
    product_id INT NOT NULL,
    branch_id INT NOT NULL,
    stock_quantity INT NOT NULL DEFAULT 0,
    UNIQUE(product_id, branch_id),
    FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE,
    FOREIGN KEY (branch_id) REFERENCES branches(branch_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS orders (
    order_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    branch_id INT NOT NULL,
    order_date DATETIME DEFAULT CURRENT_TIMESTAMP,
    order_method ENUM('Pick-up','Delivery') NOT NULL,
    delivery_address VARCHAR(255),
    payment_method ENUM('Cash on Delivery','GCash','Credit/Debit Card') NOT NULL,
    status ENUM('Pending','Processing','Shipped','Delivered','Completed','Cancelled') DEFAULT 'Pending',
    total_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
    is_archived TINYINT(1) NOT NULL DEFAULT 0,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS order_items (
    order_item_id INT AUTO_INCREMENT PRIMARY KEY,
    order_id INT NOT NULL,
    product_id INT NOT NULL,
    size VARCHAR(10) NOT NULL,
    color VARCHAR(50) NOT NULL DEFAULT 'Black',
    quantity INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL,
    FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(product_id)
);

CREATE TABLE IF NOT EXISTS cart_items (
    cart_item_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    product_id INT NOT NULL,
    branch_id INT NOT NULL,
    size VARCHAR(10) NOT NULL,
    quantity INT NOT NULL,
    color VARCHAR(50) NOT NULL DEFAULT 'Black',
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE,
    FOREIGN KEY (branch_id) REFERENCES branches(branch_id) ON DELETE CASCADE,
    UNIQUE KEY uq_cart_item (user_id, product_id, branch_id, size, color)
);

INSERT IGNORE INTO branches(branch_id,branch_name,branch_type,address) VALUES
(1,'Kinawe (Main Branch)','Main Branch','Kinawe'),
(2,'Opol Branch','Branch','Opol');

INSERT IGNORE INTO users(username,password,email,first_name,last_name,role)
VALUES('admin','pbkdf2_sha256$120000$hAw2SZh7YzYbVt9o6aySvg==$a4fx8v5Bw2F5gkeieVoBXpXaw6e0/V8+L+qF85OK1Hw=','admin@stylehub.com','StyleHub','Admin','Admin');

INSERT IGNORE INTO products(product_id,product_name,category,price) VALUES
(1,'Classic White Tee','Tops',499.00),
(2,'Oversized Graphic Tee','Tops',650.00),
(3,'Linen Button-Up Shirt','Tops',899.00),
(4,'Slim Fit Jeans','Bottoms',1299.00),
(5,'Cargo Pants','Bottoms',1199.00),
(6,'Pleated Midi Skirt','Bottoms',950.00),
(7,'Denim Jacket','Outerwear',1799.00),
(8,'Hooded Sweatshirt','Outerwear',1499.00),
(9,'Floral Summer Dress','Dresses',1350.00),
(10,'Satin Slip Dress','Dresses',1650.00),
(11,'Canvas Tote Bag','Accessories',450.00),
(12,'Leather Belt','Accessories',599.00);

UPDATE products SET colors='White, Black, Beige', availability='Available' WHERE product_id=1;
UPDATE products SET colors='Black, White, Gray', availability='Available' WHERE product_id=2;
UPDATE products SET colors='White, Blue, Beige', availability='Available' WHERE product_id=3;
UPDATE products SET colors='Blue, Black', availability='Available' WHERE product_id=4;
UPDATE products SET colors='Black, Khaki, Olive', availability='Available' WHERE product_id=5;
UPDATE products SET colors='Black, Cream, Brown', availability='Available' WHERE product_id=6;
UPDATE products SET colors='Blue, Black', availability='Available' WHERE product_id=7;
UPDATE products SET colors='Black, Gray, Beige', availability='Available' WHERE product_id=8;
UPDATE products SET colors='Floral Pink, Blue, Cream', availability='Available' WHERE product_id=9;
UPDATE products SET colors='Black, Red, Champagne', availability='Available' WHERE product_id=10;
UPDATE products SET colors='Natural, Black', availability='Available' WHERE product_id=11;
UPDATE products SET colors='Brown, Black', availability='Available' WHERE product_id=12;

INSERT IGNORE INTO inventory(product_id,branch_id,stock_quantity) VALUES
(1,1,40),(1,2,25),(2,1,30),(2,2,4),(3,1,18),(3,2,12),
(4,1,22),(4,2,15),(5,1,3),(5,2,10),(6,1,14),(6,2,9),
(7,1,10),(7,2,6),(8,1,20),(8,2,0),(9,1,16),(9,2,11),
(10,1,8),(10,2,5),(11,1,35),(11,2,28),(12,1,12),(12,2,7);
