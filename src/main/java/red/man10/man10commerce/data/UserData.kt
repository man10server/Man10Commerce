package red.man10.man10commerce.data

import red.man10.man10commerce.Man10Commerce.Companion.plugin
import java.util.UUID

object UserData {

    private val mysql = MySQLManager(plugin,"Man10CommercePrice")

    fun getSellCount(uuid:UUID):Int{

        val rs = mysql.query("select count(*) from order_table where uuid='${uuid}';")?:return 0
        rs.next()
        val amount = rs.getInt(1)

        rs.close()
        mysql.close()

        return amount
    }



}