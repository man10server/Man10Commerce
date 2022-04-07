package red.man10.man10commerce.menu

import org.bukkit.Material
import org.bukkit.entity.Player
import red.man10.man10commerce.data.ItemData
import red.man10.man10commerce.sort.Sort

class MaterialMenu(p:Player,private val material: Material) : ListMenu("§l同じ種類のリスト",p) {
    override fun open() {
        val keys = Sort.materialSort(material, ItemData.orderMap.keys().toList())

        listInventory(keys)

        p.openInventory(menu)

        pushStack()
    }
}