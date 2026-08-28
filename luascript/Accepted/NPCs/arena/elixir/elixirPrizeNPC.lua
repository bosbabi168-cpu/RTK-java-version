ElixirPrizeNpc = {
	on_spawn = function(npc)
		npc.look = 8
		npc.lookColor = 3
		npc.side = 2

		local pcs = npc:getObjectsInSameMap(BL_PC)

		if pcs ~= nil then
			for i = 1, #pcs do
				pcs[i]:refresh()
			end
		end
	end,

	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		--player:dialogSeq({t,"Disabled."},0)

		if player.registry["elixirTeam"] == 0 then
			player:dialogSeq({t, "Kau bukan anggota regu elixir."}, 0)
			return
		end
		if player.registry["elixirTeam"] ~= core.gameRegistry["elixirWinner"] then
			player:dialogSeq({t, "Kau bukan regu elixir yang menang."}, 0)
			return
		end

		local prizes = {
			"ancient_potion",
			"autumn_potion",
			"blood_potion",
			"earth_potion",
			"ruby_potion",
			"sea_potion",
			"summer_potion",
			"winter_potion",
			"long_sheath"
		}
		local prizeNames = {}
		for i = 1, #prizes do
			table.insert(prizeNames, Item(prizes[i]).name)
		end

		local prizeAmounts = {12, 12, 12, 12, 12, 12, 12, 12, 1}

		local choice = player:menuSeq(
			"Pilih hadiahmu.",
			prizeNames,
			{}
		)

		if player:hasSpace(prizes[choice], prizeAmounts[choice]) ~= true then
			player:dialogSeq(
				{t, "Ruang di kantongmu tidak cukup."},
				0
			)
			return
		end

		player:addItem(prizes[choice], prizeAmounts[choice])

		player:removeLegendbyName("elixir_war_victories")
		player.registry["elixir_war_victories"] = player.registry[
			"elixir_war_victories"
		] + 1
		player:addLegend(
			player.registry["elixir_war_victories"] .. " Elixir War victories",
			"elixir_war_victories",
			1,
			128
		)

		clone.wipe(player)
		player.registry["elixirTeam"] = 0
		minigames.giveMinigameExp(player, 1)
		player:returnToInn()
	end)
}
