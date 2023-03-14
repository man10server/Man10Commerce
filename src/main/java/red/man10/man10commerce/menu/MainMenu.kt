package red.man10.man10commerce.menu

import org.bukkit.Material
import org.bukkit.entity.Player

class MainMenu(p:Player) : MenuFramework(p, CHEST_SIZE,"§l出品中のアイテム一覧"){

    init {
        val buttonShowItem = Button(Material.GRASS_BLOCK)
        buttonShowItem.title("§a§l出品されているアイテムをみる")
        buttonShowItem.setClickAction{

        }
        setButton(buttonShowItem,1)

        val buttonBasic = Button(Material.DIAMOND)
        buttonBasic.title("§a§lAmanzonBasic")
        buttonBasic.lore(mutableListOf("§f公式が販売している","§fアイテムを買うことができます"))
        buttonBasic.setClickAction{

        }
        setButton(buttonBasic,3)

        val buttonMySellItem = Button(Material.CHEST)
        buttonMySellItem.title("§a§l自分が出品したアイテムを確かめる")
        buttonMySellItem.setClickAction{

        }
        setButton(buttonMySellItem,5)

        val buttonToSell = Button(Material.COBBLESTONE)
        buttonToSell.title("§a§lアイテムを出品する")
        buttonToSell.setClickAction{

        }
        setButton(buttonToSell,7)

        val buttonSearchByName = Button(Material.OAK_SIGN)
        buttonSearchByName.title("§a§l出品されたアイテムを検索する")
        buttonSearchByName.setClickAction{

        }
        setButton(buttonSearchByName,19)

        val buttonSearchByMaterial = Button(Material.GLASS)
        buttonSearchByMaterial.title("§a§l手に持っているアイテムから検索する")
        buttonSearchByMaterial.setClickAction{

        }
        setButton(buttonSearchByMaterial,21)

        val buttonSearchByAuthor = Button(Material.PLAYER_HEAD)
        buttonSearchByAuthor.title("§a§l出品者名で検索する")
        buttonSearchByAuthor.setClickAction{

        }
        setButton(buttonSearchByAuthor,23)

        val buttonSearchByEnchant = Button(Material.ENCHANTED_BOOK)
        buttonSearchByEnchant.title("§a§lエンチャントで検索する")
        buttonSearchByEnchant.setClickAction{

        }
        setButton(buttonSearchByEnchant,25)

        //スタックに追加
        push()
    }
}