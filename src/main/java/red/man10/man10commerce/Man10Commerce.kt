package red.man10.man10commerce

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10bank.BankAPI
import red.man10.man10commerce.Utility.sendMsg
import red.man10.man10commerce.data.Database
import red.man10.man10commerce.data.Log
import red.man10.man10commerce.data.MySQLManager
import red.man10.man10commerce.data.Schema
import red.man10.man10commerce.data.Transaction
import red.man10.man10commerce.menu.*
import java.io.File
import java.nio.file.Files
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class Man10Commerce : JavaPlugin() {

    companion object{
        lateinit var plugin: JavaPlugin
        lateinit var bank : BankAPI
        lateinit var es : ExecutorService
        lateinit var vault: VaultManager

        const val prefix = "§l[§a§lA§d§lma§f§ln§a§lzon§f§l]§f"

        var enable = true

        var minPrice : Double =  1.0
        var maxPrice : Double = 10000000.0 //一般会員の出品額上限
//        var maxPriceMultiply : Double = 10.0

        var maxItems : Int = 54 // 一般会員の出品数上限

        const val OP = "commerce.op"
        const val USER = "commerce.user"

        lateinit var lang : JsonObject

        val disableItems = ArrayList<String>()

        fun getDisplayName(item: ItemStack): String {

            val name: String
            if (item.hasItemMeta() && item.itemMeta.hasDisplayName()){
                name = item.itemMeta.displayName
            }else{
                val separator =
                    if (item.type.isBlock) {
                        "block.minecraft."
                    } else {
                        "item.minecraft."
                    }
                val japanese = lang[separator + item.type.name.toLowerCase()]
                name = if (japanese == null){
                    item.i18NDisplayName?:""
                }else{
                    japanese.asString
                }
            }
            return name
        }

        fun shutdownExecutor(){
            if (::es.isInitialized) es.shutdownNow()
        }

    }

    override fun onEnable() {
        // Plugin startup logic
        if (server.pluginManager.getPlugin("Man10Bank") == null){
            Bukkit.getLogger().warning("Man10Bankが見つかりません。Man10Commerceを無効化します")
            server.pluginManager.disablePlugin(this)
            return
        }

        saveDefaultConfig()

        es  = Executors.newCachedThreadPool()

        plugin = this

        loadConfig()

        try {
            lang = Gson().fromJson(Files.readString(File(plugin.dataFolder.path+"/ja_jp.json").toPath()),JsonObject::class.java)
        }catch (e:Exception){
            Bukkit.getLogger().warning("言語ファイルがありません")
        }

        //DBに繋がらない状態で動かすとアイテムやお金を失うので、ここで止める
        if (!Database.setup(this)){
            Bukkit.getLogger().severe("データベースに接続できなかったためMan10Commerceを無効化します")
            server.pluginManager.disablePlugin(this)
            return
        }

        if (!Schema.migrate(MySQLManager("Man10CommerceSchema"))){
            Bukkit.getLogger().warning("テーブル・インデックスの適用に失敗しました。処理が遅くなる可能性があります")
        }

        bank = BankAPI(plugin)
        vault = VaultManager(plugin)
        vault.hook()
        MenuFramework.setup(this)

        Log.setup()
        Transaction.setup()

        server.pluginManager.registerEvents(MenuFramework.MenuListener,this)
    }

    override fun onDisable() {
        // Plugin shutdown logic
        Transaction.stop()
        Log.stop()
        Database.shutdown()
        shutdownExecutor()
    }

    //  DB接続も含めて作り直す
    private fun reload(sender: CommandSender){

        Transaction.stop()
        Log.stop()

        loadConfig()

        if (!Database.setup(this)){
            sender.sendMessage("§c§lデータベースへの再接続に失敗しました。config.ymlを確認してください")
            return
        }

        Schema.migrate(MySQLManager("Man10CommerceSchema"))

        Log.setup()
        Transaction.setup()

        sender.sendMessage("§a§lコンフィグのリロード完了")
        sender.sendMessage("§a§l取引システムのリロード完了")
    }

    private fun loadConfig(){

        reloadConfig()

        minPrice = config.getDouble("minPrice",10.0)
        maxPrice = config.getDouble("maxPrice",100000000.0)
        maxItems = config.getInt("maxItems",54)
        enable = config.getBoolean("enable")

        disableItems.clear()
        val confFile = File(dataFolder.path + "/disableItems.yml")
        if (confFile.exists()){
            val conf = YamlConfiguration.loadConfiguration(confFile)
            disableItems.addAll(conf.getStringList("disables"))
        }
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {

        if (sender !is Player)return false

        if (!sender.hasPermission(USER)){
            sendMsg(sender,"§c§lあなたはアマンゾンへのアクセス権がありません")
            return true
        }

        if (!sender.hasPermission(OP) && !enable){
            sendMsg(sender,"§f現在営業を停止しています")

            return false
        }

        if (sender.hasPermission(OP) && !enable){
            sendMsg(sender,"§c§l管理者として実行しています")
        }

        if (!Database.isReady){
            sendMsg(sender,"§c§l現在センターにアクセスできません。復旧までお待ちください")
            return true
        }

        if (label == "amauthor"){

            if (args.size != 1){
                sendMsg(sender,"§a§l/amauthor <出品者名>")
                return true
            }

            SellerMenu(sender,0,args[0]).open()

            return true
        }

        if (label == "amsearch"){
            if (!sender.hasPermission(USER))return true

            if (args.isEmpty()){
                sendMsg(sender,"§a§l/amsearch <検索するアイテムの名前>")
                return true
            }

            SearchMenu(sender,0,args.joinToString(" ")).open()

            return true
        }

        if (label == "amsell"){

            if (!sender.hasPermission(USER))return true

            if (args.isEmpty()){

                sendMsg(sender,"§a§l/amsell <値段> (アイテム一つあたりの値段を入力してください)")

                return false
            }

            val itemInHand = sender.inventory.itemInMainHand
            val clone = itemInHand.clone()

            if (itemInHand.type == Material.AIR){
                sendMsg(sender,"§cアイテムをメインの手に持ってください")
                return true
            }

            itemInHand.amount = 0

            val price = args[0].toDoubleOrNull()

            if (price == null){
                sendMsg(sender,"§c§l金額は数字を使ってください！")
                sender.inventory.addItem(clone)
                return true
            }

            Transaction.asyncSell(sender,clone,price,{ result->
                if (!result){
                    sender.inventory.addItem(clone)
                }
            })

            return true
        }

        if (label=="amsellop"){

            if (!sender.hasPermission(OP))return false

            if (args.isEmpty()){

                sendMsg(sender,"§a§l/amsellop <値段> (アイテム一つあたりの値段を入力してください)")

                return false
            }

            val itemInHand = sender.inventory.itemInMainHand
            val clone = itemInHand.clone()

            if (itemInHand.type == Material.AIR){
                sendMsg(sender,"§cアイテムをメインの手に持ってください")
                return true
            }

            itemInHand.amount = 0

            val price = args[0].toDoubleOrNull()

            if (price == null){
                sendMsg(sender,"§c§l金額は数字を使ってください！")
                sender.inventory.addItem(clone)
                return true
            }

            Transaction.asyncSell(sender,clone,price,{ result->
                if (!result){
                    sender.inventory.addItem(clone)
                }
            },true)

            return true
        }

        if (label=="amzn" && args.isEmpty()){

            if (!sender.hasPermission(USER))return false

            MainMenu(sender).open()

            return true
        }

        when(args[0]){

            "category" ->{
                if (!sender.hasPermission(USER))return false

                CategoryMenu(sender).open()

            }

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

                es.execute { reload(sender) }
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
        return super.onTabComplete(sender, command, alias, args)?:Collections.emptyList()
    }
}