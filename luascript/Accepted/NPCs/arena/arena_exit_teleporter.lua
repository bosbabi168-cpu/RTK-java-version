ArenaExitTeleporterNpc = {
	click = async(function(player, npc)
		local name = "<b>[" .. npc.name .. "]\n\n"
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local opts = {}
		table.insert(opts, "Keluarkan aku dari tempat ini")
		menu = player:menuString(
			name .. "Penakut seperti aku, BAA-KOKOKOKOKKKKK!",
			opts
		)

		if (menu == "Keluarkan aku dari tempat ini") then
			minigame_powers.resetPlayer(player)
		end
	end)
}

minigame_powers = {
	f1click = function(player, npc)
		clone.gfx(player, npc)
		player:refresh()

		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 2
		local playerToBan
		local banTime
		local opts = {}
		local pc = player:getObjectsInMap(player.m, BL_PC)

		table.insert(opts, "Add Player")
		table.insert(opts, "Remove Player")
		table.insert(opts, "Pause Game")
		table.insert(opts, "Unpause Game")
		table.insert(opts, "Check Online Bans")
		table.insert(opts, "Set Minigame Ban")

		menu = player:menuString(
			"<b>[KUASA MINIGAME]\nApa yang ingin kau lakukan?",
			opts
		)

		if menu == "Add Player" then
			playerToAdd = player:input("Siapa yang perlu dimasukkan ke minigame?")
			if Player(playerToAdd) ~= nil then
				minigame_powers.addPlayer(player, Player(playerToAdd))
			end
		elseif menu == "Remove Player" then
			playerToKick = player:input("Siapa yang perlu dikeluarkan dari minigame?")
			if Player(playerToKick) ~= nil then
				minigame_powers.kickPlayer(Player(playerToKick))
			end
		elseif menu == "Pause Game" then
			if #pc > 0 then
				for i = 1, #pc do
					pc[i]:sendAnimation(2)
					pc[i].paralyzed = true
				end
			end
			broadcast(player.m, "The game has been paused by " .. player.name)
		elseif menu == "Unpause Game" then
			if #pc > 0 then
				for i = 1, #pc do
					pc[i]:sendAnimation(3)
					pc[i].paralyzed = false
				end
			end
			broadcast(player.m, "The game has been paused by " .. player.name)
		elseif menu == "Check Online Bans" then
			minigame_powers.checkBans(player)
		elseif menu == "Set Minigame Ban" then
			playerToBan = Player(player:input("Siapa yang dicekal?"))
			banTime = player:input("Dicekal berapa jam?")
			playerToBan.registry["minigame_ban_timer"] = os.time() + (banTime * 3600)
			player:popUp("" .. playerToBan.name .. " has been banned from minigames for " .. banTime .. " hours.")
		end
	end,

	checkBans = function(player)
		local pc = core:getUsers()
		local calc, dif, hour, minute, second = 0, 0, 0, 0, 0
		local banned = {}

		for i = 1, #pc do
			if pc[i].registry["minigameBan"] > os.time() then
				dif = pc[i].registry["minigame_ban_timer"] - os.time()
				hour = string.format("%02.f", math.floor(dif / 3600))
				minute = string.format(
					"%02.f",
					math.floor(dif / 60 - (hour * 60))
				)
				second = string.format(
					"%02.f",
					math.floor(dif - hour * 3600 - minute * 60)
				)

				calc = hour .. ":" .. minute .. ":" .. second

				table.insert(banned, "" .. pc[i].name .. " dicekal selama " .. calc)

				--Player(4):talk(0,""..pc[i].name.." banned for "..calc)
			end
		end
		player:menuString("<b>[CURRENT BANS]", banned)
	end,

	resetPlayer = function(player)
		--[[
	if player.m == 15020 then
		if player.registry["beach_war_team"] == 1 then
			table.remove(livingRedSquirt)
		elseif player.registry["beach_war_team"] == 2 then
			table.remove(livingBlueSquirt)
		end
	end
]]
		--
		player.registry["beach_war_times_hit"] = 0
		player.registry["beach_war_gun_pct"] = 0
		player.registry["beach_war_registered"] = 0
		player.registry["beach_war_flag"] = 0
		player.registry["beach_war_team"] = 0
		player.registry["beach_war_kills"] = 0

		player.registry["freeze_war_registered"] = 0
		player.registry["freeze_war_flag"] = 0
		player.registry["freeze_war_team"] = 0

		player.registry["sumo_war_registered"] = 0
		player.registry["sumo_war_team"] = 0

		player.registry["elixir_registered"] = 0
		player.registry["elixir_flag"] = 0
		player.registry["elixir_team"] = 0
		player.registry["elixir_hit"] = 0
		player.registry["elixir_arrows"] = 0

		player.registry["bomber_war_registered"] = 0
		player.registry["bomber_war_team"] = 0
		player.registry["speed_boost"] = 0
		player.registry["bomb_max"] = 0
		player.registry["bomb_distance"] = 0

		player.speed = 80
		player.gfxClone = 0
		player.calcStat()
		player:updateState()
		player:warp(31, math.random(6, 15), math.random(8, 15))
		player:sendAnimation(16)
		player:playSound(29)
	end,

	kickPlayer = function(player)
		minigame_powers.resetPlayer(player)
		player:sendMinitext("Kau dikeluarkan dari minigame.")
	end,

	addPlayer = function(player, target)
		-- ⚠️ Pendaftaran Flag Freeze Tag (peta 15000) DIHAPUS 29 Agu 2026:
		-- tabel `ctf` yang dipanggilnya (`ctf.costume`, `ctf.entryLegend`)
		-- tidak pernah ada di pohon skrip ini — berkasnya memang tidak ikut
		-- dalam dump konten. Kode yang tersisa hanya akan melempar
		-- "attempt to index nil" saat pemain mengklik NPC-nya. Lihat
		-- luascript/PERUBAHAN.md.
		if player.m == 15020 then
		elseif player.m == 15030 then
		elseif player.m == 15040 then
		elseif player.m == 15050 then
		end
	end
}
