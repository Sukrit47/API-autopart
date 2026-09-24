package com.pkshop.domain.sales.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Repository
public class DashboardRepository {

    private final JdbcTemplate jdbc;

    public DashboardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String SALES_STATUSES =
            "('PAID','PACKING','SHIPPED','DELIVERED','PARTIALLY_REFUNDED')";

    private boolean hasDateRange(LocalDate from, LocalDate to) {
        return from != null && to != null;
    }

    public long countPaidOrders(LocalDate from, LocalDate to) {

        String sql = """
            SELECT COUNT(*)
            FROM orders
            WHERE status IN
        """ + SALES_STATUSES;

        if (hasDateRange(from, to)) {

            sql += " AND DATE(created_at) BETWEEN ? AND ?";

            return jdbc.queryForObject(
                    sql,
                    Long.class,
                    Date.valueOf(from),
                    Date.valueOf(to)
            );
        }

        return jdbc.queryForObject(sql, Long.class);
    }

    public BigDecimal sumRevenue(LocalDate from, LocalDate to) {

        String sql = """
            SELECT COALESCE(SUM(
                o.grand_total - COALESCE((
                    SELECT SUM(cc.refund_amount)
                    FROM customer_claims cc
                    WHERE cc.order_id = o.id AND cc.status = 'APPROVED'
                ), 0)
            ), 0)
            FROM orders o
            WHERE o.status IN
        """ + SALES_STATUSES;

        if (hasDateRange(from, to)) {
            sql += " AND DATE(o.created_at) BETWEEN ? AND ?";
            return jdbc.queryForObject(sql, BigDecimal.class, Date.valueOf(from), Date.valueOf(to));
        }

        return jdbc.queryForObject(sql, BigDecimal.class);
    }

    public BigDecimal sumCogs(LocalDate from, LocalDate to) {

        String sql = """
            SELECT COALESCE(
                SUM(
                    oi.qty * COALESCE(p.import_cost_avg, 0)
                ),
                0
            )
            FROM order_items oi
            JOIN orders o
                ON o.id = oi.order_id
            JOIN products p
                ON p.id = oi.product_id
            WHERE o.status IN
        """ + SALES_STATUSES;

        if (hasDateRange(from, to)) {

            sql += " AND DATE(o.created_at) BETWEEN ? AND ?";

            return jdbc.queryForObject(
                    sql,
                    BigDecimal.class,
                    Date.valueOf(from),
                    Date.valueOf(to)
            );
        }

        return jdbc.queryForObject(sql, BigDecimal.class);
    }

    public long countLowStock(int threshold) {

        String sql = """
            SELECT COUNT(*)
            FROM products
            WHERE is_active = true
              AND stock_qty <= ?
        """;

        return jdbc.queryForObject(
                sql,
                Long.class,
                threshold
        );
    }

    public long countPendingShipment() {

        String sql = """
            SELECT COUNT(*)
            FROM orders
            WHERE status IN ('PAID','PACKING')
        """;

        return jdbc.queryForObject(sql, Long.class);
    }

    /*
     =========================================================
     SALES CHART
     =========================================================
    */

    public List<Map<String, Object>> salesDaily(LocalDate from, LocalDate to) {
        String sql = """
            SELECT
                DATE(o.created_at) AS d,
                COUNT(o.id) AS orders,
                COALESCE(SUM(
                    o.grand_total - COALESCE((
                        SELECT SUM(cc.refund_amount)
                        FROM customer_claims cc
                        WHERE cc.order_id = o.id AND cc.status = 'APPROVED'
                    ), 0)
                ), 0) AS revenue
            FROM orders o
            WHERE o.status IN
        """ + SALES_STATUSES;

        if (hasDateRange(from, to)) {
            sql += " AND DATE(o.created_at) BETWEEN ? AND ?";
        }

        sql += """
            GROUP BY DATE(o.created_at)
            ORDER BY d ASC
        """;

        if (hasDateRange(from, to)) {
            return jdbc.queryForList(sql, Date.valueOf(from), Date.valueOf(to));
        }
        return jdbc.queryForList(sql);
    }

    public List<Map<String, Object>> salesMonthly(LocalDate from, LocalDate to) {
        String sql = """
            SELECT
                DATE_FORMAT(o.created_at, '%Y-%m') AS m,
                COUNT(o.id) AS orders,
                COALESCE(SUM(
                    o.grand_total - COALESCE((
                        SELECT SUM(cc.refund_amount)
                        FROM customer_claims cc
                        WHERE cc.order_id = o.id AND cc.status = 'APPROVED'
                    ), 0)
                ), 0) AS revenue
            FROM orders o
            WHERE o.status IN
        """ + SALES_STATUSES;

        if (hasDateRange(from, to)) {
            sql += " AND DATE(o.created_at) BETWEEN ? AND ?";
        }

        sql += """
            GROUP BY DATE_FORMAT(o.created_at, '%Y-%m')
            ORDER BY m ASC
        """;

        if (hasDateRange(from, to)) {
            return jdbc.queryForList(sql, Date.valueOf(from), Date.valueOf(to));
        }
        return jdbc.queryForList(sql);
    }

    /*
     =========================================================
     BEST SELLERS
     =========================================================
    */

    public List<Map<String, Object>> bestSellers(
            LocalDate from,
            LocalDate to,
            int limit
    ) {

        String sql = """
            SELECT
                p.id AS product_id,
                p.name AS product_name,
                COALESCE(SUM(oi.qty),0) AS qty_sold,
                COALESCE(SUM(oi.line_total),0) AS revenue
            FROM order_items oi
            JOIN orders o
                ON o.id = oi.order_id
            JOIN products p
                ON p.id = oi.product_id
            WHERE o.status IN
        """ + SALES_STATUSES;

        if (hasDateRange(from, to)) {
            sql += " AND DATE(o.created_at) BETWEEN ? AND ?";
        }

        sql += """
            GROUP BY p.id, p.name
            ORDER BY qty_sold DESC
            LIMIT ?
        """;

        if (hasDateRange(from, to)) {

            return jdbc.queryForList(
                    sql,
                    Date.valueOf(from),
                    Date.valueOf(to),
                    limit
            );
        }

        return jdbc.queryForList(sql, limit);
    }

    /*
     =========================================================
     DEAD STOCK
     =========================================================
    */

    public List<Map<String, Object>> deadStock(
            int days,
            int limit
    ) {

        String sql = """
            SELECT
                p.id AS product_id,
                p.name AS product_name,
                p.stock_qty,

                DATEDIFF(
                    CURDATE(),
                    MAX(DATE(o.created_at))
                ) AS days_since_last_sale

            FROM products p

            LEFT JOIN order_items oi
                   ON oi.product_id = p.id

            LEFT JOIN orders o
                   ON o.id = oi.order_id
                  AND o.status IN
        """ + SALES_STATUSES + """

            WHERE p.is_active = true
              AND p.stock_qty > 0

            GROUP BY p.id, p.name, p.stock_qty

            HAVING DATEDIFF(CURDATE(), MAX(DATE(o.created_at))) >= ?
                OR MAX(DATE(o.created_at)) IS NULL

            ORDER BY MAX(DATE(o.created_at)) IS NULL DESC,
                     DATEDIFF(CURDATE(), MAX(DATE(o.created_at))) DESC

            LIMIT ?
        """;

        return jdbc.queryForList(sql, days, limit);
    }

   /*
     =========================================================
     IMPORT
     =========================================================
    */

    public BigDecimal sumImportExpense(
            LocalDate from,
            LocalDate to
    ) {
        // ปรับ SQL ใหม่: ใช้ Subquery ดึงผลรวมแยกทีละล็อตออกมาก่อน แล้วค่อยเอาตารางหลัก SUM ภาพรวมด้านนอกสุด
        String sql = """
            SELECT COALESCE(
                SUM(
                    il.total_import_cost + 
                    COALESCE(
                        (SELECT SUM(ili.line_cost) 
                         FROM import_lot_items ili 
                         WHERE ili.import_lot_id = il.id), 
                        0
                    )
                ),
                0
            )
            FROM import_lots il
            WHERE il.status = 'RECEIVED'
        """;

        if (hasDateRange(from, to)) {

            sql += " AND DATE(il.created_at) BETWEEN ? AND ?";

            return jdbc.queryForObject(
                    sql,
                    BigDecimal.class,
                    Date.valueOf(from),
                    Date.valueOf(to)
            );
        }

        return jdbc.queryForObject(
                sql,
                BigDecimal.class
        );
    }

    public long countCustomsDocsUnderReview() {

        String sql = """
            SELECT COUNT(*)
            FROM import_documents
            WHERE status = 'UNDER_REVIEW'
        """;

        return jdbc.queryForObject(sql, Long.class);
    }

    public long countCustomsDocsRejected(
            LocalDate from,
            LocalDate to
    ) {

        String sql = """
            SELECT COUNT(*)
            FROM import_documents
            WHERE status = 'REJECTED'
        """;

        if (hasDateRange(from, to)) {

            sql += " AND DATE(updated_at) BETWEEN ? AND ?";

            return jdbc.queryForObject(
                    sql,
                    Long.class,
                    Date.valueOf(from),
                    Date.valueOf(to)
            );
        }

        return jdbc.queryForObject(sql, Long.class);
    }

    public long countLotsReadyToReceive() {

        String sql = """
            SELECT COUNT(*)
            FROM import_lots
            WHERE status = 'CUSTOMS_APPROVED'
        """;

        return jdbc.queryForObject(sql, Long.class);
    }
}