package red.man10.man10commerce.menu

import org.bukkit.Material
import org.bukkit.entity.Player

class CategoryMenu(p:Player) : MenuFramework(p, CHEST_SIZE,"§lカテゴリーメニュー"){

    init {
        val buttonAllItem = Button(Material.COBBLESTONE)
        buttonAllItem.title("§a§lすべてのアイテムをみる")
        buttonAllItem.setClickAction{

        }
        setButton(buttonAllItem,1)

        val buttonNotCategorized = Button(Material.BARRIER)
        buttonNotCategorized.title("§a§lその他のアイテム")
        buttonNotCategorized.setClickAction{

        }

    }
}