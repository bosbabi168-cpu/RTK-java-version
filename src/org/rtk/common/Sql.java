package org.rtk.common;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Port of common/db_mysql.c on top of JDBC, using HikariCP for connection
 * pooling (extLib/HikariCP-*.jar) instead of the single long-lived
 * connection the original C server kept open.
 *
 * The C code interpolated values straight into query strings; this port uses
 * PreparedStatement parameter binding instead, which keeps identical
 * behavior while closing the SQL-injection holes of the original.
 */
public final class Sql {

    private static final Logger log = LogManager.getLogger(Sql.class);

    private HikariDataSource dataSource;

    /** Sql_Connect(): builds the connection pool and verifies it works. */
    public boolean connect(String user, String pass, String host, int port, String db) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8&serverTimezone=UTC");
        config.setUsername(user);
        config.setPassword(pass);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setPoolName("RTK-" + db);
        config.setMaximumPoolSize(Props.getInt("sql.pool_max", 10));
        config.setMinimumIdle(Props.getInt("sql.pool_min_idle", 2));
        config.setConnectionTimeout(Props.getLong("sql.pool_connection_timeout_ms", 10_000));
        config.setIdleTimeout(Props.getLong("sql.pool_idle_timeout_ms", 600_000));
        config.setMaxLifetime(Props.getLong("sql.pool_max_lifetime_ms", 1_800_000));
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        try {
            dataSource = new HikariDataSource(config);
            try (Connection c = dataSource.getConnection()) {
                // just verifying the pool can actually reach the server
            }
            log.info("Connected to MySQL database '{}' at {}:{} (pool '{}', max {} connections).",
                    db, host, port, config.getPoolName(), config.getMaximumPoolSize());
            return true;
        } catch (Exception e) {
            log.error("SQL_ERR: unable to initialize connection pool for {}:{}/{}", host, port, db, e);
            if (dataSource != null) {
                dataSource.close();
                dataSource = null;
            }
            return false;
        }
    }

    private Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("Connection pool is not initialized");
        }
        return dataSource.getConnection();
    }

    /** Sql_ShowDebug() */
    public static void showDebug(SQLException e) {
        log.error("SQL_ERR: {}", e.getMessage(), e);
    }

    private PreparedStatement prepare(Connection c, String sql, Object... params) throws SQLException {
        PreparedStatement st = c.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            st.setObject(i + 1, params[i]);
        }
        return st;
    }

    /** INSERT/UPDATE/DELETE. Returns affected rows, or -1 on error. */
    public int update(String sql, Object... params) {
        try (Connection c = getConnection();
             PreparedStatement st = prepare(c, sql, params)) {
            return st.executeUpdate();
        } catch (SQLException e) {
            showDebug(e);
            return -1;
        }
    }

    /**
     * Single integer column of the first row.
     * Returns null when the query yields no rows, or on error (after logging).
     */
    public Integer queryInt(String sql, Object... params) {
        try (Connection c = getConnection();
             PreparedStatement st = prepare(c, sql, params);
             ResultSet rs = st.executeQuery()) {
            if (rs.next()) {
                int v = rs.getInt(1);
                // ⚠️ `getInt` mengembalikan 0 untuk SQL NULL, sehingga "tidak
                // ada nilainya" dan "nilainya nol" jadi tak terbedakan. Itu
                // penting pada agregat: `SELECT MAX(x)` pada himpunan kosong
                // menghasilkan SATU baris berisi NULL, bukan nol baris — jadi
                // tanpa penjaga ini pemanggilnya mengira nilai tertingginya 0.
                // Kejadian pada nomor urut kiriman, yang seharusnya mulai
                // dari 0 tapi malah mulai dari 1.
                return rs.wasNull() ? null : v;
            }
            return null;
        } catch (SQLException e) {
            showDebug(e);
            return null;
        }
    }

    /** Single string column of the first row; null when absent or on error. */
    /**
     * Satu nilai pecahan dari kolom pertama baris pertama; null bila tidak
     * ada baris <b>atau</b> nilainya SQL NULL.
     *
     * <p>Pembedaan itu penting untuk alasan yang sama dengan
     * {@link #queryInt}: agregat pada himpunan kosong menghasilkan satu
     * baris berisi NULL, bukan nol baris (Peringatan #44).</p>
     */
    public Double queryDouble(String sql, Object... params) {
        Object[] row = queryRow(sql, 1, params);
        if (row == null || row[0] == null) {
            return null;
        }
        return row[0] instanceof Number n ? n.doubleValue()
                : Double.parseDouble(row[0].toString());
    }

    public String queryString(String sql, Object... params) {
        try (Connection c = getConnection();
             PreparedStatement st = prepare(c, sql, params);
             ResultSet rs = st.executeQuery()) {
            if (rs.next()) {
                return rs.getString(1);
            }
            return null;
        } catch (SQLException e) {
            showDebug(e);
            return null;
        }
    }

    /** First row as an Object[]; null when absent or on error. */
    public Object[] queryRow(String sql, int columns, Object... params) {
        try (Connection c = getConnection();
             PreparedStatement st = prepare(c, sql, params);
             ResultSet rs = st.executeQuery()) {
            if (rs.next()) {
                Object[] row = new Object[columns];
                for (int i = 0; i < columns; i++) {
                    row[i] = rs.getObject(i + 1);
                }
                return row;
            }
            return null;
        } catch (SQLException e) {
            showDebug(e);
            return null;
        }
    }

    /** Penerima satu baris hasil query. */
    public interface RowHandler {
        void row(ResultSet rs) throws SQLException;
    }

    /**
     * Jalankan query dan serahkan setiap baris ke handler.
     * @return jumlah baris yang diproses, -1 bila gagal
     */
    public int forEachRow(String sql, RowHandler handler, Object... params) {
        try (Connection c = getConnection();
             PreparedStatement st = prepare(c, sql, params);
             ResultSet rs = st.executeQuery()) {
            int n = 0;
            while (rs.next()) {
                handler.row(rs);
                n++;
            }
            return n;
        } catch (SQLException e) {
            showDebug(e);
            return -1;
        }
    }

    /**
     * Jalankan satu perintah untuk banyak baris sekaligus (batch).
     * @param rows tiap elemen = parameter untuk satu baris
     * @return jumlah baris terpengaruh, -1 bila gagal
     */
    public int batch(String sql, java.util.List<Object[]> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        try (Connection c = getConnection();
             PreparedStatement st = c.prepareStatement(sql)) {
            for (Object[] r : rows) {
                for (int i = 0; i < r.length; i++) {
                    st.setObject(i + 1, r[i]);
                }
                st.addBatch();
            }
            int total = 0;
            for (int n : st.executeBatch()) {
                total += Math.max(n, 0);
            }
            return total;
        } catch (SQLException e) {
            showDebug(e);
            return -1;
        }
    }

    /** Number of rows the query returns; -1 on error. */
    public int rowCount(String sql, Object... params) {
        try (Connection c = getConnection();
             PreparedStatement st = prepare(c, sql, params);
             ResultSet rs = st.executeQuery()) {
            int n = 0;
            while (rs.next()) {
                n++;
            }
            return n;
        } catch (SQLException e) {
            showDebug(e);
            return -1;
        }
    }

    /** Sql_Free(): shuts the pool down. */
    public void close() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }
}
