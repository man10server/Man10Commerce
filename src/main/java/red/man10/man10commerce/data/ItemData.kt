package red.man10.man10commerce.data

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import red.man10.man10commerce.Man10Commerce.Companion.plugin
import red.man10.man10commerce.Utility
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class ItemData {

    var id = 0
    var sellItem : ItemStack? = null
    var price : Double = 0.0
    var date : Date? = null


    //商品を出品する
    fun push():Boolean{


        return false
    }

    //商品を購入する
    fun buy(buyer:Player):Boolean{


        return false
    }

    //出品期間が過ぎたかどうか
    fun isFinishTime():Boolean{


        return false
    }

    //出品を取り下げる
    fun close():Boolean{

        return false
    }

    companion object{

        private val itemMap = ConcurrentHashMap<ItemStack,Int>()

        private val mysql = MySQLManager(plugin,"Man10Commerce")

        fun createItemMap(item:ItemStack):Boolean{

            val one = item.asOne()

            if (itemMap.containsKey(one))return false

            val name = if (one.hasItemMeta()) one.itemMeta!!.displayName else one.i18NDisplayName

            mysql.execute("INSERT INTO item_list " +
                    "(item_name, item_type, base64) VALUES ('${name}', '${one.type}', '${Utility.itemToBase64(one)}');")

            val rs = mysql.query("select id from item_list ORDER BY id DESC LIMIT 1;")!!

            rs.next()

            itemMap[one] = rs.getInt("id")

            rs.close()
            mysql.close()

            return true
        }

        fun loadItemMap(){

            itemMap.clear()

            val rs = mysql.query("select id,base64 from item_list;")?:return

            while (rs.next()){
                itemMap[Utility.itemFromBase64(rs.getString("base64"))!!] = rs.getInt("id")
            }

            rs.close()
            mysql.close()

        }


    }

}