DuelNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local opts = {"Engage duel", "End duel"}

		if player.gmLevel == 99 then
			table.insert(opts, "Clear duel")
		end

		local menu = player:menuString("Halo! Ada yang bisa kubantu hari ini?", opts)

		if menu == "Engage duel" then
			DuelNpc.duelSetup(player, npc)
		elseif menu == "End duel" then
			DuelNpc.duelEnd(player, npc)
		elseif menu == "Clear duel" then
			DuelNpc.resetDuel(npc)
		end

		-- Duel between Rubber (8613:1100) and Rubber(8613:1100) for 1 gold will start
	end),

	duelSetup = function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local choice = player:menuSeq(
			"Kau yakin ingin memulai duel?",
			{"Ya", "Tidak"},
			{}
		)

		if choice == 1 then
			if npc.registry["duelist1"] ~= 0 then
				local duelist1 = Player(npc.registry["duelist1"])

				if duelist1 == nil then
					-- not online
					DuelNpc.resetDuel(npc)
					return
				end

				if duelist1.name == player.name then
					player:dialogSeq({t, "Kau tidak bisa berduel dengan dirimu sendiri."}, 0)
					return
				end

				local against = player:menuSeq(
					"Maukah kau bertarung melawan " .. duelist1.name .. " emas " .. Tools.formatNumber(npc.registry["duelist1gold"]) .. " Handicap " .. npc.registry[
						"duelist1handicap"
					] .. "?",
					{"Ya", "Tidak"},
					{}
				)

				if against == 2 then
					player:dialogSeq({t, "Sampai jumpa."}, 0)
					return
				end
			end

			local input = player:input("Berapa banyak yang ingin kau pertaruhkan?")

			if (not input:match("%d")) then
				player:dialog("Kau hanya boleh memasukkan angka, bukan huruf.", {})
				return
			end

			local amount = tonumber(input)

			if amount <= 0 then
				player:dialogSeq(
					{
						t,
						"Kau harus memasukkan angka positif yang tidak melebihi saldo Kan-mu."
					},
					0
				)
				return
			end

			if amount > player.money then
				player:dialogSeq(
					{t, "Uangmu tidak sebanyak itu."},
					0
				)
				return
			end

			local confirm = player:menuSeq(
				"Kau yakin ingin mempertaruhkan " .. Tools.formatNumber(amount) .. " emas?",
				{"Ya", "Tidak"},
				{}
			)

			if confirm == 1 then
				local input2 = player:input("Berapa Handicap-mu? 1-99(%)")
				if (not input2:match("%d")) then
					player:dialog("Kau hanya boleh memasukkan angka, bukan huruf.", {})
					return
				end

				local handicap = tonumber(input2)

				if handicap < 1 or handicap > 99 then
					player:dialogSeq(
						{t, "Kau harus memasukkan angka antara 1 dan 99"},
						0
					)
					return
				end

				local handicapConfirm = player:menuSeq(
					"Kau yakin ingin menetapkan Vita pada " .. handicap .. "(%) dan Mana pada " .. handicap .. "(%)?",
					{"Ya", "Tidak"},
					{}
				)

				if handicapConfirm == 1 then
					if DuelNpc.duelCheck(npc) then
						npc.registry["duelist2"] = player.ID
						npc.registry["duelist2handicap"] = handicap
						npc.registry["duelist2gold"] = amount
						broadcast(29200, player.name .. " joined the duel!")

						Player(npc.registry["duelist1"]):setTimer(2, 15)
						Player(npc.registry["duelist2"]):setTimer(2, 15)
					else
						npc.registry["duelist1"] = player.ID
						npc.registry["duelist1handicap"] = handicap
						npc.registry["duelist1gold"] = amount
						broadcast(29200, player.name .. " initiated a duel!")
					end

					player:removeGold(amount)
					player:warp(29200, math.random(5, 14), math.random(5, 14))
					pvp_balance.cast(player)
				end
			end
		elseif choice == 2 then
			player:dialogSeq({t, "Sampai jumpa."}, 0)
			return
		end
	end,

	duelEnd = function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		if npc.registry["duelist1"] ~= player.ID and npc.registry["duelist2"] ~= player.ID then
			player:dialogSeq(
				{t, "Hanya yang sedang berduel yang bisa mengakhiri duel."},
				0
			)
			return
		end

		local choice = player:menuSeq(
			"Kau yakin ingin mengakhiri duel ini?",
			{"Ya", "Tidak"},
			{}
		)

		local duelist1 = Player(npc.registry["duelist1"])
		local duelist2 = Player(npc.registry["duelist2"])

		local winner = 0
		local loser = 0

		if duelist1 == nil or duelist2 == nil then
			-- one of them logged off
			if duelist1 == nil then
				-- logged off
				winner = npc.registry["duelist2"]
				loser = npc.registry["duelist1"]
			elseif duelist2 == nil then
				-- logged off
				winner = npc.registry["duelist1"]
				loser = npc.registry["duelist2"]
			end

			DuelNpc.declareDuelWinner(npc, winner, loser)

			return
		end

		if winner == 0 then
			-- both still playing

			if not DuelNpc.checkPlayArea(duelist1, npc) or not DuelNpc.checkPlayArea(
				duelist2,
				npc
			) then
				-- one of the duelists is online but left the map or play area

				if not DuelNpc.checkPlayArea(duelist1, npc) then
					winner = npc.registry["duelist2"]
					loser = npc.registry["duelist1"]
				end
				if not DuelNpc.checkPlayArea(duelist2, npc) then
					winner = npc.registry["duelist1"]
					loser = npc.registry["duelist2"]
				end

				DuelNpc.declareDuelWinner(npc, winner, loser)
				return
			else
				-- both still in play area
				if duelist1.state == 1 or duelist2.state == 1 then
					-- one of htem is dead
					if duelist1.state == 1 then
						winner = npc.registry["duelist2"]
						loser = npc.registry["duelist1"]
					elseif duelist2.state == 1 then
						winner = npc.registry["duelist1"]
						loser = npc.registry["duelist2"]
					end

					DuelNpc.declareDuelWinner(npc, winner, loser)
					return
				else
					-- both still alive so one will forfeit
					if player.ID == npc.registry["duelist1"] then
						winner = npc.registry["duelist2"]
						loser = npc.registry["duelist1"]
					elseif player.ID == npc.registry["duelist2"] then
						winner = npc.registry["duelist1"]
						loser = npc.registry["duelist2"]
					end

					local choice = player:menuSeq(
						"Kau paham bahwa kau menyerahkan kemenangan kepada " .. Player(winner) .. "?",
						{"Ya", "Tidak"},
						{}
					)

					if choice == 1 then
						DuelNpc.declareDuelWinner(npc, winner, loser)
					end
				end
			end
		end
	end,

	declareDuelWinner = function(npc, winner, loser)
		local totalGold = npc.registry["duelist1gold"] + npc.registry[
			"duelist2gold"
		]

		local winningPlayer = Player(winner)

		if winningPlayer ~= nil then
			winningPlayer:addGold(totalGold)
		end

		broadcast(29200, winningPlayer.name .. " has won the duel.")

		DuelNpc.resetDuel(npc)
	end,

	checkPlayArea = function(player, npc)
		local playerInArea = false

		if player.m == npc.m then
			return true
		end

		local pcs = npc:getObjectsInSameMap(BL_PC)

		if pcs ~= nil then
			for i = 1, #pcs do
				if pcs[i].ID == player.ID and pcs[i].x >= 5 and pcs[i].x <= 14 and pcs[i].y >= 5 and pcs[
					i
				].y <= 14 then
					return true
				end
			end
		end

		return playerInArea
	end,

	duelCheck = function(npc)
		if npc.registry["duelist1"] ~= 0 then
			return true
		else
			return false
		end
	end,

	resetDuel = function(npc)
		npc.registry["duelist1"] = 0
		npc.registry["duelist1handicap"] = 0
		npc.registry["duelist1gold"] = 0

		npc.registry["duelist2"] = 0
		npc.registry["duelist2handicap"] = 0
		npc.registry["duelist2gold"] = 0

		local pcs = npc:getObjectsInSameMap(BL_PC)

		if pcs ~= nil then
			for i = 1, #pcs do
				if pcs[i].x >= 5 and pcs[i].x <= 14 and pcs[i].y >= 5 and pcs[i].y <= 14 then
					pcs[i]:warp(29200, 6, 3)
					pcs[i]:setDuration("pvp_balance", 0)
				end
			end
		end
	end,
}
