package red.man10.man10commerce

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import red.man10.man10commerce.data.ItemData

class TestCommand :CommandExecutor{
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {

        if (label!="amzntest"){return true}

        if (sender!is Player)return true

        when(args[0]){

            "testsell" ->{
                if (!sender.hasPermission(Man10Commerce.OP))return false

                val amount = args[1].toInt()

                Man10Commerce.debug = true

                val item = sender.inventory.itemInMainHand

                testSell(sender, amount, item)

            }

            "testsell2" ->{

                val amount = args[1].toInt()
                val multi = args[2].toInt()

                for (i in 0 until multi){
                    val item = sender.inventory.itemInMainHand

                    testSell(sender, amount, item)
                }

            }

            "testbuy" ->{

                if (!sender.hasPermission(Man10Commerce.OP))return false

                if (args.size<3){
                    Utility.sendMsg(sender, "/amzn testbuy idKey itemID")
                    return true
                }

                val key = args[1].toInt()
                val itemID = args[2].toInt()

                Man10Commerce.debug = true

                Bukkit.getLogger().info("StartTestBuy")

                Man10Commerce.es.execute {
                    for (i in 0 until key){
                        ItemData.buy(sender, itemID, i) { code: Int ->
                            when (code) {
                                0 -> {
                                    Utility.sendMsg(sender, "§c§l購入失敗！電子マネーが足りません！")
                                }
                                1 -> {
                                    Utility.sendMsg(sender, "§a§l購入成功！")
                                }
                                4 -> {
                                    Utility.sendMsg(sender, "§a§lインベントリに空きがありません！")
                                }
                                3, 5 -> {
                                    Utility.sendMsg(sender, "購入しようとしたアイテムが売り切れています！")
                                }
                                else -> {
                                    Utility.sendMsg(sender, "エラー:${code} サーバー運営者、GMに報告してください")
                                }
                            }
                            Bukkit.getLogger().info("TestFinish:${i} code:${code}")
                        }

                    }
                    Bukkit.getLogger().info("FinishedTestBuy")

                    Man10Commerce.debug = false
                }



            }
        }

        return true
    }


    private fun testSell(sender:Player, amount:Int, item:ItemStack){
        Man10Commerce.es.execute {

            val price = 10.0

            Bukkit.getLogger().info("StartTestSell:${amount}")

            for (i in 0 until amount){

                Man10Commerce.debug = true

                if (!ItemData.sell(sender,item,price))continue

                Utility.sendMsg(sender, "§e§l出品成功しました！")

                Bukkit.getLogger().info("TestFinish:${i}")
            }

            Bukkit.getLogger().info("FinishedTestSell")

            Man10Commerce.debug = false
        }

    }
}