package red.man10.man10commerce.data

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import red.man10.man10commerce.Man10Commerce
import red.man10.man10commerce.Man10Commerce.Companion.bank
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

    fun joinPrime(uuid:UUID):Boolean{

        if (isPrimeUser(uuid))return false

        if (!bank.withdraw(uuid, Man10Commerce.primeMoney,"PrimeMoney"))return false

        val p = Bukkit.getOfflinePlayer(uuid)

        mysql.execute("INSERT INTO prime_list (player, uuid, pay_date) " +
                "VALUES ('${p.name}', '${uuid}', now());")

        return true
    }

    fun leavePrime(uuid:UUID):Boolean{

        if (!isPrimeUser(uuid))return false

        mysql.execute("DELETE FROM prime_list where uuid='${uuid}';")

        return true
    }

    fun primeThread(){

        val format = SimpleDateFormat("yyyy-MM-dd 00:00:00")

        while (true){

            val cal = Calendar.getInstance()
            cal.time = Date()
            cal.add(Calendar.MONTH,-1)

            val rs = mysql.query("SELECT uuid FROM prime_list where pay_date<'${format.format(cal.time)}'")?:continue

            while (rs.next()){

                val uuid = UUID.fromString(rs.getString("uuid"))

                if (!bank.withdraw(uuid,Man10Commerce.primeMoney,"PrimeMoney")){
                    leavePrime(uuid)
                }
            }

            rs.close()
            mysql.close()

            Thread.sleep(100000)
        }

    }


}