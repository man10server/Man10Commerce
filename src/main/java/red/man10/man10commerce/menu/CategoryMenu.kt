package red.man10.man10commerce.menu

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import red.man10.man10commerce.data.ItemData

class CategoryMenu(p:Player) : Menu("§lカテゴリーメニュー",27,p){

    override fun open() {
        val allItemIcon = ItemStack(Material.COBBLESTONE)

        val meta = allItemIcon.itemMeta
        meta.displayName(Component.text("§a§lすべてのアイテムをみる"))
        CommerceMenu.setID(meta, "all")
        allItemIcon.itemMeta = meta

        menu.addItem(allItemIcon)

        for (data in ItemData.categories.values){
            menu.addItem(data.categoryIcon)
        }

        val notCategorized = ItemStack(Material.BARRIER)

        val meta2 = notCategorized.itemMeta
        meta2.displayName(Component.text("§a§lカテゴリーわけされてないアイテムを見る"))
        CommerceMenu.setID(meta2, "not")
        notCategorized.itemMeta = meta2

        menu.addItem(notCategorized)

        p.openInventory(menu)

        pushStack()

    }
}