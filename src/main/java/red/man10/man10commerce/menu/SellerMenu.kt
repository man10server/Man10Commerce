package red.man10.man10commerce.menu

import org.bukkit.entity.Player
import red.man10.man10commerce.data.ItemData
import red.man10.man10commerce.sort.Sort

class SellerMenu(p:Player,private val seller:String) : ListMenu("§l出品者名の検索結果",p){
    override fun open() {
        val keys = Sort.sellerSort(seller, ItemData.orderMap.keys().toList())

        listInventory(keys)

        p.openInventory(menu)

        pushStack()
    }
}