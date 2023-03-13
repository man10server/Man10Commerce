package red.man10.man10commerce.data

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import red.man10.man10commerce.Man10Commerce.Companion.es
import red.man10.man10commerce.Man10Commerce.Companion.plugin
import red.man10.man10commerce.data.MySQLManager.Companion.escapeStringForMySQL
import red.man10.man10commerce.data.MySQLManager.Companion.mysqlQueue

object Log {

    init {
        es.execute { mysqlQueue(plugin,"LogQueue") }
    }

    //      販売ログを追加
    fun sellLog(p:Player,item: ItemStack,price:Double,itemID:Int){

        val name = if (item.hasItemMeta()) item.itemMeta!!.displayName else item.i18NDisplayName

        mysqlQueue.add("INSERT INTO log " +
                "(order_player, target_player, action, item_id, item_name, amount, price, date) " +
                "VALUES ('${p.name}', '','SellItem' , $itemID, '${escapeStringForMySQL(name?:"")}', ${item.amount}, ${price}, now())")
    }

    //      購入ログを追加
    fun buyLog(p:Player,data: OrderData,item:ItemStack){

        val order = Bukkit.getOfflinePlayer(data.seller)

        val name = if (item.hasItemMeta()) item.itemMeta!!.displayName else item.i18NDisplayName
        mysqlQueue.add("INSERT INTO log " +
                "(order_player, target_player, action, item_id, item_name, amount, price, date) " +
                "VALUES ('${order.name}', '${p.name}','BuyItem' , ${data.itemID}, '${escapeStringForMySQL(name?:"")}', ${item.amount}, ${data.price}, now())")
    }

    //      取り消しログを追加
    fun closeLog(p:Player,itemID:Int,item: ItemStack){

        val name = if (item.hasItemMeta()) item.itemMeta!!.displayName else item.i18NDisplayName

        mysqlQueue.add("INSERT INTO log " +
                "(order_player, target_player, action, item_id, item_name, amount, price, date) " +
                "VALUES ('${p.name}', '','CloseItem' , ${itemID}, '${escapeStringForMySQL(name?:"")}', ${item.amount}, 0, now())")

    }

}