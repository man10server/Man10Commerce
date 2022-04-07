package red.man10.man10commerce.menu

import org.bukkit.entity.Player
import red.man10.man10commerce.data.ItemData

class AllItemMenu(p:Player) :ListMenu("§l出品中のアイテム一覧",p){

    override fun open() {

        val keys = ItemData.orderMap.keys().toList()

        listInventory(keys)

        p.openInventory(menu)

        pushStack()
    }
}