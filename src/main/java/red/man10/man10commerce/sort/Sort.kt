package red.man10.man10commerce.sort

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.meta.EnchantmentStorageMeta
import red.man10.man10commerce.Man10Commerce
import red.man10.man10commerce.data.ItemData

object Sort {

    fun nameSort(string: String, items: List<Int>): List<Int> {
        return items.filter { ChatColor.stripColor(Man10Commerce.getDisplayName(ItemData.itemDictionary[ItemData.orderMap[it]!!.itemID]!!))!!
            .toLowerCase().contains(string.toLowerCase()) }
    }

    fun materialSort(material: Material, items: List<Int>): List<Int> {
        return items.filter { ItemData.itemDictionary[ItemData.orderMap[it]!!.itemID]!!.type == material }
    }

    fun sellerSort(seller : String, items: List<Int>): List<Int> {
        val uuid = Bukkit.getOfflinePlayer(seller).uniqueId
        return items.filter { uuid  == ItemData.orderMap[it]!!.seller }
    }

    fun enchantSort(enchantment: Enchantment, level: Int, items: List<Int>): List<Int> {
        return items.filter {
            val meta = ItemData.itemDictionary[ItemData.orderMap[it]!!.itemID]!!.itemMeta
            meta is EnchantmentStorageMeta && meta.getStoredEnchantLevel(enchantment) == level
        }
    }
}
