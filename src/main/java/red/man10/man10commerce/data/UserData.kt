package red.man10.man10commerce.data

import java.util.UUID

object UserData {

    /**
     * 出品中の件数を返す。
     * @return 件数。取得に失敗した場合はnull
     */
    fun getSellCount(uuid:UUID, sql: MySQLManager):Int?{
        return sql.query("SELECT COUNT(*) FROM order_table WHERE uuid = ?", uuid){ rs ->
            if (rs.next()) rs.getInt(1) else 0
        }
    }
}
