package red.man10.man10commerce

import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class Man10Commerce : JavaPlugin() {

    companion object{
        lateinit var plugin: JavaPlugin
    }

    override fun onEnable() {
        // Plugin startup logic
        plugin = this

    }

    override fun onDisable() {
        // Plugin shutdown logic
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {

        if (sender !is Player)return false

        if (args.isNullOrEmpty()){


            return true
        }



        return false
    }
}