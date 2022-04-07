package red.man10.man10commerce.menu

import org.bukkit.entity.Player
import red.man10.man10commerce.data.ItemData
import red.man10.man10commerce.sort.Sort

class SearchMenu(p:Player, private val query:String) : ListMenu("§l検索結果",p){

    override fun open() {

        val keys = Sort.nameSort(query, ItemData.orderMap.keys().toList())

        listInventory(keys)

        p.openInventory(menu)

        pushStack()
    }
}