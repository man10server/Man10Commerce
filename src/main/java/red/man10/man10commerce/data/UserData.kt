package red.man10.man10commerce.data

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import red.man10.man10commerce.Man10Commerce.Companion.plugin
import java.text.SimpleDateFormat
import java.util.*

object UserData {

    private val mysql = MySQLManager(plugin,"Man10CommercePrice")

    fun isPrimeUser(p:Player):Boolean{

        val rs = mysql.query("select * from prime_list where uuid='${p.uniqueId}';")?:return false

        val ret = rs.next()

        rs.close()
        mysql.close()

        return ret
    }

    fun isPrimeUser(uuid: UUID):Boolean{

        val rs = mysql.query("select * from prime_list where uuid='$uuid';")?:return false

        val ret = rs.next()

        rs.close()
        mysql.close()

        return ret
    }

    fun getSellAmount(p: Player):Int{

        val rs = mysql.query("select count(*) from order_table where uuid=${p.uniqueId};")?:return 0
        rs.next()
        val amount = rs.getInt(1)

        rs.close()
        mysql.close()

        return amount
    }

    fun getProfitMonth(p: Player):Double{

        val format = SimpleDateFormat("yyyy-MM-01 00:00:00")

        Bukkit.getLogger().info(format.format(Date()))

        val rs = mysql.query("select sum(price) from log where order_player=${p.uniqueId} and action='BuyItem' " +
                "and date>='${format.format(Date())}';")?:return 0.0

        rs.next()

        val profit = rs.getDouble(1)

        rs.close()
        mysql.close()

        return profit
    }



}