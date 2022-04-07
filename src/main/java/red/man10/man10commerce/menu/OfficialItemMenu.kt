package red.man10.man10commerce.menu

import org.bukkit.entity.Player
import red.man10.man10commerce.data.ItemData

class OfficialItemMenu(p:Player) : ListMenu("§d§lAmanzonBasic",p){
    override fun open() {

        val keys = ItemData.opOrderMap.keys().toList()

        listInventory(keys)

        p.openInventory(menu)

        pushStack()

    }
}