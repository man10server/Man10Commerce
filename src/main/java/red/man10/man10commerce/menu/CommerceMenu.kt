package red.man10.man10commerce.menu

import org.bukkit.Bukkit
import org.bukkit.entity.Player

object CommerceMenu {

    private const val ITEM_MENU = "§e§l出品中のアイテム一覧"

    fun openMainMenu(p:Player){

    }

    fun openItemMenu(p:Player,page:Int){

        val inv = Bukkit.createInventory(null,54,)

    }

}