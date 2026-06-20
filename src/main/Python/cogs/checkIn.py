from datetime import datetime
from enum import Enum
import discord
from discord.ext import commands, tasks

import sys
from pathlib import Path
parent_dir = Path(__file__).resolve().parent.parent

sys.path.append(str(parent_dir))
from bot import SheetsManagement, Game
USS_COLOUR = 0x992299
ADMIN_ROLES = ["ow admin", "rl admin", "staff lead", "deadlock admin", "valorant admin"]
#CHANNEL_NAMES = ["bot", "check"] # Channels with these words allow commands

def is_admin():
    async def predicate(ctx):
        return any((role.name.lower() in ADMIN_ROLES) for role in ctx.author.roles)
    return commands.check(predicate)

"""
def allowed_channel():
    async def predicate(ctx):
        return any(word in ctx.channel.name.lower() for word in CHANNEL_NAMES)
 return commands.check(predicate)
    """

class GameData():
    def __init__(self):
        self.teamsMapped = {}
        self.teamsMapped_user = {}
        self.checkInActive = False

class CheckInCommands(commands.Cog):
    def __init__(self, bot):
        self.manager = SheetsManagement()
        self.overwatch = GameData()
        self.rocket_league = GameData()
        self.deadlock = GameData()
        self.valorant = GameData()
        self.verifiedPlayersMap = {}
        self.lastRefresh : datetime = datetime.now()

        self.sync_team_data()
        self.sync_verified_players()

        self.refresh_task.start()  
        
    @tasks.loop(minutes=5) 
    async def refresh_task(self):
        self.sync_team_data()
        self.sync_verified_players()
        self.lastRefresh = datetime.now()

    #@allowed_channel()
    @commands.hybrid_group(name="rocketleague", aliases=["RL", "rl", "Rocket League", "RocketLeague"])
    async def rl(self, ctx):
        if ctx.invoked_subcommand is None:
            await ctx.reply("Available subcommands: checkin, checkout, getcaptain")

    #@allowed_channel()
    @commands.hybrid_group(name="overwatch", aliases=["OW", "ow", "Overwatch2", "Overwatch"])
    async def ow(self, ctx):
        if ctx.invoked_subcommand is None:
            await ctx.reply("Available subcommands: checkin, checkout, getcaptain")

    @commands.hybrid_group(name="deadlock", aliases=["DL", "dl", "Deadlock"])
    async def dl(self, ctx):
        if ctx.invoked_subcommand is None:
            await ctx.reply("Available subcommands: checkin, checkout, getcaptain")

    #@allowed_channel()
    @commands.hybrid_group(name="valorant", aliases=["val", "Valorant", "Val"])
    async def val(self, ctx):
        if ctx.invoked_subcommand is None:
            await ctx.reply("Available subcommands: checkin, checkout, getcaptain")


    @rl.command(name="checkin")
    async def check_in_rl(self, ctx):
        await self.check_in(ctx, Game.ROCKET_LEAGUE)

    @ow.command(name="checkin")
    async def check_in_ow(self, ctx):
        await self.check_in(ctx, Game.OVERWATCH)

    @dl.command(name="checkin")
    async def check_in_dl(self,ctx):
        await self.check_in(ctx, Game.DEADLOCK)
    
    @val.command(name="checkin")
    async def check_in_val(self,ctx):
        await self.check_in(ctx, Game.VALORANT)

    async def check_in(self, ctx, game:Game):
        gameObj = self.get_game_obj(game);
        sender : discord.Member = ctx.author

        if (gameObj.checkInActive):
            message = self.check_in_sheet(sender.name, game)
        else:
            message = "Check-Ins are currently closed."

        embed = self.custom_embed(description=message, game=game)
        await ctx.reply(embed=embed)

    @rl.command(name="checkout")
    async def check_out_rl(self, ctx):
        await self.check_out(ctx, Game.ROCKET_LEAGUE)

    @ow.command(name="checkout")
    async def check_out_ow(self, ctx):
        await self.check_out(ctx, Game.OVERWATCH)
    
    @dl.command(name="checkout")
    async def check_out_dl(self,ctx):
        await self.check_out(ctx, Game.DEADLOCK)
    
    @val.command(name="checkout")
    async def check_out_val(self,ctx):
        await self.check_out(ctx, Game.VALORANT)

    async def check_out(self, ctx, game:Game):
        gameObj = self.get_game_obj(game);
        sender : discord.Member = ctx.author

        if (gameObj.checkInActive):
            message = self.check_out_sheet(sender.name, game)
        else:
            message = "Check-Ins are currently closed."
            
        embed = self.custom_embed(description=message, game=game)
        await ctx.reply(embed=embed)

    def add_status(self, name : str):
        try:
            if name.lower() not in self.verifiedPlayersMap.keys():
                return "Unverified :x:";
            if self.verifiedPlayersMap[name.lower()].lower() == "verified":
                return "Verified ✅"
            return "Pending Verification 🟨"
        except:
            return "Unverified (Error)"

    @rl.command(name="getteam")
    async def get_connection_rl(self, ctx, team :str):
        await self.get_connection(ctx, team, Game.ROCKET_LEAGUE)

    @ow.command(name="getteam")
    async def get_connection_ow(self, ctx, team :str):
        await self.get_connection(ctx, team, Game.OVERWATCH)

    @dl.command(name="getteam")
    async def get_connection_dl(self,ctx, team :str):
        await self.get_connection(ctx, team, Game.DEADLOCK)

    @val.command(name="getteam")
    async def get_connection_val(self,ctx, team :str):
        await self.get_connection(ctx, team, Game.VALORANT)

    async def get_connection(self, ctx, team:str, game:Game):
        gameObj = self.get_game_obj(game);
        if team.lower() not in gameObj.teamsMapped:
                message = f"Could not find team {team}"
        else:
            players = "\n".join(f"- {con} | **{self.add_status(con)}**" for con in gameObj.teamsMapped[team.lower()]['connections'])
            message = f"""**{gameObj.teamsMapped[team.lower()]['formalised_name']}**\nCaptain's Discord: {gameObj.teamsMapped[team.lower()]['discord']}\n
            **Players:**
            {players}
            """
        embed = self.custom_embed(description=message, lastUpdate=True, game=game)
        await ctx.reply(embed=embed)

    @commands.hybrid_command(name="refreshteamdata")
    @is_admin()
    async def refresh_team_data(self, ctx):
        self.sync_team_data()
        self.sync_verified_players()
        self.lastRefresh = datetime.now()
        await ctx.reply("Refreshed data..")

    @commands.hybrid_command(name="checkverification")
    async def check_verification(self, ctx, username:str):
        msg = f"{username} -> **{self.add_status(username)}**";
        await ctx.reply(embed=self.custom_embed("Verification Check", description=msg, lastUpdate=True))

    @rl.group(name="admincheckin")
    async def rl_admincheckin(self, ctx):
        return
    
    @ow.group(name="admincheckin")
    async def ow_admincheckin(self, ctx):
        return
    
    @dl.group(name="admincheckin")
    async def dl_admincheckin(self, ctx):
        return
    
    @val.group(name="admincheckin")
    async def val_admincheckin(self, ctx):
        return
    
    ## Checkin Open/close

    @rl_admincheckin.command(name="open")
    @is_admin()
    async def open_check_in_rl(self, ctx):
        await self.set_check_in(ctx, Game.ROCKET_LEAGUE, True)

    @ow_admincheckin.command(name="open")
    @is_admin()
    async def open_check_in_ow(self, ctx):
        await self.set_check_in(ctx, Game.OVERWATCH, True)

    @dl_admincheckin.command(name="open")
    @is_admin()
    async def open_check_in_dl(self, ctx):
        await self.set_check_in(ctx, Game.DEADLOCK, True)

    @val_admincheckin.command(name="open")
    @is_admin()
    async def open_check_in_val(self, ctx):
        await self.set_check_in(ctx, Game.VALORANT, True)



    ##


    @rl_admincheckin.command(name="close")
    @is_admin()
    async def close_check_in_rl(self, ctx):
        await self.set_check_in(ctx, Game.ROCKET_LEAGUE, False)

    @ow_admincheckin.command(name="close")
    @is_admin()
    async def close_check_in_ow(self, ctx):
        await self.set_check_in(ctx, Game.OVERWATCH, False)

    @dl_admincheckin.command(name="close")
    @is_admin()
    async def close_check_in_dl(self, ctx):
        await self.set_check_in(ctx, Game.DEADLOCK, False)

    @val_admincheckin.command(name="close")
    @is_admin()
    async def close_check_in_val(self, ctx):
        await self.set_check_in(ctx, Game.VALORANT, False)

    async def set_check_in(self, ctx, game:Game, isOpen:bool):
        gameObj = self.get_game_obj(game);
        gameObj.checkInActive = isOpen;
        await ctx.reply(f"Check-ins have been {'opened' if isOpen else 'closed'}")

    ## Checkin Status

    @rl_admincheckin.command(name="status")
    @is_admin()
    async def status_check_in_rl(self, ctx):
        await self.status_check_in(ctx, Game.ROCKET_LEAGUE)
    
    @ow_admincheckin.command(name="status")
    @is_admin()
    async def status_check_in_ow(self, ctx):
        await self.status_check_in(ctx, Game.OVERWATCH)

    @dl_admincheckin.command(name="status")
    @is_admin()
    async def status_check_in_dl(self, ctx):
        await self.status_check_in(ctx, Game.DEADLOCK)
    
    @val_admincheckin.command(name="status")
    @is_admin()
    async def status_check_in_val(self, ctx):
        await self.status_check_in(ctx, Game.VALORANT)

    async def status_check_in(self, ctx, game:Game):
        gameObj = self.get_game_obj(game);
        if (gameObj.checkInActive):
            await ctx.reply("Check-ins are open")
        else:
            await ctx.reply("Check-ins are closed")

    @commands.Cog.listener()
    async def on_command_error(self, ctx, error):
        if isinstance(error, commands.CheckFailure):
            await ctx.reply("You need the **admin** role to use this command.")

    def get_game_obj(self, game:Game):
        if (game == Game.OVERWATCH):
            return self.overwatch
        elif (game == Game.DEADLOCK):
            return self.deadlock
        elif (game == Game.VALORANT):
            return self.valorant
        else:
            return self.rocket_league
        
    def get_game_string(self, game:Game):
        if (game == Game.OVERWATCH):
            return "Overwatch"
        elif (game == Game.DEADLOCK):
            return "Deadlock"
        elif (game == Game.VALORANT):
            return "Valorant"
        else:
            return "Rocket League"
        
    def grab_all_exist(self, row : list):
        connections : list = []
        for cell in row:
            if cell != "":
                connections.append(cell);
            else:
                continue;

        return connections;

    def custom_embed(self, description, lastUpdate=False, game : Game = Game.OVERWATCH ):
        embed = discord.Embed(title=f"USS - {self.get_game_string(game)}", description=description, colour=USS_COLOUR)
        if lastUpdate: embed.set_footer(text=f"Last Data Update: {self.lastRefresh.strftime('%d/%m/%Y at %H:%M UTC')}")
        embed.set_thumbnail(url="https://pbs.twimg.com/profile_images/2015895728149696512/SfKjcwoz_400x400.jpg")
        return embed

    def sync_team_data(self):
        try:
            self.rocket_league.teamsMapped.clear()
            data : list = self.manager.read_data("TeamContact!A2:H", Game.ROCKET_LEAGUE)
            for row in data:
                self.rocket_league.teamsMapped[row[0].lower()] = {"discord":row[1], "connections": self.grab_all_exist(row[2:]), "formalised_name": row[0]}
                self.rocket_league.teamsMapped_user[row[1].lower()] = {"team_name":row[0], "connections": self.grab_all_exist(row[2:])}

            self.overwatch.teamsMapped.clear()
            data : list = self.manager.read_data("TeamContact!A2:L", Game.OVERWATCH)
            for row in data:
                self.overwatch.teamsMapped[row[0].lower()] = {"discord":row[1], "connections": self.grab_all_exist(row[2:]), "formalised_name": row[0]}
                self.overwatch.teamsMapped_user[row[1].lower()] = {"team_name":row[0], "connections": self.grab_all_exist(row[2:])}

            self.deadlock.teamsMapped.clear()
            data : list = self.manager.read_data("TeamContact!A2:L", Game.DEADLOCK)
            for row in data:
                self.deadlock.teamsMapped[row[0].lower()] = {"discord":row[1], "connections": self.grab_all_exist(row[2:]), "formalised_name": row[0]}
                self.deadlock.teamsMapped_user[row[1].lower()] = {"team_name":row[0], "connections": self.grab_all_exist(row[2:])}

            self.valorant.teamsMapped.clear()
            data : list = self.manager.read_data("TeamContact!A2:L", Game.VALORANT)
            for row in data:
                self.valorant.teamsMapped[row[0].lower()] = {"discord":row[1], "connections": self.grab_all_exist(row[2:]), "formalised_name": row[0]}
                self.valorant.teamsMapped_user[row[1].lower()] = {"team_name":row[0], "connections": self.grab_all_exist(row[2:])}
        except:
            print("Error loading team data..")

    def sync_verified_players(self):
        self.verifiedPlayersMap.clear()

        data: list = self.manager.read_verified()

        if (len(data) < 1):
            return 

        for acc in data:
            if (len(acc) > 1):
                self.verifiedPlayersMap[acc[1].lower()] = acc[0]

    def get_team_from_user(self, username : str, game: Game):
        gameObj = self.get_game_obj(game)
        if username not in gameObj.teamsMapped_user: return "N/A"

        return gameObj.teamsMapped_user[username]["team_name"]

    def find_and_flip_checkin(self, teamName : str, checkin : bool, game: Game):
        if teamName == "N/A":
            return "You are not registered as a captain\n\nIf you believe this is wrong, please contact an admin"
        data : list = self.manager.read_data("Datasheet!A2:C", game)

        i = 2
        for row in data:
            if row[0] == teamName:
                flag = self.google_bool(row[2])
                if checkin and not flag:
                    self.manager.write_data([[checkin]], f"Datasheet!C{i}", game)
                    return f"Checked in {teamName}"
                elif checkin and flag:
                    return f"{teamName} are already checked in."
                elif not checkin and not flag:
                    return f"{teamName} are already checked out."
                else:
                    self.manager.write_data([[checkin]], f"Datasheet!C{i}", game)
                    return f"Checked out {teamName}"

            i = i+1

        return "You are not registered as a captain\n\nIf you believe this is wrong, please contact an admin"

    def check_in_sheet(self, username:str, game: Game):
        return self.find_and_flip_checkin(self.get_team_from_user(username.lower(), game),True, game)
    
    def check_out_sheet(self, username:str, game: Game):
        return self.find_and_flip_checkin(self.get_team_from_user(username.lower(), game),False, game)
    
    def google_bool(self, value):
        if isinstance(value, bool):
            return value 
        if isinstance(value, str):
            return value.strip().upper() == "TRUE"
        return bool(value) 

async def setup(bot: commands.Bot) -> None:
    await bot.add_cog(CheckInCommands(bot))