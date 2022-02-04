package red.man10.man10commerce.sort

import org.bukkit.ChatColor
import org.bukkit.Material
import red.man10.man10commerce.Man10Commerce
import red.man10.man10commerce.data.ItemData

object Sort {

    fun nameSort(string: String, items: List<Int>): List<Int> {
        return items.filter { ChatColor.stripColor(Man10Commerce.getDisplayName(ItemData.itemDictionary[ItemData.orderMap[it]!!.itemID]!!))!!.contains(string) }
    }

    fun materialSort(material: Material, items: List<Int>): List<Int> {
        return items.filter { ItemData.itemDictionary[ItemData.orderMap[it]!!.itemID]!!.type == material }
    }
}
