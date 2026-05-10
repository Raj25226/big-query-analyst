-- ============================================================
-- BigQuery Sample Data Setup — Sales Dataset
-- ============================================================
-- Run these queries in the BigQuery Console:
-- https://console.cloud.google.com/bigquery
--
-- Replace YOUR_PROJECT_ID with your actual GCP project ID
-- ============================================================


-- ── Step 1: Create Dataset ──────────────────────────────────
-- (Run this via CLI instead: bq mk --dataset --location=US YOUR_PROJECT_ID:sample_sales)
-- Or create it from the BigQuery Console UI.


-- ── Step 2: Customers Table ─────────────────────────────────

CREATE TABLE `aesthetic-kite-456610-b9.sample_sales.customers` (
  customer_id   INT64 NOT NULL,
  name          STRING,
  email         STRING,
  city          STRING,
  country       STRING,
  segment       STRING,
  created_at    DATE
);

INSERT INTO `aesthetic-kite-456610-b9.sample_sales.customers` (customer_id, name, email, city, country, segment, created_at)
VALUES
  (1,  'Aditi Sharma',      'aditi@example.com',    'Mumbai',         'India',   'Enterprise',  '2024-01-15'),
  (2,  'John Smith',        'john@example.com',     'New York',       'USA',     'SMB',         '2024-02-20'),
  (3,  'Priya Patel',       'priya@example.com',    'Bangalore',      'India',   'Enterprise',  '2024-01-10'),
  (4,  'Emily Chen',        'emily@example.com',    'San Francisco',  'USA',     'Startup',     '2024-03-05'),
  (5,  'Rahul Verma',       'rahul@example.com',    'Delhi',          'India',   'SMB',         '2024-04-12'),
  (6,  'Sarah Johnson',     'sarah@example.com',    'London',         'UK',      'Enterprise',  '2024-02-28'),
  (7,  'Amit Kumar',        'amit@example.com',     'Chennai',        'India',   'Startup',     '2024-05-01'),
  (8,  'Maria Garcia',      'maria@example.com',    'Madrid',         'Spain',   'SMB',         '2024-03-15'),
  (9,  'Deepak Singh',      'deepak@example.com',   'Pune',           'India',   'Enterprise',  '2024-06-10'),
  (10, 'Lisa Wang',         'lisa@example.com',     'Toronto',        'Canada',  'Enterprise',  '2024-01-25'),
  (11, 'Ravi Iyer',         'ravi@example.com',     'Hyderabad',      'India',   'SMB',         '2024-07-01'),
  (12, 'James Wilson',      'james@example.com',    'Chicago',        'USA',     'Enterprise',  '2024-03-22'),
  (13, 'Neha Gupta',        'neha@example.com',     'Kolkata',        'India',   'Startup',     '2024-08-05'),
  (14, 'Carlos Rivera',     'carlos@example.com',   'Mexico City',    'Mexico',  'SMB',         '2024-04-18'),
  (15, 'Ananya Reddy',      'ananya@example.com',   'Bangalore',      'India',   'Enterprise',  '2024-05-30');


-- ── Step 3: Products Table ──────────────────────────────────

CREATE TABLE `aesthetic-kite-456610-b9.sample_sales.products` (
  product_id    INT64 NOT NULL,
  name          STRING,
  category      STRING,
  price         FLOAT64,
  cost          FLOAT64
);

INSERT INTO `aesthetic-kite-456610-b9.sample_sales.products` (product_id, name, category, price, cost)
VALUES
  (101, 'Analytics Dashboard Pro',   'Software',        999.00,   200.00),
  (102, 'Data Pipeline Starter',     'Software',        499.00,   100.00),
  (103, 'Cloud Storage 1TB',         'Infrastructure',  120.00,    40.00),
  (104, 'ML Model Training Kit',     'AI/ML',          2499.00,   800.00),
  (105, 'API Gateway Enterprise',    'Infrastructure',  799.00,   250.00),
  (106, 'Security Audit Tool',       'Security',       1499.00,   500.00),
  (107, 'Real-time Streaming',       'Software',       1299.00,   400.00),
  (108, 'Database Optimizer',        'Software',        699.00,   150.00),
  (109, 'Identity Manager',          'Security',        899.00,   300.00),
  (110, 'Auto-scaling Engine',       'Infrastructure',  599.00,   180.00);


-- ── Step 4: Orders Table ────────────────────────────────────

CREATE TABLE `aesthetic-kite-456610-b9.sample_sales.orders` (
  order_id      INT64 NOT NULL,
  customer_id   INT64,
  product_id    INT64,
  quantity      INT64,
  total_amount  FLOAT64,
  order_date    DATE,
  status        STRING,
  region        STRING
);

INSERT INTO `aesthetic-kite-456610-b9.sample_sales.orders` (order_id, customer_id, product_id, quantity, total_amount, order_date, status, region)
VALUES
  -- July 2024
  (1001, 1,  101, 2,  1998.00, '2024-07-01', 'completed', 'APAC'),
  (1002, 2,  104, 1,  2499.00, '2024-07-03', 'completed', 'Americas'),
  (1003, 3,  102, 5,  2495.00, '2024-07-05', 'completed', 'APAC'),
  (1004, 4,  107, 1,  1299.00, '2024-07-08', 'completed', 'Americas'),
  (1005, 5,  103, 10, 1200.00, '2024-07-10', 'pending',   'APAC'),
  (1006, 6,  106, 2,  2998.00, '2024-07-12', 'completed', 'EMEA'),
  (1007, 7,  101, 1,   999.00, '2024-07-15', 'cancelled', 'APAC'),
  (1008, 1,  105, 3,  2397.00, '2024-07-18', 'completed', 'APAC'),
  (1009, 8,  108, 2,  1398.00, '2024-07-20', 'completed', 'EMEA'),
  (1010, 3,  104, 1,  2499.00, '2024-07-22', 'completed', 'APAC'),
  (1011, 9,  107, 4,  5196.00, '2024-07-25', 'completed', 'APAC'),
  (1012, 10, 106, 1,  1499.00, '2024-07-28', 'pending',   'Americas'),

  -- August 2024
  (1013, 2,  101, 1,   999.00, '2024-08-01', 'completed', 'Americas'),
  (1014, 6,  102, 3,  1497.00, '2024-08-05', 'completed', 'EMEA'),
  (1015, 4,  103, 5,   600.00, '2024-08-08', 'completed', 'Americas'),
  (1016, 1,  104, 1,  2499.00, '2024-08-10', 'completed', 'APAC'),
  (1017, 5,  105, 2,  1598.00, '2024-08-12', 'cancelled', 'APAC'),
  (1018, 3,  106, 1,  1499.00, '2024-08-15', 'completed', 'APAC'),
  (1019, 9,  101, 3,  2997.00, '2024-08-18', 'completed', 'APAC'),
  (1020, 10, 108, 1,   699.00, '2024-08-20', 'completed', 'Americas'),

  -- September 2024
  (1021, 11, 109, 2,  1798.00, '2024-09-01', 'completed', 'APAC'),
  (1022, 12, 104, 1,  2499.00, '2024-09-04', 'completed', 'Americas'),
  (1023, 13, 102, 3,  1497.00, '2024-09-07', 'completed', 'APAC'),
  (1024, 14, 110, 5,  2995.00, '2024-09-10', 'completed', 'Americas'),
  (1025, 15, 101, 2,  1998.00, '2024-09-13', 'completed', 'APAC'),
  (1026, 1,  107, 1,  1299.00, '2024-09-16', 'completed', 'APAC'),
  (1027, 3,  109, 1,   899.00, '2024-09-19', 'pending',   'APAC'),
  (1028, 6,  104, 2,  4998.00, '2024-09-22', 'completed', 'EMEA'),
  (1029, 9,  110, 3,  1797.00, '2024-09-25', 'completed', 'APAC'),
  (1030, 12, 106, 1,  1499.00, '2024-09-28', 'completed', 'Americas'),

  -- October 2024
  (1031, 2,  107, 2,  2598.00, '2024-10-02', 'completed', 'Americas'),
  (1032, 5,  109, 1,   899.00, '2024-10-05', 'completed', 'APAC'),
  (1033, 7,  104, 1,  2499.00, '2024-10-08', 'completed', 'APAC'),
  (1034, 8,  101, 1,   999.00, '2024-10-11', 'completed', 'EMEA'),
  (1035, 14, 105, 2,  1598.00, '2024-10-14', 'completed', 'Americas'),
  (1036, 11, 108, 3,  2097.00, '2024-10-17', 'completed', 'APAC'),
  (1037, 4,  106, 1,  1499.00, '2024-10-20', 'cancelled', 'Americas'),
  (1038, 13, 103, 8,   960.00, '2024-10-23', 'completed', 'APAC'),
  (1039, 15, 107, 1,  1299.00, '2024-10-26', 'completed', 'APAC'),
  (1040, 10, 102, 2,   998.00, '2024-10-29', 'completed', 'Americas');


-- ── Step 5: Verify Data ─────────────────────────────────────

-- Run these to confirm everything loaded correctly:

SELECT 'customers' AS table_name, COUNT(*) AS row_count FROM `aesthetic-kite-456610-b9.sample_sales.customers`
UNION ALL
SELECT 'products',  COUNT(*) FROM `aesthetic-kite-456610-b9.sample_sales.products`
UNION ALL
SELECT 'orders',    COUNT(*) FROM `aesthetic-kite-456610-b9.sample_sales.orders`;

-- Expected result:
-- customers  → 15 rows
-- products   → 10 rows
-- orders     → 40 rows
