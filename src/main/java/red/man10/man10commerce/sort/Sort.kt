package red.man10.man10commerce.sort

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import red.man10.man10commerce.Man10Commerce
import red.man10.man10commerce.data.Data
import red.man10.man10commerce.data.ItemData
import java.util.*

object Sort {

    fun nameSort(string: String, items: List<Int>): List<Int> {
        return items.filter { ChatColor.stripColor(Man10Commerce.getDisplayName(ItemData.itemDictionary[ItemData.orderMap[it]!!.itemID]!!)) == string }
    }

    fun materialSort(material: Material, items: List<Int>): List<Int> {
        return items.filter { ItemData.itemDictionary[ItemData.orderMap[it]!!.itemID]!!.type == material }
    }
}
