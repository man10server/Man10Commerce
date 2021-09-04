package red.man10.man10commerce

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.text
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10bank.BankAPI
import red.man10.man10commerce.Utility.format
import red.man10.man10commerce.Utility.sendMsg
import red.man10.man10commerce.data.ItemData
import red.man10.man10commerce.menu.CommerceMenu
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class Man10Commerce : JavaPlugin() {

    companion object{
        lateinit var plugin: JavaPlugin
        lateinit var bank : BankAPI
        lateinit var es : ExecutorService

        const val prefix = "§l[§a§lA§d§lma§f§ln§a§lzon§f§l]§f"

        var enable = true

        var minPrice : Double =  1.0
        var maxPrice : Double = 10000000.0 //一般会員の出品額上限

        var maxItems : Int = 54 // 一般会員の出品数上限
//        var fee = 0.0

        const val OP = "commerce.op"
        const val USER = "commerce.user"

    }

    override fun onEnable() {
        // Plugin startup logic
        saveDefaultConfig()

        es  = Executors.newCachedThreadPool()

        plugin = this
        bank = BankAPI(plugin)
        ItemData.loadItemIndex()
        ItemData.loadOrderTable()
        ItemData.loadOPOrderTable()

        loadConfig()

        server.pluginManager.registerEvents(CommerceMenu,this)

    }

    override fun onDisable() {
        // Plugin shutdown logic
    }

    private fun loadConfig(){

        reloadConfig()

        minPrice = config.getDouble("minPrice")
        maxPrice = config.getDouble("maxPrice")
        maxItems = config.getInt("maxItems")
        enable = config.getBoolean("enable")

        ItemData.loadCategoriesData()

    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {

        if (sender !is Player)return false


        if (label == "amsell"){

            if (!sender.hasPermission(USER))return true

            if (!sender.hasPermission(OP) && !enable){
                sendMsg(sender,"§f現在営業を停止しています")

                return false
            }

            if (args.isEmpty()){

                sendMsg(sender,"§a§l/amsell <値段> (アイテム一つあたりの値段を入力してください)")

                return false
            }

            val item = sender.inventory.itemInMainHand
            val display = item.clone()

            if (item.type == Material.AIR){ return true }

            val price = args[0].toDoubleOrNull()

            if (price == null){

                sendMsg(sender,"§c§l金額は数字を使ってください！")

                return true
            }

            if (price< minPrice){
                sendMsg(sender,"§c§l${minPrice}円未満の出品はできません！")
                return true
            }

            es.execute {
                if (!ItemData.sell(sender,item,price))return@execute

                sendMsg(sender,"§e§l出品成功しました！")

                val name = if (display.hasItemMeta()) display.itemMeta!!.displayName else display.i18NDisplayName

                Bukkit.getScheduler().runTask(this, Runnable {
                    Bukkit.broadcast(text("${prefix}§f${name}§f(${display.amount}個)が§e§l単価${format(price)}§f円で出品されました！"))
                })
            }

            return true
        }

        if (label=="amsellop"){

            if (!sender.hasPermission(OP))return false

            if (args.isEmpty()){

                sendMsg(sender,"§a§l/amsellop <値段> (単価ではなく、合計の値段を入力してください)")

                return false
            }

            val item = sender.inventory.itemInMainHand
            val display = item.clone()

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
                if (!ItemData.sellOP(sender,item,price))return@execute

                sendMsg(sender,"§e§l出品成功しました！")

                val name = if (display.hasItemMeta()) display.itemMeta!!.displayName else display.i18NDisplayName

                Bukkit.getScheduler().runTask(this, Runnable {
                    Bukkit.broadcast(text("${prefix}§f§l${name}§f§l(${display.amount}個)が§e§l単価${format(price)}§f§l円で§d§l公式出品されました！"))
                })
            }

            return true
        }

        if (args.isNullOrEmpty()){

            if (!sender.hasPermission(USER))return false

            if (!sender.hasPermission(OP) && !enable){
                sendMsg(sender,"§f現在営業を停止しています")
                return false
            }

            CommerceMenu.openMainMenu(sender)

            return true
        }

        when(args[0]){

            "on" ->{
                if (!sender.hasPermission(OP))return true

                enable = true
                config.set("enable", enable)
                sendMsg(sender,"営業を開始させました")
                saveConfig()
            }

            "off" ->{
                if (!sender.hasPermission(OP))return true

                enable = false
                config.set("enable", enable)
                sendMsg(sender,"営業を停止させました")
                saveConfig()

            }

            "reload" ->{
                if (!sender.hasPermission(OP))return true

                es.execute {
                    loadConfig()
                    sender.sendMessage("§a§lReloaded Config")
                    ItemData.loadItemIndex()
                    sendMsg(sender,"Reload Table")
                }
            }
        }

        return false
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String> {
        if (alias == "amsell" && args.size==1){
            return mutableListOf("アイテム一つあたりの値段を入力")
        }
        return super.onTabComplete(sender, command, alias, args)!!
    }
}