package red.man10.man10commerce.menu

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import red.man10.man10commerce.Utility
import red.man10.man10commerce.data.ItemData.Companion.itemIndex
import red.man10.man10commerce.data.ItemData.Companion.itemList

object CommerceMenu {

    private const val ITEM_MENU = "§e§l出品中のアイテム一覧"

    fun openMainMenu(p:Player){

    }

    fun openItemMenu(p:Player,page:Int){

        val inv = Bukkit.createInventory(null,54, ITEM_MENU)

        val keys = itemIndex.keys().toList()

        var inc = 0

        for (i in page*45 .. (page+1)*45){

            val itemID = keys[i]

            val data = itemList[itemID]?:continue
            val item = itemIndex[itemID]!!.clone()

            val lore = item.lore?: mutableListOf()

            lore.add("§e§l値段:${Utility.format(data.price)}")
            lore.add("§e§l個数:${data.amount}")

            item.lore = lore

            inv.setItem(inc,item)

            inc ++

        }

    }

}