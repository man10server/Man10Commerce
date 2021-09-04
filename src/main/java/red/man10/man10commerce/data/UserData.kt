package red.man10.man10commerce.data

import org.bukkit.entity.Player
import red.man10.man10commerce.Man10Commerce.Companion.plugin

object UserData {

    private val mysql = MySQLManager(plugin,"Man10CommercePrice")

    fun getSellAmount(p: Player):Int{

        val rs = mysql.query("select count(*) from order_table where uuid='${p.uniqueId}';")?:return 0
        rs.next()
        val amount = rs.getInt(1)

        rs.close()
        mysql.close()

        return amount
    }



}