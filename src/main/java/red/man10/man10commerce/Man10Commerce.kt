package red.man10.man10commerce

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10commerce.Utility.format
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

        var enable = true

        var minPrice : Double =  10.0

        var maxPrice : Double = 10000000.0 //一般会員の出品額上限
        var maxItems : Int = 54 // 一般会員の出品数上限
        var primeMoney : Double = 1000000.0 //プライム会員の会員費
    }

    override fun onEnable() {
        // Plugin startup logic
        saveDefaultConfig()

        plugin = this
        ItemData.loadItemIndex()

        loadConfig()

        server.pluginManager.registerEvents(CommerceMenu,this)

    }

    override fun onDisable() {
        // Plugin shutdown logic
    }

    fun loadConfig(){

        reloadConfig()

        ItemData.fee = config.getDouble("fee")
        minPrice = config.getDouble("minPrice")
        maxPrice = config.getDouble("maxPrice")
        maxItems = config.getInt("maxItems")
        enable = config.getBoolean("enable")
        primeMoney = config.getDouble("primeMoney")


    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {

        if (sender !is Player)return false


        if (label == "amsell"){

            if (!sender.hasPermission("commerce.user"))return true

            if (!sender.hasPermission("commerce.op") && !enable){
                sendMsg(sender,"§f現在営業を停止しています")

                return false
            }

            if (args.isEmpty()){

                sendMsg(sender,"§a§l/amsell <値段> (単価ではなく、合計の値段を入力してください)")

                return false
            }

            val item = sender.inventory.itemInMainHand

            if (item.type == Material.AIR){ return true }

            val price = args[0].toDoubleOrNull()

            if (price == null){

                sendMsg(sender,"§c§l金額は数字を使ってください！")

                return true
            }

            if (price< minPrice){
                sendMsg(sender,"§c§l${minPrice}円以下での出品はできません！")
                return true
            }

            es.execute {
                if (ItemData.sell(sender,item,price)){
                    sendMsg(sender,"§e§l出品成功しました！")

                    val name = if (item.hasItemMeta()) item.itemMeta!!.displayName else item.i18NDisplayName

                    Bukkit.getScheduler().runTask(this, Runnable {
                        Bukkit.broadcastMessage("${prefix}§f§l${name}が§e§l${format(price)}§f§l円で出品されました！")
                    })
                }
            }

            return true
        }



        if (args.isNullOrEmpty()){

            if (!sender.hasPermission("commerce.user"))return false

            if (!sender.hasPermission("commerce.op") && !enable){
                sendMsg(sender,"§f現在営業を停止しています")

                return false
            }

            CommerceMenu.openMainMenu(sender)

            return true
        }

        when(args[0]){

            "on" ->{
                if (!sender.hasPermission("commerce.op"))return true

                enable = true
                config.set("enable", enable)
                saveConfig()
            }

            "off" ->{
                if (!sender.hasPermission("commerce.op"))return true

                enable = false
                config.set("enable", enable)
                saveConfig()

            }

            "config" ->{
                if (!sender.hasPermission("commerce.op"))return true

                Thread{
                    loadConfig()
                    sender.sendMessage("§a§lReloaded Config")
                }.start()
            }

            "sell" ->{//mnc sell price
                if (!sender.hasPermission("commerce.user"))return true

                if (!sender.hasPermission("commerce.op") && !enable){
                    sendMsg(sender,"§f現在営業を停止しています")

                    return false
                }

                if (args.size != 2)return false

                val item = sender.inventory.itemInMainHand

                if (item.type == Material.AIR){ return true }

                val price = args[1].toDoubleOrNull()

                if (price == null){

                    sendMsg(sender,"§c§l金額は数字を使ってください！")

                    return true
                }

                if (price< minPrice){
                    sendMsg(sender,"§c§l${minPrice}円以下での出品はできません！")
                    return true
                }

                es.execute {
                    if (ItemData.sell(sender,item,price)){
                        sendMsg(sender,"§e§l出品成功しました！")
                    }
                }

            }

        }

        return false
    }
}