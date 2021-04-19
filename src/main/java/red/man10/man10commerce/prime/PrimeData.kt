package red.man10.man10commerce.prime

import org.bukkit.entity.Player
import red.man10.man10commerce.Man10Commerce.Companion.plugin
import red.man10.man10commerce.data.MySQLManager

object PrimeData {

    private val mysql = MySQLManager(plugin,"Man10CommercePrice")

    fun isPrimeUser(UUID:Player):Boolean{

        return false
    }

}