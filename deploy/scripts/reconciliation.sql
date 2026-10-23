-- Reconciliation queries (10-DEPLOYMENT section 9.4): run after any restore and every week of the parallel run.
-- Every query must return zero rows.

-- stock: the balance projection equals the movement ledger
SELECT 'stock' AS check_name, product_type, location, ledger_qty, balance_qty FROM (
  SELECT m.product_type, m.location, SUM(m.qty) AS ledger_qty, COALESCE(b.quantity_kg, 0) AS balance_qty
  FROM (SELECT to_product_type AS product_type, to_location AS location, quantity_kg AS qty FROM material_movements
        UNION ALL
        SELECT product_type, from_location AS location, -quantity_kg AS qty FROM material_movements) m
  LEFT JOIN inventory_balances b ON b.product_type = m.product_type AND b.location = m.location
  WHERE m.location IN ('RAW_STOCK', 'INTERNAL_WIP', 'EXTERNAL_WIP', 'FINISHED_STOCK')
  GROUP BY m.product_type, m.location, b.quantity_kg) x
WHERE ledger_qty <> balance_qty;

-- money: no account is negative
SELECT 'money' AS check_name, financial_account_id, SUM(CASE direction WHEN 'IN' THEN amount ELSE -amount END) AS balance
FROM financial_transactions GROUP BY financial_account_id
HAVING SUM(CASE direction WHEN 'IN' THEN amount ELSE -amount END) < 0;

-- allocations never exceed their payment
SELECT 'allocation' AS check_name, p.id FROM payments p
JOIN (SELECT payment_id, SUM(allocated_amount) AS allocated FROM (
        SELECT payment_id, allocated_amount FROM customer_payment_allocations
        UNION ALL SELECT payment_id, allocated_amount FROM supplier_payment_allocations
        UNION ALL SELECT payment_id, allocated_amount FROM manufacturer_payment_allocations
        UNION ALL SELECT payment_id, allocated_amount FROM worker_payment_allocations) all_alloc
      GROUP BY payment_id) a ON a.payment_id = p.id
WHERE a.allocated > p.amount;
