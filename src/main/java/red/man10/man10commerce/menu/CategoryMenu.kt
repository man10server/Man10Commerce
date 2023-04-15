package red.man10.man10commerce.menu

import org.bukkit.Material
import org.bukkit.entity.Player
import red.man10.man10commerce.data.Category
import red.man10.man10commerce.data.Transaction

class CategoryMenu(p:Player) : MenuFramework(p, CHEST_SIZE,"§lカテゴリーメニュー"){

    override fun init () {
        setClickAction{
            it.isCancelled = true
        }


        val buttonAllItem = Button(Material.COBBLESTONE)
        buttonAllItem.title("§a§lすべてのアイテムをみる")
        buttonAllItem.setClickAction{
            AllItemMenu(p,0).open()
        }
        addButton(buttonAllItem)

        val categoryData = Transaction.categories

        categoryData.forEach { data ->
            val button = Button(data.value.categoryIcon.type)
            button.title(data.value.categoryIcon.itemMeta.displayName)
            button.cmd(data.value.categoryIcon.itemMeta.customModelData)
            button.lore(data.value.categoryIcon.lore?: emptyList())
            button.setClickAction{
                CategorizedMenu(p,0,data.key).open()
            }
            addButton(button)
        }

        val buttonNotCategorized = Button(Material.BARRIER)
        buttonNotCategorized.title("§a§lその他のアイテム")
        buttonNotCategorized.setClickAction{
            CategorizedMenu(p,0,Category.NOT_CATEGORIZED).open()
        }
        addButton(buttonNotCategorized)
    }
}