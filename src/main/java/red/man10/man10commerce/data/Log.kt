package red.man10.man10commerce.data

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import red.man10.man10commerce.Man10Commerce.Companion.es
import red.man10.man10commerce.Man10Commerce.Companion.plugin
import red.man10.man10commerce.data.MySQLManager.Companion.mysqlQueue

object Log {

    init {
        es.execute { mysqlQueue(plugin,"LogQueue") }
    }

    fun sellLog(p:Player,item: ItemStack,price:Double,itemID:Int){

        val name = if (item.hasItemMeta()) item.itemMeta!!.displayName else item.i18NDisplayName

        mysqlQueue.add("INSERT INTO log " +
                "(order_player, target_player, action, item_id, item_name, amount, price, date) " +
                "VALUES ('${p.name}', '','SellItem' , $itemID, '${name}', ${item.amount}, ${price}, now())")
    }

    fun buyLog(p:Player,data: Data,item:ItemStack){

        val order = Bukkit.getOfflinePlayer(data.seller!!)

        val name = if (item.hasItemMeta()) item.itemMeta!!.displayName else item.i18NDisplayName
        mysqlQueue.add("INSERT INTO log " +
                "(order_player, target_player, action, item_id, item_name, amount, price, date) " +
                "VALUES ('${order.name}', '${p.name}','BuyItem' , ${data.itemID}, '${name}', ${item.amount}, ${data.price}, now())")
    }

    fun closeLog(p:Player,itemID:Int,item: ItemStack){

        val name = if (item.hasItemMeta()) item.itemMeta!!.displayName else item.i18NDisplayName

        mysqlQueue.add("INSERT INTO log " +
                "(order_player, target_player, action, item_id, item_name, amount, price, date) " +
                "VALUES ('${p.name}', '','CloseItem' , ${itemID}, '${name}', ${item.amount}, 0, now())")

    }

}