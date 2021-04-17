package red.man10.man10commerce

import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10commerce.Utility.sendMsg
import red.man10.man10commerce.data.ItemData
import red.man10.man10commerce.menu.CommerceMenu
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class Man10Commerce : JavaPlugin() {

    companion object{
        lateinit var plugin: JavaPlugin
        val es : ExecutorService = Executors.newCachedThreadPool()

        const val prefix = "§l[§a§lA§d§lma§f§ln§a§lzon§f§l]"
    }

    override fun onEnable() {
        // Plugin startup logic
        saveDefaultConfig()

        plugin = this
        ItemData.loadItemIndex()

        ItemData.fee = config.getDouble("fee")

        server.pluginManager.registerEvents(CommerceMenu,this)

    }

    override fun onDisable() {
        // Plugin shutdown logic
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {

        if (sender !is Player)return false

        if (args.isNullOrEmpty()){

            CommerceMenu.openMainMenu(sender)

            return true
        }

        when(args[0]){

            "sell" ->{//mnc sell price

                if (args.size != 2)return false

                val item = sender.inventory.itemInMainHand

                if (item.type == Material.AIR){ return true }

                val price = args[1].toDoubleOrNull()

                if (price == null){

                    sender.sendMsg("§c§l金額は数字を使ってください！")

                    return true
                }


                es.execute {
                    if (ItemData.sell(sender,item,price)){
                        sender.sendMsg("§e§l出品成功しました！")
                    }
                }

            }

        }



        return false
    }
}