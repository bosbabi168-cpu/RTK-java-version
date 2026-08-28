OldDogNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		if player:hasLegend("avenged_treachery_against_the_dogs") then
			if not player:hasSpell("restore_poet") and player.baseClass == 4 then
				player:addSpell("restore_poet")
			end
			if not player:hasSpell("magis_bane") and player.baseClass == 3 then
				player:addSpell("magis_bane")
			end
			player:dialogSeq({t, "Kau sudah membalaskan dendam kami."}, 0)
		end

		if player.baseClass == 4 and player:hasSpell("restore_poet") and not player:hasLegend("avenged_treachery_against_the_dogs") then
			player:addLegend(
				"Membalas pengkhianatan terhadap para anjing (" .. curT() .. ")",
				"avenged_treachery_against_the_dogs",
				5,
				128
			)
			return
		end

		if player.baseClass == 3 and player:hasSpell("magis_bane") and not player:hasLegend("empowered_by_the_dogs") then
			player:addLegend(
				"Dikuatkan oleh para anjing (" .. curT() .. ")",
				"empowered_by_the_dogs",
				5,
				128
			)
			return
		end

		if player.level == 99 and player.baseClass == 4 and player:hasLegend("dog_linguist") then
			if player.quest["poet_restore"] == 1 then
				if player:killCount("tiger_storm") >= 1 then
					player:addKarma(math.random(0.1, 0.3))
					player:addSpell("restore_poet")
					player:giveXP(50000000)
					player.quest["poet_restore"] = 0
					player:addLegend(
						"Membalas pengkhianatan terhadap para anjing (" .. curT() .. ")",
						"avenged_treachery_against_the_dogs",
						5,
						128
					)
					player:dialogSeq({t, "Terima kasih sudah membalaskan dendam kami."}, 0)
					return
				else
					player:dialogSeq(
						{t, "Temui aku lagi setelah kau membunuh Storm"},
						0
					)
					return
				end
				return
			end

			player.quest["poet_restore"] = 1
			player:flushKills("tiger_storm")

			-- resets prior kill count for Storm
			player:dialogSeq(
				{
					t,
					"Di istana Rose kau akan menemukan Storm. Bunuh dia lalu kembalilah kepadaku."
				},
				0
			)
			return
		elseif player.level == 99 and player.baseClass == 3 and player:hasLegend("dog_linguist") then
			if player.quest["magis_bane"] == 1 then
				if player:killCount("tiger_summit") > player.quest["magis_bane_kill"] then
					player:addKarma(math.random(0.1, 0.3))
					player:addSpell("magis_bane")
					player:giveXP(75000000)
					player.quest["magis_bane"] = 0
					player:addLegend(
						"Dikuatkan oleh para anjing (" .. curT() .. ")",
						"empowered_by_the_dogs",
						5,
						128
					)
					player:dialogSeq({t, "Terima kasih sudah membalaskan dendam kami."}, 0)
					return
				else
					player:dialogSeq(
						{t, "Temui aku lagi setelah kau membunuh Summit"},
						0
					)
					return
				end
				return
			end

			player.quest["magis_bane"] = 1
			player.quest["magis_bane_kill"] = player:killCount("tiger_summit")

			-- capture kills
			player:dialogSeq(
				{
					t,
					"Jauh di dalam istana harimau kau akan menemukan musuh yang kuat, Summit. Bunuh dia lalu kembalilah kepadaku."
				},
				0
			)
			return
		else
			player:dialogSeq({t, "Anjing itu tampak tidak tertarik padamu."}, 0)
			return
		end
	end),

	move = function(npc, owner)
		local found
		local moved = true
		local oldside = npc.side
		local checkmove = math.random(0, 10)

		if (npc.retDist <= distanceXY(npc, npc.startX, npc.startY) and npc.retDist > 1 and npc.returning == false) then
			npc.returning = true
		elseif (npc.returning == true and npc.retDist > distanceXY(npc, npc.startX, npc.startY) and npc.retDist > 1) then
			npc.returning = false
		end

		if (npc.returning == true) then
			found = toStart(npc, npc.startX, npc.startY)
		else
			if (checkmove >= 4) then
				npc.side = math.random(0, 3)
				npc:sendSide()
				if (npc.side == oldside) then
					moved = npc:move()
				end
			end
		end

		if (found == true) then
			npc.returning = false
		end
	end,
}
