ElixirGameNpc = {
	handItem = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local invItem = player:getInventoryItem(player.invSlot)

		if invItem.yname ~= "acorn" then
			player:sendMinitext("Serahkan satu acorn kepadaku.")
			return
		end

		if player.registry["elixirTeam"] == 1 and player:hasItem("blue_elixir", 1) ~= true then
			player:sendMinitext("Kau butuh elixir biru!")
			return
		end
		if player.registry["elixirTeam"] == 2 and player:hasItem("red_elixir", 1) ~= true then
			player:sendMinitext("Kau butuh elixir merah!")
			return
		end

		if core.gameRegistry["elixirRoundWinner"] ~= 0 then
			player:sendMinitext("Regumu sudah menyerahkan elixir.")
			return
		end

		if player.registry["elixirTeam"] == 1 then
			-- red
			if player.gfxDye == 50 then
				return
			end
			core.gameRegistry["elixirRedScore"] = core.gameRegistry[
				"elixirRedScore"
			] + 1
			core.gameRegistry["elixirRoundWinner"] = 1
			broadcast2(elixirMaps, "RED: " .. player.name .. " has the trophy!")
		elseif player.registry["elixirTeam"] == 2 then
			-- blue
			if player.gfxDye == 21 then
				return
			end
			core.gameRegistry["elixirBlueScore"] = core.gameRegistry[
				"elixirBlueScore"
			] + 1
			core.gameRegistry["elixirRoundWinner"] = 2
			broadcast2(
				elixirMaps,
				"BLUE: " .. player.name .. " has the trophy!"
			)
		end

		player:addItem("elixir_trophy", 1)

		player:removeItem("blue_elixir", 1)
		player:removeItem("red_elixir", 1)
		player:removeItem("acorn", 1)

		core.gameRegistry["elixirRound"] = core.gameRegistry["elixirRound"] + 1
		core.gameRegistry["elixirState"] = 14
		core.gameRegistry["elixirStart"] = os.time() + 15
		broadcast2(elixirMaps, "Elixir war 15 second intermission. Get ready!")
	end)
}
