package red.man10.man10commerce.menu

import org.bukkit.entity.Player
import red.man10.man10commerce.data.ItemData

class CategorizedMenu(p:Player, private val category:String) : ListMenu("§lカテゴリーアイテム",p){

    override fun open() {

        val keys = ItemData.getCategorized(category).keys.toList()

        listInventory(keys)

        p.openInventory(menu)

        pushStack()

    }
}