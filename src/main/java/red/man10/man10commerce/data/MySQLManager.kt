package red.man10.man10commerce.data

import org.bukkit.Bukkit
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.sql.Timestamp
import java.util.Date
import java.util.UUID

/**
 * Thin, stateless helper around [Database]. Every call borrows a pooled
 * connection and returns it before the method exits, so a single instance can
 * safely be shared between threads.
 *
 * Failures are reported through the return value instead of being swallowed:
 * [query] returns null, [execute] returns [FAILED] and [insert] returns null.
 *
 * Created by takatronix on 2017/03/05, rewritten for HikariCP.
 */
class MySQLManager(private val name: String) {

    var debugMode = false

    companion object {
        /** Returned by [execute] when the statement could not be run at all. */
        const val FAILED = -1
    }

    /**
     * Runs a SELECT and hands the [ResultSet] to [block]. The result set and the
     * connection are closed as soon as [block] returns, so anything that must
     * outlive the call has to be copied out inside it.
     *
     * @return whatever [block] returned, or null when the query failed.
     */
    fun <T> query(sql: String, vararg params: Any?, block: (ResultSet) -> T): T? {
        val connection = Database.connection() ?: return null
        debugLog(sql)
        try {
            connection.prepareStatement(sql).use { statement ->
                bind(statement, params)
                statement.executeQuery().use { result -> return block(result) }
            }
        } catch (e: SQLException) {
            logError("query", sql, e)
            return null
        } finally {
            closeQuietly(connection)
        }
    }

    /**
     * Runs an INSERT/UPDATE/DELETE.
     *
     * @return the number of affected rows, or [FAILED] when the statement failed.
     */
    fun execute(sql: String, vararg params: Any?): Int {
        val connection = Database.connection() ?: return FAILED
        debugLog(sql)
        try {
            connection.prepareStatement(sql).use { statement ->
                bind(statement, params)
                return statement.executeUpdate()
            }
        } catch (e: SQLException) {
            logError("execute", sql, e)
            return FAILED
        } finally {
            closeQuietly(connection)
        }
    }

    /**
     * Runs an INSERT and returns its generated auto increment key, or null when
     * the insert failed or produced no key.
     */
    fun insert(sql: String, vararg params: Any?): Int? {
        val connection = Database.connection() ?: return null
        debugLog(sql)
        try {
            connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { statement ->
                bind(statement, params)
                if (statement.executeUpdate() == 0) return null
                statement.generatedKeys.use { keys ->
                    return if (keys.next()) keys.getInt(1) else null
                }
            }
        } catch (e: SQLException) {
            logError("insert", sql, e)
            return null
        } finally {
            closeQuietly(connection)
        }
    }

    /**
     * Runs [rows] through one batched statement.
     *
     * @return true when every row was applied.
     */
    fun batch(sql: String, rows: List<Array<out Any?>>): Boolean {
        if (rows.isEmpty()) return true
        val connection = Database.connection() ?: return false
        debugLog(sql)
        try {
            connection.prepareStatement(sql).use { statement ->
                for (row in rows) {
                    bind(statement, row)
                    statement.addBatch()
                }
                statement.executeBatch()
                return true
            }
        } catch (e: SQLException) {
            logError("batch", sql, e)
            return false
        } finally {
            closeQuietly(connection)
        }
    }

    private fun bind(statement: PreparedStatement, params: Array<out Any?>) {
        params.forEachIndexed { index, value ->
            val position = index + 1
            when (value) {
                null -> statement.setObject(position, null)
                is UUID -> statement.setString(position, value.toString())
                is Timestamp -> statement.setTimestamp(position, value)
                is Date -> statement.setTimestamp(position, Timestamp(value.time))
                is Enum<*> -> statement.setString(position, value.name)
                else -> statement.setObject(position, value)
            }
        }
    }

    private fun closeQuietly(connection: Connection) {
        try {
            connection.close()
        } catch (e: SQLException) {
            Bukkit.getLogger().warning("[$name] failed to return a connection to the pool: ${e.message}")
        }
    }

    private fun debugLog(sql: String) {
        if (debugMode) Bukkit.getLogger().info("[$name] $sql")
    }

    private fun logError(operation: String, sql: String, e: Exception) {
        val code = if (e is SQLException) " (errorCode=${e.errorCode}, sqlState=${e.sqlState})" else ""
        Bukkit.getLogger().warning("[$name] $operation failed$code: ${e.message}")
        if (sql.isNotEmpty()) Bukkit.getLogger().warning("[$name] statement: $sql")
    }
}
